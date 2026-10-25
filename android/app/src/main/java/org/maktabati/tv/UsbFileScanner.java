package org.maktabati.tv;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File metadata enumeration on Android 9: removable USB, no network and no writes. */
final class UsbFileScanner {
    private static final int LIMIT=25000;
    private static final Pattern YEAR=Pattern.compile("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)");
    private static final Pattern EP=Pattern.compile("(?i)(?:^|[._\\s-])S(\\d{1,2})[._\\s-]*E(\\d{1,3})(?=$|[._\\s-])");
    private static final Pattern ALT_EP=Pattern.compile("(?i)(?:^|[._\\s-])(\\d{1,2})x(\\d{1,3})(?=$|[._\\s-])");
    private static final Pattern RELEASE=Pattern.compile("(?i)[._\\s-]+(?:2160p|1080p|720p|480p|bluray|brrip|web\\s?dl|webrip|x264|x265|hevc|h264|h265|hdr|remux)(?:[._\\s-]|$)");

    static final class Scan {
        final DocumentTreeScanner.Result result;
        final String source;
        Scan(DocumentTreeScanner.Result result,String source){this.result=result;this.source=source;}
    }

    static Scan scan(Context context, boolean wholeDrive) {
        List<UsbVolumes.Volume> volumes=UsbVolumes.removable(context);
        if(volumes.isEmpty())throw new IllegalStateException("لم نجد قرص USB متاحًا للقراءة بعد منح الإذن.");
        if(volumes.size()>1)throw new IllegalStateException("وجدنا أكثر من قرص؛ افصل الأقراص الأخرى مؤقتًا لاختيار القرص الصحيح.");
        UsbVolumes.Volume vol=volumes.get(0);
        File scanRoot=vol.root;
        if(!scanRoot.isDirectory() || !scanRoot.canRead())
            throw new IllegalStateException("تعذر قراءة جذر قرص USB.");

        DocumentTreeScanner.Result result=new DocumentTreeScanner.Result();
        LinkedHashMap<String,DocumentTreeScanner.Show> shows=new LinkedHashMap<>();
        ArrayDeque<File> pending=new ArrayDeque<>();
        pending.add(scanRoot);
        try{File imports=NetworkFiles.root(context);if(imports.getAbsolutePath().contains("/Android/data/")&&imports.isDirectory())pending.add(imports);}catch(Exception ignored){}
        int entries=0;
        while(!pending.isEmpty()){
            File folder=pending.removeFirst();
            File[] children=folder.listFiles();
            if(children==null){result.unreadableFolders++;continue;}
            if((entries+=children.length)>LIMIT){result.truncated=true;break;}
            Map<String,File> covers=new HashMap<>();
            List<File> images=new ArrayList<>();
            for(File ch:children)if(ch.isFile() && image(ch.getName())){
                covers.put(ch.getName().toLowerCase(Locale.ROOT),ch);
                images.add(ch);
            }
            List<File> subtitleCandidates=new ArrayList<>();
            int localVideos=0;
            for(File candidate:children){
                if(candidate.isFile() && video(candidate.getName()))localVideos++;
                else if(candidate.isFile() && SubtitleMatcher.isSubtitle(candidate.getName()))subtitleCandidates.add(candidate);
            }
            if(localVideos==1){
                for(File candidate:children){
                    if(candidate.isDirectory() && within(vol.root,candidate) && subtitleFolder(candidate.getName())){
                        File[] inside=candidate.listFiles();
                        if(inside!=null && inside.length<=100){
                            for(File nested:inside)if(nested.isFile() && SubtitleMatcher.isSubtitle(nested.getName()))subtitleCandidates.add(nested);
                        }
                    }
                }
            }
            for(File file:children){
                if(file.isDirectory()){
                    if(!excluded(file.getName()) && within(vol.root,file))pending.addLast(file);
                }else if(file.isFile() && video(file.getName())){
                    DocumentTreeScanner.Media item=parse(vol,file);
                    for(File subtitle:subtitleCandidates){
                        boolean nested=!subtitle.getParentFile().equals(folder);
                        int score=SubtitleMatcher.score(file.getName(),subtitle.getName(),nested,localVideos==1);
                        if(score>=0){
                            String label=nested?subtitle.getParentFile().getName()+"/"+subtitle.getName():subtitle.getName();
                            item.subtitles.add(new DocumentTreeScanner.Subtitle(
                                    ReadOnlyMediaProvider.uriFor(vol,subtitle),label,score));
                        }
                    }
                    Collections.sort(item.subtitles,(a,b)->{
                        int s=Integer.compare(b.score,a.score);
                        return s!=0?s:a.fileName.compareToIgnoreCase(b.fileName);
                    });
                    File cover=findCover(file,folder,covers,images);
                    if(cover!=null){
                        item.posterUri=ReadOnlyMediaProvider.uriFor(vol,cover);
                        result.posterCount++;
                    }
                    if(item.series){
                        String key=item.seriesName.toLowerCase(Locale.ROOT);
                        DocumentTreeScanner.Show show=shows.get(key);
                        if(show==null){show=new DocumentTreeScanner.Show();show.title=item.seriesName;shows.put(key,show);}
                        show.episodes.add(item);
                        if(show.posterUri==null)show.posterUri=item.posterUri;
                    }else result.movies.add(item);
                    result.videoCount++;
                }
            }
        }
        result.shows.addAll(shows.values());
        Collections.sort(result.movies,Comparator.comparing(m->m.title.toLowerCase(Locale.ROOT)));
        Collections.sort(result.shows,Comparator.comparing(s->s.title.toLowerCase(Locale.ROOT)));
        for(DocumentTreeScanner.Show show:result.shows)
            Collections.sort(show.episodes,(a,b)->{
                int cmp=Integer.compare(a.season,b.season);
                return cmp!=0?cmp:Integer.compare(a.episode,b.episode);
            });
        return new Scan(result,"USB كامل");
    }

    private static DocumentTreeScanner.Media parse(UsbVolumes.Volume vol,File file){
        DocumentTreeScanner.Media m=new DocumentTreeScanner.Media();
        m.videoUri=ReadOnlyMediaProvider.uriFor(vol,file);
        m.fileName=file.getName();
        m.relativePath=file.getName(); // Do not log the user's complete local path.
        String stem=stem(file.getName());
        Matcher episode=EP.matcher(stem);
        if(!episode.find()){episode=ALT_EP.matcher(stem);if(!episode.find())episode=null;}
        boolean series=episode!=null;
        File cursor=file.getParentFile(),titleFolder=null;
        while(cursor!=null && within(vol.root,cursor)){
            String name=cursor.getName();
            if("series".equalsIgnoreCase(name) || "مسلسلات".equals(name) ||
               "tv shows".equalsIgnoreCase(name)){series=true;break;}
            if(titleFolder==null && !generic(name))titleFolder=cursor;
            cursor=cursor.getParentFile();
        }
        m.series=series;
        if(series){
            String raw=titleFolder!=null?titleFolder.getName():(episode!=null?stem.substring(0,episode.start()):stem);
            String clean=clean(raw).replaceAll("(?i)\\bS\\d{1,2}\\b"," ").trim();
            m.seriesName=withoutYear(clean);
            if(m.seriesName.isEmpty())m.seriesName="مسلسل غير معروف";
            if(episode!=null){
                m.season=Integer.parseInt(episode.group(1));
                m.episode=Integer.parseInt(episode.group(2));
                m.title=String.format(Locale.US,"الموسم %d · الحلقة %d",m.season,m.episode);
            }else m.title=clean(stem);
        }else{
            Integer year=yearOf(stem);
            String raw=stem;
            if(titleFolder!=null){
                if(year==null)year=yearOf(titleFolder.getName());
                raw=titleFolder.getName();
            }
            if(year==null){
                File ancestor=file.getParentFile();
                while(ancestor!=null && within(vol.root,ancestor)){
                    if(ancestor.getName().matches("(?:19|20)\\d{2}")){
                        year=Integer.parseInt(ancestor.getName());break;
                    }
                    ancestor=ancestor.getParentFile();
                }
            }
            m.year=year;
            m.title=withoutYear(clean(raw));
            if(m.title.isEmpty())m.title=clean(stem);
        }
        return m;
    }

    private static File findCover(File video,File folder,Map<String,File> covers,List<File> images){
        String stem=stem(video.getName()).toLowerCase(Locale.ROOT);
        for(String name:new String[]{stem,folder.getName().toLowerCase(Locale.ROOT),"poster","folder","cover"}){
            if(generic(folder.getName()) && !name.equals(stem))continue;
            for(String ext:new String[]{".jpg",".jpeg",".png",".webp",".bmp",".gif",".heic",".heif",".tif",".tiff",".avif"}){
                File f=covers.get(name+ext);
                if(f!=null)return f;
            }
        }
        return images.size()==1 && !generic(folder.getName())?images.get(0):null;
    }

    private static boolean within(File root,File candidate){
        try {
            return candidate.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator);
        }catch(IOException e){return false;}
    }
    private static String stem(String n){int i=n.lastIndexOf('.');return i>0?n.substring(0,i):n;}
    private static String clean(String n){
        String t=n.replace('.',' ').replace('_',' ');
        Matcher release=RELEASE.matcher(t);
        if(release.find())t=t.substring(0,release.start());
        return t.replaceAll("[\\[\\]{}]"," ").replaceAll("\\s+"," ").trim();
    }
    private static String withoutYear(String n){
        return YEAR.matcher(n).replaceFirst(" ").replaceAll("[()]"," ").replaceAll("\\s+"," ").trim();
    }
    private static Integer yearOf(String n){
        Matcher m=YEAR.matcher(n);return m.find()?Integer.parseInt(m.group(1)):null;
    }
    private static boolean generic(String n){
        return n.matches("(?:19|20)\\d{2}") ||
               n.matches("(?:19|20)\\d{2}\\s*[-–—]\\s*(?:19|20)\\d{2}") ||
               n.equalsIgnoreCase("series") || n.equalsIgnoreCase("movies") ||
               n.equalsIgnoreCase("films") || n.equalsIgnoreCase("tv shows") || n.equals("مسلسلات");
    }
    private static boolean subtitleFolder(String name){
        return name.equalsIgnoreCase("subs") || name.equalsIgnoreCase("subtitles") ||
                name.equalsIgnoreCase("sub") || name.equals("ترجمات");
    }
    private static boolean excluded(String n){
        String v=n.toLowerCase(Locale.ROOT);
        return v.startsWith(".") || Arrays.asList("android","lost.dir","$recycle.bin",
                "system volume information","@recycle","recycler").contains(v);
    }
    private static boolean video(String n){
        String v=n.toLowerCase(Locale.ROOT);
        for(String extension:new String[]{".mkv",".mp4",".avi",".mov",".m4v",".webm",".ts",".m2ts",".wmv"})
            if(v.endsWith(extension))return true;
        return false;
    }
    private static boolean image(String n){
        String v=n.toLowerCase(Locale.ROOT);
        for(String extension:new String[]{".jpg",".jpeg",".png",".webp",".bmp",".gif",".heic",".heif",".tif",".tiff",".avif"})
            if(v.endsWith(extension))return true;
        return false;
    }
}


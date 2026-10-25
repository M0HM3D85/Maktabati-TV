package org.maktabati.tv;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

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

/** No network and no writes. Each item is a URI supplied by Android's document provider. */
final class DocumentTreeScanner {
    private static final int MAX_DOCUMENTS = 25000;
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)");
    private static final Pattern SEASON_EPISODE = Pattern.compile("(?i)(?:^|[._\\s-])S(\\d{1,2})[._\\s-]*E(\\d{1,3})(?=$|[._\\s-])");
    private static final Pattern ALT_EPISODE = Pattern.compile("(?i)(?:^|[._\\s-])(\\d{1,2})x(\\d{1,3})(?=$|[._\\s-])");
    private static final Pattern SEASON_NAME = Pattern.compile("(?i)^(?:season|series|الموسم)[._\\s-]*\\d{1,2}$");
    private static final Pattern SEASON_MARKER = Pattern.compile("(?i)\\bS\\d{1,2}\\b");
    private static final Pattern RELEASE = Pattern.compile("(?i)\\b(?:2160p|1080p|720p|480p|4k|uhd|hdr|webrip|web\\s?dl|bluray|brrip|remux|x264|x265|h264|h265|hevc|aac|dts|ddp)\\b");
    private static final String[] PROJECTION = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
    };

    static final class Media {
        Uri videoUri;
        Uri posterUri;
        String fileName;
        String title;
        String seriesName;
        String relativePath;
        Integer year;
        int season = -1;
        int episode = -1;
        boolean series;
        final List<Subtitle> subtitles = new ArrayList<>();
    }
    static final class Subtitle {
        Uri uri;
        String fileName;
        int score;
        Subtitle(Uri uri,String fileName,int score){this.uri=uri;this.fileName=fileName;this.score=score;}
    }
    static final class Show {
        String title;
        Uri posterUri;
        final List<Media> episodes = new ArrayList<>();
    }
    static final class Result {
        final List<Media> movies = new ArrayList<>();
        final List<Show> shows = new ArrayList<>();
        int videoCount;
        int posterCount;
        int unreadableFolders;
        boolean truncated;
    }
    private static final class Child {
        final String id, name, mime;
        Child(String id, String name, String mime) {
            this.id=id; this.name=name; this.mime=mime;
        }
        boolean isFolder() {
            return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
        }
    }
    private static final class Frame {
        final String documentId;
        final List<String> dirs;
        Frame(String id, List<String> dirs) { this.documentId=id; this.dirs=dirs; }
    }

    static Result scan(ContentResolver resolver, Uri treeUri) {
        Result result = new Result();
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        String rootName = "USB";
        try (Cursor c = resolver.query(DocumentsContract.buildDocumentUriUsingTree(treeUri,rootId),
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) {
                rootName = c.getString(0);
            }
        } catch (RuntimeException ignored) { /* Use generic source label if provider omits name. */ }

        ArrayDeque<Frame> pending = new ArrayDeque<>();
        pending.add(new Frame(rootId,Collections.singletonList(rootName)));
        LinkedHashMap<String,Show> shows = new LinkedHashMap<>();
        int visited=0;
        while (!pending.isEmpty() && visited<MAX_DOCUMENTS) {
            Frame f = pending.removeFirst();
            Uri childrenUri=DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,f.documentId);
            List<Child> children = new ArrayList<>();
            try (Cursor c=resolver.query(childrenUri,PROJECTION,null,null,null)) {
                if (c==null) { result.unreadableFolders++; continue; }
                while (c.moveToNext()) {
                    if (++visited>MAX_DOCUMENTS) { result.truncated=true; break; }
                    String id=c.getString(0),name=c.getString(1),mime=c.getString(2);
                    if (id!=null && name!=null) children.add(new Child(id,name,mime));
                }
            } catch (RuntimeException ex) {
                result.unreadableFolders++;
                continue;
            }
            Map<String,Child> covers = new HashMap<>();
            int coverCount=0;
            Child soleCover=null;
            for (Child ch:children) {
                if (!ch.isFolder() && isPicture(ch.name)) {
                    covers.put(ch.name.toLowerCase(Locale.ROOT),ch);
                    soleCover=ch; coverCount++;
                }
            }
            String dirName=f.dirs.get(f.dirs.size()-1);
            for (Child ch:children) {
                if (ch.isFolder()) {
                    if (skipDirectory(ch.name)) continue;
                    List<String> next=new ArrayList<>(f.dirs);
                    next.add(ch.name);
                    pending.add(new Frame(ch.id,next));
                } else if (isVideo(ch.name)) {
                    Media item=makeMedia(ch, f.dirs, treeUri);
                    Child poster=findPoster(ch.name,dirName,covers,soleCover,coverCount);
                    if (poster!=null) {
                        item.posterUri=DocumentsContract.buildDocumentUriUsingTree(treeUri,poster.id);
                        result.posterCount++;
                    }
                    if (item.series) {
                        String key=item.seriesName.toLowerCase(Locale.ROOT);
                        Show show=shows.get(key);
                        if (show==null) {
                            show=new Show();show.title=item.seriesName;shows.put(key,show);
                        }
                        show.episodes.add(item);
                        if (show.posterUri==null) show.posterUri=item.posterUri;
                    } else {
                        result.movies.add(item);
                    }
                    result.videoCount++;
                }
            }
        }
        if (!pending.isEmpty()) result.truncated=true;
        result.shows.addAll(shows.values());
        Collections.sort(result.movies, Comparator.comparing(m->m.title.toLowerCase(Locale.ROOT)));
        Collections.sort(result.shows, Comparator.comparing(g->g.title.toLowerCase(Locale.ROOT)));
        for (Show show:result.shows) {
            Collections.sort(show.episodes,(a,b)-> {
                int s=Integer.compare(a.season,b.season);
                return s!=0?s:Integer.compare(a.episode,b.episode);
            });
        }
        return result;
    }

    private static Media makeMedia(Child file,List<String> dirs,Uri treeUri) {
        Media m=new Media();
        m.fileName=file.name;
        m.videoUri=DocumentsContract.buildDocumentUriUsingTree(treeUri,file.id);
        m.relativePath=String.join("/",dirs)+"/"+file.name;
        String stem=stem(file.name);
        Matcher first=SEASON_EPISODE.matcher(stem);
        if (!first.find()) {
            first=ALT_EPISODE.matcher(stem);
            if (!first.find()) first=null;
        }
        String parent=nearestTitleFolder(dirs);
        m.series=first!=null || isWithinSeries(dirs);
        if (m.series) {
            if (first!=null) {
                m.season=parseInt(first.group(1),-1);
                m.episode=parseInt(first.group(2),-1);
            }
            String raw=(parent!=null?parent:(first!=null?stem.substring(0,first.start()):stem));
            String title=clean(raw);
            title=SEASON_MARKER.matcher(title).replaceAll(" ").trim();
            title=withoutYear(title);
            m.seriesName=title.isEmpty()?"مسلسل غير معروف":title;
            m.title=(m.season>0 && m.episode>0)
                    ?String.format(Locale.US,"الموسم %d · الحلقة %d",m.season,m.episode)
                    :clean(stem);
        } else {
            String title=parent!=null && findYear(parent)!=null?parent:stem;
            Integer year=findYear(stem);
            if (year==null && parent!=null) year=findYear(parent);
            if (year==null) {
                for (String folder:dirs) {
                    if (folder.matches("(?:19|20)\\d{2}")) {
                        year=Integer.parseInt(folder);
                        break;
                    }
                }
            }
            m.year=year;
            m.title=withoutYear(clean(title));
            if (m.title.isEmpty()) m.title=clean(stem);
        }
        return m;
    }

    private static boolean isWithinSeries(List<String> dirs) {
        for (String name:dirs) if (name.equalsIgnoreCase("series") ||
            name.equalsIgnoreCase("shows") || name.equalsIgnoreCase("tv shows") ||
            name.equals("مسلسلات")) return true;
        return false;
    }
    private static String nearestTitleFolder(List<String> dirs) {
        for (int i=dirs.size()-1;i>=0;i--) {
            String name=dirs.get(i);
            if (name.equalsIgnoreCase("series") || name.equalsIgnoreCase("shows") ||
                name.equalsIgnoreCase("tv shows") || name.equals("مسلسلات") ||
                name.equalsIgnoreCase("movies") || name.equalsIgnoreCase("films") ||
                SEASON_NAME.matcher(name).matches() || name.matches("(?:19|20)\\d{2}") ||
                name.matches("(?:19|20)\\d{2}\\s*[-–—]\\s*(?:19|20)\\d{2}")) continue;
            // Ignore the scanner's root "USB" placeholder if the provider gave no label.
            if (i==0 && name.equals("USB")) continue;
            return name;
        }
        return null;
    }
    private static Child findPoster(String video, String dir, Map<String,Child> images, Child sole, int count) {
        String original=stem(video).toLowerCase(Locale.ROOT);
        for (String suffix:new String[]{".jpg",".jpeg",".png",".webp",".bmp",".gif",".heic",".heif",".tif",".tiff",".bmp",".gif",".heic",".heif",".tif",".tiff",".avif"}) {
            Child same=images.get(original+suffix);
            if (same!=null) return same;
        }
        for (String suffix:new String[]{".jpg",".jpeg",".png",".webp",".bmp",".gif",".heic",".heif",".tif",".tiff"}) {
            Child folder=images.get(dir.toLowerCase(Locale.ROOT)+suffix);
            if (folder!=null) return folder;
        }
        for (String name:new String[]{"poster","folder","cover"}) {
            for (String suffix:new String[]{".jpg",".png",".webp"}) {
                Child common=images.get(name+suffix);
                if (common!=null && !isGenericDir(dir)) return common;
            }
        }
        return count==1 && !isGenericDir(dir)?sole:null;
    }
    private static boolean isGenericDir(String name) {
        return name.equalsIgnoreCase("movies") || name.equalsIgnoreCase("films") ||
                name.equalsIgnoreCase("series") || name.matches("(?:19|20)\\d{2}") ||
                name.matches("(?:19|20)\\d{2}\\s*[-–—]\\s*(?:19|20)\\d{2}");
    }
    private static String stem(String name) {
        int dot=name.lastIndexOf('.');
        return dot>0?name.substring(0,dot):name;
    }
    private static String clean(String text) {
        String x=text.replace('.',' ').replace('_',' ');
        Matcher release=RELEASE.matcher(x);
        if (release.find()) x=x.substring(0,release.start());
        return x.replaceAll("[\\[\\]{}]"," ")
                .replaceAll("\\s+"," ").trim()
                .replaceAll("^[\\s-]+|[\\s-]+$","");
    }
    private static String withoutYear(String name) {
        return YEAR.matcher(name).replaceFirst("").replaceAll("[()]+"," ").trim();
    }
    private static Integer findYear(String name) {
        Matcher m=YEAR.matcher(name);
        return m.find()?parseInt(m.group(1),0):null;
    }
    private static int parseInt(String text,int fallback) {
        try {return Integer.parseInt(text);}catch(NumberFormatException e){return fallback;}
    }
    private static boolean skipDirectory(String name) {
        String n=name.toLowerCase(Locale.ROOT);
        return n.startsWith(".") || n.equals("android") || n.equals("lost.dir") ||
            n.equals("$recycle.bin") || n.equals("system volume information") ||
            n.equals("@recycle");
    }
    private static boolean isVideo(String name) {
        String n=name.toLowerCase(Locale.ROOT);
        for(String suffix:new String[]{".mkv",".mp4",".avi",".m4v",".mov",".webm",".ts",".m2ts",".wmv"}) {
            if (n.endsWith(suffix))return true;
        }
        return false;
    }
    private static boolean isPicture(String name) {
        String n=name.toLowerCase(Locale.ROOT);
        for(String suffix:new String[]{".jpg",".jpeg",".png",".webp",".bmp",".gif",".heic",".heif",".tif",".tiff",".bmp",".gif",".heic",".heif",".tif",".tiff",".avif"}) {
            if(n.endsWith(suffix))return true;
        }
        return false;
    }
}


package org.maktabati.tv;

import java.util.Locale;

/** Viewer-facing copy only. Filenames and file extensions stay inside the scanner/player. */
final class MediaDisplay {
    private MediaDisplay() {}
    private static final java.util.regex.Pattern EXT=java.util.regex.Pattern.compile("(?i)\\.(mkv|mp4|avi|mov|m4v|wmv|webm|ts|m2ts)$");
    private static final java.util.regex.Pattern RELEASE=java.util.regex.Pattern.compile("(?i)(?:[ ._-]+)(?:2160p|1080p|720p|480p|BluRay|BRRip|WEB-DL|WEBRip|REMUX|HDR|HEVC|x264|x265|H264|H265|AAC|DTS)(?:[ ._-]+.*)?$");
    private static final java.util.regex.Pattern DOT=java.util.regex.Pattern.compile("(?<=\\p{L})\\.(?=\\p{L})");
    private static final java.util.regex.Pattern SPACE=java.util.regex.Pattern.compile("\\s{2,}");
    private static final java.util.Map<String,String> titles=new java.util.LinkedHashMap<String,String>(512,.75f,true){protected boolean removeEldestEntry(java.util.Map.Entry<String,String> e){return size()>4096;}};

    static synchronized String title(String raw){
        if(raw==null)return "عمل بلا عنوان";
        String cached=titles.get(raw);if(cached!=null)return cached;
        String value=raw.trim();
        value=EXT.matcher(value).replaceAll("");
        value=RELEASE.matcher(value).replaceAll("");
        value=DOT.matcher(value.replace('_',' ')).replaceAll(" ");
        value=SPACE.matcher(value).replaceAll(" ").trim();
        value=value.isEmpty()?"عمل بلا عنوان":value;titles.put(raw,value);return value;
    }

    static String subtitle(String raw,int index){
        String n=raw==null?"":raw.toLowerCase(Locale.ROOT);
        String language=n.matches(".*(?:^|[._ -])(?:ar|ara|arabic|arab|عربي|العربية)(?:[._ -]|$).*")?"ترجمة عربية":
            n.matches(".*(?:^|[._ -])(?:en|eng|english)(?:[._ -]|$).*")?"ترجمة إنجليزية":
            n.matches(".*(?:^|[._ -])(?:fr|fre|fra|french)(?:[._ -]|$).*")?"ترجمة فرنسية":
            n.matches(".*(?:^|[._ -])(?:es|spa|spanish)(?:[._ -]|$).*")?"ترجمة إسبانية":"ترجمة "+(index+1);
        if(n.contains("forced"))language+=" (إجبارية)";
        if(n.contains("sdh"))language+=" (وصف صوتي)";
        return language;
    }
}


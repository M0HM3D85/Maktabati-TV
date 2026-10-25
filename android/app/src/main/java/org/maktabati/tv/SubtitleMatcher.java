package org.maktabati.tv;

import java.util.Locale;

/** Pure filename matcher: never opens or rewrites subtitle/video files. */
final class SubtitleMatcher {
    private static final String[] EXTENSIONS={".srt",".ass",".ssa",".vtt",".idx",".sub"};
    static boolean isSubtitle(String name){
        String lower=name.toLowerCase(Locale.ROOT);
        for(String ext:EXTENSIONS)if(lower.endsWith(ext))return true;
        return false;
    }
    static String stem(String name){
        int p=name.lastIndexOf('.');
        return (p>0?name.substring(0,p):name).toLowerCase(Locale.ROOT).trim();
    }
    static int score(String video, String subtitle,boolean inSubsDirectory,boolean singleVideo){
        if(!isSubtitle(subtitle))return -1;
        String v=stem(video),s=stem(subtitle);
        if(s.equals(v))return 100;
        if(s.startsWith(v) && s.length()>v.length()){
            char delimiter=s.charAt(v.length());
            if(delimiter=='.'||delimiter=='-'||delimiter=='_'||delimiter==' '||delimiter=='[')return 80;
        }
        // Generic filenames such as English.srt in Subs/ are only associated when
        // there is a single video in the containing movie folder.
        if(singleVideo && inSubsDirectory)return 20;
        if(singleVideo && (s.equals("arabic")||s.equals("english")||s.equals("forced")||
            s.equals("العربية")||s.equals("الانجليزية")))return 15;
        return -1;
    }
    static int chooseAutomaticScore(int candidateScore) {
        // Do not automatically load an ambiguous generic English/Forced subtitle.
        return candidateScore>=80?candidateScore:-1;
    }
}


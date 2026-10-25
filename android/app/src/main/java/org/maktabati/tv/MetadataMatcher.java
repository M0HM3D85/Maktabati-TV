package org.maktabati.tv;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Conservative library matching: skip ambiguity instead of saving a wrong film. */
final class MetadataMatcher {
    private MetadataMatcher(){}
    static String normalized(String text){
        if(text==null)return "";
        String clean=Normalizer.normalize(MediaDisplay.title(text),Normalizer.Form.NFKC);
        return clean.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}_]+"," ")
                .replaceAll("\\s+"," ").trim();
    }
    static TmdbService.Candidate unambiguous(List<TmdbService.Candidate> choices,String title,Integer year){
        if(year==null)return unambiguous(choices,title);
        java.util.List<TmdbService.Candidate> sameYear=new java.util.ArrayList<>();
        if(choices!=null)for(TmdbService.Candidate c:choices)if(c!=null&&c.date!=null&&c.date.startsWith(year+"-"))sameYear.add(c);
        return unambiguous(sameYear,title);
    }
    static TmdbService.Candidate unambiguous(List<TmdbService.Candidate> choices,String localTitle){
        if(choices==null)return null;
        String key=normalized(localTitle);
        if(key.isEmpty() || "ط¹ظ…ظ„ ط¨ظ„ط§ ط¹ظ†ظˆط§ظ†".equals(key))return null;
        TmdbService.Candidate selected=null;
        for(TmdbService.Candidate c:choices){
            if(c==null)continue;
            if(!key.equals(normalized(c.title)) && !key.equals(normalized(c.original)))continue;
            if(selected!=null && selected.id!=c.id)return null;
            selected=c;
        }
        return selected;
    }
}


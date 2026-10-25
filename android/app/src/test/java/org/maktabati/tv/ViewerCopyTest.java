package org.maktabati.tv;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Prevent raw media names and ambiguous external matches from returning to viewer UI. */
public final class ViewerCopyTest {
    @Test public void stripsReleaseCodecsAndExtensionFromFilmTitle(){
        String display=MediaDisplay.title("The Matrix WEB-DL 1080p.mkv");
        assertEquals("The Matrix",display);
        assertFalse(display.contains("mkv"));
        assertFalse(display.contains("WEB"));
    }
    @Test public void subtitleNamesAreHumanReadable(){
        assertEquals("ترجمة عربية",MediaDisplay.subtitle("Film.2026.ar.srt",0));
        assertEquals("ترجمة إنجليزية",MediaDisplay.subtitle("film.EN.srt",1));
        assertEquals("ترجمة 3",MediaDisplay.subtitle("film.commentary.srt",2));
    }
    @Test public void normalizesPunctuationForConservativeMatching(){
        assertEquals("the matrix",MetadataMatcher.normalized("The.Matrix"));
        assertEquals("فيلم عربي",MetadataMatcher.normalized("فيلم-عربي"));
    }
    @Test public void exactSingleCandidateIsAccepted(){
        TmdbService.Candidate a=new TmdbService.Candidate(1,"ذا ماتريكس","The Matrix","1999-03-31","");
        assertSame(a,MetadataMatcher.unambiguous(Arrays.asList(a),"The Matrix"));
    }
    @Test public void ambiguousCandidateIsRejected(){
        TmdbService.Candidate a=new TmdbService.Candidate(1,"Film","Film","2000-01-01","");
        TmdbService.Candidate b=new TmdbService.Candidate(2,"Film","Film","2024-01-01","");
        assertNull(MetadataMatcher.unambiguous(Arrays.asList(a,b),"Film"));
    }
    @Test public void unrelatedCandidateIsRejected(){
        TmdbService.Candidate a=new TmdbService.Candidate(1,"Different film","Different film","2024-01-01","");
        assertNull(MetadataMatcher.unambiguous(Arrays.asList(a),"My Film"));
    }
}


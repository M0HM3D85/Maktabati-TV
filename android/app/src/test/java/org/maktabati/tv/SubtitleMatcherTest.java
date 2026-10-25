package org.maktabati.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public final class SubtitleMatcherTest {
    @Test public void exactMatchingSiblingSrt(){
        String name="Sample Film (2026) [1080p].mp4";
        assertEquals(100,SubtitleMatcher.score(name,"Sample Film (2026) [1080p].srt",false,true));
    }
    @Test public void acceptsVlcSrtAssAndLanguageSuffixes(){
        assertEquals(80,SubtitleMatcher.score("Movie.mkv","Movie.ar.srt",false,true));
        assertEquals(80,SubtitleMatcher.score("Movie.mkv","Movie.eng.ass",false,true));
        assertEquals(100,SubtitleMatcher.score("Movie.mkv","movie.ASS",false,true));
    }
    @Test public void neverAutoAssignGenericUnrelatedLanguage(){
        int score=SubtitleMatcher.score("Film.mp4","English.srt",true,true);
        assertEquals(20,score);
        assertEquals(-1,SubtitleMatcher.chooseAutomaticScore(score));
    }
    @Test public void neverCrossAssociateBetweenEpisodes(){
        assertEquals(-1,SubtitleMatcher.score("Show.S01E02.mkv","Show.S01E03.srt",false,false));
        assertEquals(100,SubtitleMatcher.score("Show.S01E02.mkv","Show.S01E02.srt",false,false));
    }
    @Test public void rejectsZipAndMoviePictures(){
        assertFalse(SubtitleMatcher.isSubtitle("subtitle.zip"));
        assertEquals(-1,SubtitleMatcher.score("movie.mp4","movie.jpg",false,true));
    }
    @Test public void noWrongAutoSelectionWhenNameDoesNotMatch(){
        assertEquals(-1,SubtitleMatcher.score("movie.mp4","trailer.srt",false,false));
    }
}


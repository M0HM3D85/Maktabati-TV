package org.maktabati.tv;
import org.junit.Test;import static org.junit.Assert.*;import java.util.Arrays;
public class MetadataYearTest {
 @Test public void distinguishesRemakes(){assertEquals(2,MetadataMatcher.unambiguous(Arrays.asList(new TmdbService.Candidate(1,"Film","Film","1996-01-01",""),new TmdbService.Candidate(2,"Film","Film","2026-02-01","")),"Film",2026).id);}
 @Test public void refusesWrongYear(){assertNull(MetadataMatcher.unambiguous(Arrays.asList(new TmdbService.Candidate(1,"Film","Film","1996-01-01","")),"Film",2026));}
 @Test public void refusesSameYearAmbiguity(){assertNull(MetadataMatcher.unambiguous(Arrays.asList(new TmdbService.Candidate(1,"Film","Film","2026-01-01",""),new TmdbService.Candidate(2,"Film","Film","2026-02-01","")),"Film",2026));}
}

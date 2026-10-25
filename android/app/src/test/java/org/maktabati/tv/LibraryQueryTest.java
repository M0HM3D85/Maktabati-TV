package org.maktabati.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public class LibraryQueryTest {
    @Test public void nameAndYearMatchTogether(){assertTrue(LibraryQuery.matches("The Matrix",1999,"matrix 1999",-1));assertFalse(LibraryQuery.matches("The Matrix",1999,"matrix 2000",-1));}
    @Test public void acceptsArabicAndPersianDigits(){assertTrue(LibraryQuery.matches("فيلم",2026,"٢٠٢٦",-1));assertTrue(LibraryQuery.matches("فيلم",2026,"۲۰۲۶",-1));}
    @Test public void yearFilterDoesNotGuessUnknownYear(){assertFalse(LibraryQuery.matches("Film",null,"",2026));assertFalse(LibraryQuery.matches("Film",2025,"",2026));assertTrue(LibraryQuery.matches("Film",2026,"",2026));}
    @Test public void newerFirstAndUnknownLast(){assertTrue(LibraryQuery.compare("A",2026,"B",1999,2)<0);assertTrue(LibraryQuery.compare("A",null,"B",1999,2)>0);assertTrue(LibraryQuery.compare("A",null,"B",1999,3)>0);}
    @Test public void titleSortIsStableAcrossCase(){assertEquals(0,LibraryQuery.compare("MATRIX",1999,"matrix",1999,0));assertTrue(LibraryQuery.compare("A",2026,"B",2026,1)>0);}
}

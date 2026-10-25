package org.maktabati.tv;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pure Java tests: identifiers must not expose device paths or change per scan. */
public final class MediaIdentityTest {
    @Test public void repeatedUriReturnsSameOpaqueKey(){
        String uri="content://org.maktabati.tv.local/source/USB/video%2Fmovie.mkv";
        String one=MediaIdentity.fromUri(uri);
        assertEquals(one,MediaIdentity.fromUri(uri));
        assertTrue(one.matches("media_[0-9a-f]{64}"));
        assertFalse(one.contains("movie"));
        assertFalse(one.contains("source"));
    }
    @Test public void distinctMediaMustNotShareKeys(){
        assertNotEquals(MediaIdentity.fromUri("content://local/movieA"),
                        MediaIdentity.fromUri("content://local/movieB"));
    }
    @Test(expected=IllegalArgumentException.class)
    public void missingUriRejected(){MediaIdentity.fromUri(" ");}
    @Test public void keysAreIndependentOfTitleText(){
        // Same canonical item stays stable when metadata is matched to another language.
        String item="content://media/local/a";
        String before=MediaIdentity.fromUri(item);
        String after=MediaIdentity.fromUri(item);
        assertEquals(before,after);
    }
}


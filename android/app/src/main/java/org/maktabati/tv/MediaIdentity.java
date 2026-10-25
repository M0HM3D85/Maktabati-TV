package org.maktabati.tv;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Opaque, stable-on-the-same-source identifier; never uploads a local path or URI. */
final class MediaIdentity {
    private MediaIdentity() {}
    static String fromUri(String localUri) {
        if (localUri == null || localUri.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing local media URI");
        }
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(
                    ("Maktabati-v1\0"+localUri).getBytes(StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder("media_");
            for (byte b:bytes) {
                int v=b&0xff;
                s.append(Character.forDigit(v>>>4,16));
                s.append(Character.forDigit(v&15,16));
            }
            return s.toString();
        }catch(Exception e){throw new IllegalStateException("Cannot identify local media",e);}
    }
}


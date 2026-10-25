package org.maktabati.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public class MetadataFailureTest {
    @Test public void diagnosticsNeverEchoExceptionDetails(){
        Exception e=new java.io.IOException("Bearer SECRET /storage/private-film");
        assertFalse(MetadataFailure.message(e).contains("SECRET"));
        assertFalse(MetadataFailure.message(e).contains("storage"));
    }
    @Test public void distinguishesActionableFailures(){
        assertEquals("AUTH",MetadataFailure.code(new MetadataFailure.Http(401)));
        assertEquals("AUTH",MetadataFailure.code(new MetadataFailure.Http(403)));
        assertEquals("RATE_LIMIT",MetadataFailure.code(new MetadataFailure.Http(429)));
        assertEquals("HTTP",MetadataFailure.code(new MetadataFailure.Http(503)));
        assertEquals("TLS",MetadataFailure.code(new javax.net.ssl.SSLHandshakeException("hidden")));
        assertEquals("DNS",MetadataFailure.code(new java.net.UnknownHostException("hidden")));
        assertEquals("TIMEOUT",MetadataFailure.code(new java.net.SocketTimeoutException()));
        assertEquals("DECRYPT",MetadataFailure.code(new javax.crypto.AEADBadTagException()));
    }
    @Test public void acceptsPastedBearerPrefixWithoutChangingSecret()throws Exception{
        String token="abcdefghijklmnopqrstuvwxyz0123456789.abc";
        assertEquals(token,MetadataFailure.normalizeToken("  Bearer "+token+"  "));
    }
    @Test public void rejectsBrokenPastesAndMissingTokens(){
        for(String value:new String[]{"","short","abcdefghijklmnopqrstuvwxyz0123456789\nabc"}){
            try{MetadataFailure.normalizeToken(value);fail();}catch(MetadataFailure.Credential expected){}
        }
    }
}

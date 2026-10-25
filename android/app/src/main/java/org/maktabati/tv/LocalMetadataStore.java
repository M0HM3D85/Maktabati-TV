package org.maktabati.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** App-private encrypted TMDb credential and bounded local metadata; no USB writes. */
final class LocalMetadataStore {
    private static final String ALIAS="Maktabati_TMDB_v1";
    private final java.util.Map<String,TmdbService.Detail> memory=new java.util.HashMap<>();
    synchronized void clearMemory(){memory.clear();}
    private final SharedPreferences prefs;
    LocalMetadataStore(Context ctx){
        prefs=ctx.getSharedPreferences("metadata_private_v1",Context.MODE_PRIVATE);
    }
    boolean configured(){return prefs.contains("tmdb_token");}
    String token() throws Exception {
        String payload=prefs.getString("tmdb_token","");
        if(payload.isEmpty())throw new MetadataFailure.Credential("MISSING");
        try {
        String[] pieces=payload.split(":",2);if(pieces.length!=2)throw new MetadataFailure.Credential("DECRYPT");
        SecretKey key=getKey(false);if(key==null)throw new MetadataFailure.Credential("DECRYPT");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key,
                    new GCMParameterSpec(128,Base64.decode(pieces[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(pieces[1],Base64.NO_WRAP)),
                StandardCharsets.UTF_8);
        }catch(Exception e){throw new MetadataFailure.Credential("DECRYPT");}
    }
    void saveToken(String value) throws Exception {
        value=MetadataFailure.normalizeToken(value);
        SecretKey key=getKey(true);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key);
        String iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        String data=Base64.encodeToString(
                cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!prefs.edit().putString("tmdb_token",iv+":"+data).commit())
            throw new IllegalStateException("تعذر حفظ إعداد الربط");
    }
    void disconnect(){
        prefs.edit().remove("tmdb_token").apply();
    }
    private static SecretKey getKey(boolean create) throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if(ks.containsAlias(ALIAS))return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
        if(!create)return null;
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return gen.generateKey();
    }

    private String cacheKey(String title,Integer year,boolean series){
        try{
            String id=(series?"tv:":"movie:")+title.trim().toLowerCase(java.util.Locale.ROOT)+
                    ":"+(year==null?"":year);
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder("film_");
            for(int i=0;i<12;i++){int n=bytes[i]&255;out.append(Character.forDigit(n>>>4,16));out.append(Character.forDigit(n&15,16));}
            return out.toString();
        }catch(Exception e){throw new IllegalStateException("Cache key error");}
    }

    synchronized TmdbService.Detail load(String title,Integer year,boolean series){
        String key=title+"|"+year+"|"+series;if(memory.containsKey(key))return memory.get(key);
        try{
            String value=prefs.getString(cacheKey(title,year,series),null);
            if(value==null){memory.put(key,null);return null;}
            JSONObject json=new JSONObject(value);
            TmdbService.Detail detail=TmdbService.Detail.fromJson(json.getJSONObject("data"));memory.put(key,detail);return detail;
        }catch(Exception ignored){return null;}
    }
    synchronized void save(String title,Integer year,boolean series,TmdbService.Detail metadata){
        memory.clear();
        try{
            JSONObject json=new JSONObject();
            json.put("data",metadata.toJson());
            json.put("time",System.currentTimeMillis());
            // User triggers one lookup at a time; metadata remains for offline use.
            prefs.edit().putString(cacheKey(title,year,series),json.toString()).apply();
        }catch(JSONException ignored){}
    }
}


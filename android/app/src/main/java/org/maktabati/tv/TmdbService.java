package org.maktabati.tv;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Opt-in TMDB v3 client. HTTPS only. No local filename, file URI or USB path is uploaded. */
final class TmdbService {
    static final String CREDIT="This product uses the TMDB API but is not endorsed or certified by TMDB.";
    static final class Candidate {
        final int id; final String title,original,date,poster;
        Candidate(int id,String title,String original,String date,String poster){
            this.id=id;this.title=title;this.original=original;this.date=date;this.poster=poster;
        }
        String label(){String y=date.length()>=4?date.substring(0,4):"سنة غير معروفة";return title+"  ·  "+y+(original.equals(title)?"":"\n"+original);}
    }
    static final class Detail {
        int id;boolean series;String title="",original="",overview="",release="",genres="",
                cast="",director="",imdbId="",poster="",backdrop="",language="ar-SA";
        int runtime; double score;int votes;
        JSONObject toJson(){
            JSONObject j=new JSONObject();
            try {
                j.put("id",id);j.put("series",series);j.put("title",title);
                j.put("original",original);j.put("overview",overview);
                j.put("release",release);j.put("genres",genres);j.put("cast",cast);
                j.put("director",director);j.put("imdbId",imdbId);
                j.put("poster",poster);j.put("backdrop",backdrop);
                j.put("language",language);j.put("runtime",runtime);
                j.put("score",score);j.put("votes",votes);
            }catch(JSONException ignored){}
            return j;
        }
        static Detail fromJson(JSONObject j){
            Detail d=new Detail();d.id=j.optInt("id");d.series=j.optBoolean("series");
            d.title=j.optString("title");d.original=j.optString("original");
            d.overview=j.optString("overview");d.release=j.optString("release");
            d.genres=j.optString("genres");d.cast=j.optString("cast");
            d.director=j.optString("director");d.imdbId=j.optString("imdbId");
            d.poster=j.optString("poster");d.backdrop=j.optString("backdrop");
            d.language=j.optString("language","ar-SA");d.runtime=j.optInt("runtime");
            d.score=j.optDouble("score");d.votes=j.optInt("votes");return d;
        }
    }

    static List<Candidate> search(String bearer,boolean series,String workTitle) throws IOException,JSONException {
        String q=workTitle.trim();
        if(q.isEmpty() || q.length()>150)throw new IOException("عنوان البحث غير صالح.");
        // Search name/year, never send filename, UUID, directory, or path to TMDb.
        String route=series?"tv":"movie";
        Uri url=new Uri.Builder().scheme("https").authority("api.themoviedb.org")
                .appendPath("3").appendPath("search").appendPath(route)
                .appendQueryParameter("query",q)
                .appendQueryParameter("language","ar-SA")
                .appendQueryParameter("include_adult","false").build();
        JSONObject json=request(bearer,url);
        JSONArray entries=json.optJSONArray("results");
        List<Candidate> out=new ArrayList<>();
        if(entries!=null)for(int i=0;i<Math.min(entries.length(),12);i++){
            JSONObject item=entries.optJSONObject(i);if(item==null || item.optInt("id")<=0)continue;
            out.add(new Candidate(item.optInt("id"),
                item.optString(series?"name":"title"),
                item.optString(series?"original_name":"original_title"),
                item.optString(series?"first_air_date":"release_date"),
                item.optString("poster_path")));
        }
        return out;
    }

    static Detail details(String bearer,boolean series,int id) throws IOException,JSONException {
        Detail detail=parseDetail(request(bearer,urlForDetails(series,id,"ar-SA")),series);
        if(detail.overview.trim().isEmpty()){
            Detail arabic=parseDetail(request(bearer,urlForDetails(series,id,"ar")),series);
            if(!arabic.overview.trim().isEmpty()){
                detail.overview=arabic.overview;detail.language="ar";
            }
            if(detail.title.isEmpty())detail.title=arabic.title;
            if(detail.genres.isEmpty())detail.genres=arabic.genres;
        }
        if(detail.overview.trim().isEmpty()){
            Detail fallback=parseDetail(request(bearer,urlForDetails(series,id,"en-US")),series);
            if(fallback.overview.trim().length()>0){
                detail.overview=fallback.overview;detail.language="en-US";
            }
            if(detail.title.isEmpty())detail.title=fallback.title;
            if(detail.genres.isEmpty())detail.genres=fallback.genres;
        }
        return detail;
    }

    static Uri image(String relative,String width){
        if(relative==null||!relative.matches("/[A-Za-z0-9_/.-]{3,220}") ||
           relative.contains("..") || !("w342".equals(width)||"w500".equals(width)||"w780".equals(width)))
            return null;
        return Uri.parse("https://image.tmdb.org/t/p/"+width+relative);
    }
    private static Uri urlForDetails(boolean series,int id,String language){
        if(id<=0)throw new IllegalArgumentException("Bad TMDb identifier");
        return new Uri.Builder().scheme("https").authority("api.themoviedb.org")
                .appendPath("3").appendPath(series?"tv":"movie")
                .appendPath(Integer.toString(id)).appendQueryParameter("language",language)
                .appendQueryParameter("append_to_response","credits,external_ids").build();
    }

    private static Detail parseDetail(JSONObject json,boolean series){
        Detail d=new Detail();
        d.id=json.optInt("id");d.series=series;
        d.title=json.optString(series?"name":"title");
        d.original=json.optString(series?"original_name":"original_title");
        d.overview=json.optString("overview");
        d.release=json.optString(series?"first_air_date":"release_date");
        d.score=json.optDouble("vote_average");d.votes=json.optInt("vote_count");
        d.runtime=json.optInt("runtime");
        if(series){JSONArray times=json.optJSONArray("episode_run_time");if(times!=null && times.length()>0)d.runtime=times.optInt(0);}
        d.poster=json.optString("poster_path");d.backdrop=json.optString("backdrop_path");
        d.imdbId=json.optString("imdb_id");
        if(series)d.imdbId=json.optJSONObject("external_ids")==null?"":json.optJSONObject("external_ids").optString("imdb_id");
        JSONArray genres=json.optJSONArray("genres");
        if(genres!=null)for(int i=0;i<Math.min(genres.length(),5);i++){
            String v=genres.optJSONObject(i)==null?"":genres.optJSONObject(i).optString("name");
            if(!v.isEmpty())d.genres+=(d.genres.isEmpty()?"":" • ")+v;
        }
        JSONObject credits=json.optJSONObject("credits");
        if(credits!=null){
            JSONArray cast=credits.optJSONArray("cast");
            if(cast!=null)for(int i=0;i<Math.min(cast.length(),4);i++){
                String name=cast.optJSONObject(i)==null?"":cast.optJSONObject(i).optString("name");
                if(!name.isEmpty())d.cast+=(d.cast.isEmpty()?"":" · ")+name;
            }
            JSONArray crew=credits.optJSONArray("crew");
            if(crew!=null)for(int i=0;i<Math.min(crew.length(),100);i++){
                JSONObject c=crew.optJSONObject(i);
                if(c!=null && "Director".equals(c.optString("job"))){
                    d.director+=(d.director.isEmpty()?"":" · ")+c.optString("name");
                    if(d.director.length()>90)break;
                }
            }
        }
        if(series){
            JSONArray creators=json.optJSONArray("created_by");
            if(creators!=null && creators.length()>0)d.director=creators.optJSONObject(0).optString("name");
        }
        return d;
    }

    // An unauthenticated request proves TLS/HTTP reachability, never account validity.
    static void probeNetwork()throws IOException{
        javax.net.ssl.HttpsURLConnection c=(javax.net.ssl.HttpsURLConnection)new URL("https://api.themoviedb.org/3/configuration").openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);
        try{int status=c.getResponseCode();if(status!=401&&status!=200)throw new MetadataFailure.Http(status);}
        finally{c.disconnect();}
    }
    private static JSONObject request(String bearer,Uri uri)throws IOException,JSONException{
        bearer=MetadataFailure.normalizeToken(bearer);
        if(!"https".equals(uri.getScheme()) ||
           !"api.themoviedb.org".equals(uri.getHost()))
            throw new IOException("تم رفض اتصال غير موثوق.");
        HttpURLConnection connection=(HttpURLConnection)new URL(uri.toString()).openConnection();
        connection.setConnectTimeout(8000);connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("Authorization","Bearer "+bearer);
        connection.setRequestProperty("User-Agent","MaktabatiTV/0.9.5");
        try{
            int code=connection.getResponseCode();
            if(code==401||code==403)throw new MetadataFailure.Http(code);
            if(code==429)throw new MetadataFailure.Http(code);
            if(code<200||code>=300)throw new MetadataFailure.Http(code);
            int cap=1024*1024;
            try(InputStream stream=connection.getInputStream();
                ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
                byte[] bytes=new byte[8192];int n;
                while((n=stream.read(bytes))!=-1){
                    if(buffer.size()+n>cap)throw new IOException("رد TMDb أكبر من الحد المسموح.");
                    buffer.write(bytes,0,n);
                }
                return new JSONObject(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
            }
        }finally{connection.disconnect();}
    }
}


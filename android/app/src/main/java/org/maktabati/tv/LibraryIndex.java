package org.maktabati.tv;
import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
/** App-private atomic catalog. Never stores credentials or writes to USB. */
final class LibraryIndex {
 static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"library-index-v1.json"));}
 static JSONObject media(DocumentTreeScanner.Media m)throws Exception{
  JSONObject j=new JSONObject().put("uri",m.videoUri.toString()).put("poster",m.posterUri==null?"":m.posterUri.toString()).put("file",m.fileName).put("title",m.title).put("show",m.seriesName).put("path",m.relativePath).put("year",m.year==null?-1:m.year).put("season",m.season).put("episode",m.episode).put("series",m.series);
  JSONArray subs=new JSONArray();for(DocumentTreeScanner.Subtitle s:m.subtitles)subs.put(new JSONObject().put("uri",s.uri.toString()).put("name",s.fileName).put("score",s.score));return j.put("subs",subs);
 }
 static DocumentTreeScanner.Media media(JSONObject j)throws Exception{
  DocumentTreeScanner.Media m=new DocumentTreeScanner.Media();m.videoUri=Uri.parse(j.getString("uri"));String p=j.optString("poster");m.posterUri=p.isEmpty()?null:Uri.parse(p);m.fileName=j.optString("file");m.title=j.optString("title");m.seriesName=j.optString("show");m.relativePath=j.optString("path");int y=j.optInt("year",-1);m.year=y<0?null:y;m.season=j.optInt("season",-1);m.episode=j.optInt("episode",-1);m.series=j.optBoolean("series");JSONArray a=j.optJSONArray("subs");if(a!=null)for(int i=0;i<a.length();i++){JSONObject s=a.getJSONObject(i);m.subtitles.add(new DocumentTreeScanner.Subtitle(Uri.parse(s.getString("uri")),s.optString("name"),s.optInt("score")));}return m;
 }
 static synchronized boolean save(Context c,DocumentTreeScanner.Result result){
  FileOutputStream out=null;AtomicFile f=file(c);
  try{JSONArray movies=new JSONArray(),shows=new JSONArray();for(DocumentTreeScanner.Media m:result.movies)movies.put(media(m));for(DocumentTreeScanner.Show s:result.shows){JSONArray eps=new JSONArray();for(DocumentTreeScanner.Media m:s.episodes)eps.put(media(m));shows.put(new JSONObject().put("title",s.title).put("poster",s.posterUri==null?"":s.posterUri.toString()).put("episodes",eps));}
   byte[] bytes=new JSONObject().put("schema",1).put("at",System.currentTimeMillis()).put("movies",movies).put("shows",shows).put("posters",result.posterCount).toString().getBytes("UTF-8");out=f.startWrite();out.write(bytes);f.finishWrite(out);return true;
  }catch(Exception e){if(out!=null)f.failWrite(out);return false;}
 }
 static synchronized DocumentTreeScanner.Result load(Context c){try{byte[] b=file(c).readFully();if(b.length>16000000)return null;JSONObject j=new JSONObject(new String(b,"UTF-8"));if(j.getInt("schema")!=1)return null;DocumentTreeScanner.Result r=new DocumentTreeScanner.Result();JSONArray a=j.getJSONArray("movies");for(int i=0;i<a.length();i++)r.movies.add(media(a.getJSONObject(i)));a=j.getJSONArray("shows");for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);DocumentTreeScanner.Show s=new DocumentTreeScanner.Show();s.title=v.getString("title");String p=v.optString("poster");s.posterUri=p.isEmpty()?null:Uri.parse(p);JSONArray eps=v.getJSONArray("episodes");for(int n=0;n<eps.length();n++)s.episodes.add(media(eps.getJSONObject(n)));r.shows.add(s);r.videoCount+=s.episodes.size();}r.videoCount+=r.movies.size();r.posterCount=j.optInt("posters");return r;}catch(Exception e){return null;}}
}

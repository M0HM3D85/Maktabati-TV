package org.maktabati.tv;
import android.content.Context;import org.json.JSONObject;
final class ArabicOverview {
 private static JSONObject summaries;
 static synchronized String text(Context c,TmdbService.Detail d){
  if(d==null)return "";String original=d.overview==null?"":d.overview;
  if(original.matches("(?s).*[\\u0600-\\u06ff].*"))return original;
  try{if(summaries==null){try(java.io.InputStream in=c.getAssets().open("arabic-overviews.json");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);summaries=new JSONObject(out.toString("UTF-8"));}}
   String translated=summaries.optString((d.series?"tv:":"movie:")+d.id);if(!translated.isEmpty())return translated;
  }catch(Exception ignored){}
  return original.isEmpty()?"لا يتوفر ملخص لهذا العمل بعد.":"لا يتوفر ملخص عربي لهذا العمل بعد. يمكنك عرض الوصف الأصلي من تفاصيله.";
 }
}

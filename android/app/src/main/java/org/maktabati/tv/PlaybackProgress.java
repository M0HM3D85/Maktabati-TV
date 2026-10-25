package org.maktabati.tv;
import android.content.*;
/** Only measured VLC results, never elapsed wall-clock estimates. */
final class PlaybackProgress {
 static final int REQUEST=781;
 static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("vlc_progress_v1",0);}
 static long position(Context c,String id){return prefs(c).getLong(id+".position",0);}
 static String label(Context c,String id){long p=position(c,id),d=prefs(c).getLong(id+".duration",0);return d>0?" · المشاهدة "+Math.min(100,Math.round(p*100.0/d))+"٪":"";}
 static void prepare(Context c,Intent i,String id){i.setClassName("org.videolan.vlc","org.videolan.vlc.gui.video.VideoPlayerActivity");long p=position(c,id),d=prefs(c).getLong(id+".duration",0);if(p>0&&p<d-5000)i.putExtra("position",p);}
 static boolean accept(Context c,String id,int result,Intent data){
  if(id==null||data==null||!"org.videolan.vlc.player.result".equals(data.getAction())||result!=android.app.Activity.RESULT_OK)return false;
  long p=data.getLongExtra("extra_position",-1),d=data.getLongExtra("extra_duration",-1);if(p<0||d<=0||p>d+5000)return false;
  return prefs(c).edit().putLong(id+".position",Math.min(p,d)).putLong(id+".duration",d).putLong(id+".updated",System.currentTimeMillis()).commit();
 }
}

package org.maktabati.tv;

import java.io.*;
import java.util.*;

/** Deletion is restricted to a dedicated single-movie directory, rechecked after confirmation. */
final class MovieDeletion {
 static final class Plan {
  final File root, video, folder; final SortedMap<String,String> snapshot;
  final long bytes; final int count;
  Plan(File r,File v,File f,SortedMap<String,String> s,long b,int n){root=r;video=v;folder=f;snapshot=s;bytes=b;count=n;}
 }
 static Plan preview(File root,File video)throws IOException {
  root=root.getCanonicalFile();
  if(!video.getAbsoluteFile().equals(video.getCanonicalFile()))throw new IOException("المسارات المرتبطة غير مسموحة للحذف");
  video=video.getCanonicalFile();File folder=video.getParentFile();
  if(!video.isFile()||folder==null||folder.equals(root)||!folder.getPath().startsWith(root.getPath()+File.separator))throw new IOException("اختر فيلمًا داخل مجلد مستقل؛ لا يمكن حذف جذر القرص");
  if(!folder.canWrite()||!folder.getParentFile().canWrite())throw new IOException("لا توجد صلاحية حذف لهذا المجلد على القرص. يمكن حذف الأفلام المنقولة إلى مجلد مكتبتي القابل للكتابة.");
  SortedMap<String,String> s=new TreeMap<>();long[] stats={0,0,0};walk(folder,folder,s,stats);
  if(stats[2]!=1||!isVideo(video))throw new IOException("الحذف متاح لمجلد فيلم واحد فقط؛ هذا المجلد يحتوي عدة فيديوهات أو ليس مجلد فيلم");
  return new Plan(root,video,folder,s,stats[0],(int)stats[1]);
 }
 static boolean isVideo(File f){return f.getName().toLowerCase(Locale.ROOT).matches(".*\\.(mkv|mp4|m4v|avi|mov|webm|ts|m2ts|mpg|mpeg|wmv|flv|vob|3gp)$");}
 static void walk(File base,File f,SortedMap<String,String> s,long[] stats)throws IOException {
  if(s.size()>5000)throw new IOException("عدد ملفات المجلد كبير جدًا للحذف الآمن");
  if(!f.getAbsoluteFile().equals(f.getCanonicalFile())||(!f.equals(base)&&!f.getCanonicalPath().startsWith(base.getCanonicalPath()+File.separator)))throw new IOException("المجلد يحتوي رابطًا خارج مساره");
  if(!f.canWrite())throw new IOException("أحد الملفات غير قابل للحذف");
  s.put(f.getAbsolutePath(),(f.isDirectory()?"d":"f")+":"+f.length()+":"+f.lastModified());
  if(f.isDirectory()){File[] children=f.listFiles();if(children==null)throw new IOException("تعذر قراءة المجلد كاملًا");for(File child:children)walk(base,child,s,stats);}
  else {stats[0]+=f.length();stats[1]++;if(isVideo(f))stats[2]++;}
 }
 static void execute(Plan plan)throws IOException {
  Plan fresh=preview(plan.root,plan.video);
  if(!fresh.snapshot.equals(plan.snapshot))throw new IOException("تغير محتوى المجلد؛ افتح تأكيد الحذف مجددًا");
  // Each candidate comes from the checked snapshot; never enumerate a newly added path while deleting.
  ArrayList<String> paths=new ArrayList<>(plan.snapshot.keySet());paths.sort((a,b)->Integer.compare(b.length(),a.length()));
  for(String path:paths){File f=new File(path);if(!f.getCanonicalPath().equals(path)||!f.delete())throw new IOException("توقف الحذف؛ تعذر حذف أحد الملفات. حدّث المكتبة للتحقق.");}
 }
}

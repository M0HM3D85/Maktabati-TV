package org.maktabati.tv;
import android.content.Context;import org.json.*;import java.io.*;import java.util.*;import java.util.zip.CRC32;
/** Paired LAN transfers, bounded chunks, no overwrite, resumable staging on the selected USB. */
final class NetworkFiles {
 static File root(Context c)throws Exception{
  java.util.List<UsbVolumes.Volume> volumes=UsbVolumes.removable(c);if(volumes.size()!=1)throw new IOException("يلزم قرص USB واحد متصل");
  File volume=volumes.get(0).root;
  if(volume.canWrite()){File shared=new File(volume,"MaktabatiUploads");if((shared.isDirectory()||shared.mkdirs())&&shared.canWrite())return shared;}
  File[] dirs=c.getExternalFilesDirs(null);if(dirs!=null)for(File d:dirs)if(d!=null&&d.getCanonicalPath().startsWith(volume.getCanonicalPath()+"/")&&d.canWrite()){File f=new File(d,"IncomingLibrary");if(f.isDirectory()||f.mkdirs())return f;}
  throw new IOException("التلفزيون لم يمنح مكتبتي صلاحية كتابة على القرص");
 }
 static File child(File root,String relative)throws Exception{
  if(relative==null||relative.length()>900||relative.startsWith("/")||relative.contains("\\")||relative.indexOf(0)>=0)throw new IOException("مسار غير صالح");
  String[] parts=relative.split("/",-1);for(String p:parts)if(p.isEmpty()||p.equals(".")||p.equals("..")||p.startsWith(".")||p.length()>180||p.indexOf(':')>=0)throw new IOException("اسم مجلد أو ملف غير صالح");
  File f=new File(root,relative);if(!f.getCanonicalPath().startsWith(root.getCanonicalPath()+"/"))throw new IOException("مسار غير صالح");return f;
 }
 static File staging(File root)throws Exception{File d=new File(root,".incoming");if(!d.isDirectory()&&!d.mkdirs())throw new IOException("تعذر إنشاء مجلد النقل");return d;}
 static File meta(File stage,String id)throws Exception{if(!id.matches("[a-f0-9-]{36}"))throw new IOException("جلسة غير صالحة");return new File(stage,id+".json");}
 static void write(File file,JSONObject j)throws Exception{android.util.AtomicFile f=new android.util.AtomicFile(file);FileOutputStream out=null;try{out=f.startWrite();out.write(j.toString().getBytes("UTF-8"));f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);throw e;}}
 static JSONObject read(File file)throws Exception{if(file.length()>8192)throw new IOException();return new JSONObject(new String(new android.util.AtomicFile(file).readFully(),"UTF-8"));}
 static synchronized JSONObject call(Context c,JSONObject b)throws Exception{
  File root=root(c);String op=b.optString("op");boolean managed=root.getAbsolutePath().contains("/Android/data/");
  if(op.equals("status"))return new JSONObject().put("writable",true).put("appManaged",managed).put("free",root.getUsableSpace());
  File stage=staging(root);
  if(op.equals("begin")){
   if(managed&&!b.optBoolean("ackAppFolder"))throw new IOException("أكد فهمك أن إزالة تطبيق مكتبتي قد تمسح ملفات مجلده الخاص على القرص");
   String path=b.getString("path");File dest=child(root,path);
   long size=b.getLong("size"),modified=b.optLong("modified");if(size<0||size>1000000000000L)throw new IOException("حجم غير صالح");
   if(dest.exists()){
    File[] receipts=stage.listFiles((d,n)->n.endsWith(".done"));if(receipts!=null)for(File receipt:receipts)try{JSONObject r=read(receipt);if(path.equals(r.optString("path"))&&size==r.optLong("size")&&modified==r.optLong("modified")&&dest.isFile()&&dest.length()==size&&dest.lastModified()==r.optLong("savedAt"))return new JSONObject().put("complete",true).put("offset",size);}catch(Exception ignored){}
    throw new IOException("الملف موجود؛ لن نستبدله");
   }
   File[] old=stage.listFiles((d,n)->n.endsWith(".json"));if(old!=null)for(File f:old)try{JSONObject m=read(f);File part=new File(stage,m.getString("id")+".part");if(path.equals(m.optString("path"))&&size==m.optLong("size")&&modified==m.optLong("modified")&&part.isFile()&&part.length()<=size)return new JSONObject().put("id",m.getString("id")).put("offset",part.length());}catch(Exception ignored){}
   if(size>root.getUsableSpace()-32L*1024*1024)throw new IOException("المساحة المتاحة لا تكفي");
   if(old!=null&&old.length>=20)throw new IOException("توجد عمليات نقل معلقة كثيرة؛ أكملها أو ألغها");
   String id=UUID.randomUUID().toString();File part=new File(stage,id+".part");if(!part.createNewFile())throw new IOException("تعذر بدء النقل");
   write(meta(stage,id),new JSONObject().put("id",id).put("path",path).put("size",size).put("modified",modified));return new JSONObject().put("id",id).put("offset",0);
  }
  String id=b.getString("id");File mf=meta(stage,id);JSONObject m=read(mf);File part=new File(stage,id+".part");
  if(op.equals("cancel")){if(part.exists()&&!part.delete())throw new IOException("تعذر حذف ملف النقل غير المكتمل");if(!mf.delete())throw new IOException("تعذر إنهاء جلسة النقل");return new JSONObject().put("cancelled",true);}
  if(op.equals("chunk")){
   long offset=b.getLong("offset");byte[] bytes=android.util.Base64.decode(b.getString("data"),android.util.Base64.DEFAULT);if(bytes.length==0||bytes.length>262144||offset<0||offset+bytes.length>m.getLong("size"))throw new IOException("جزء نقل غير صالح");
   CRC32 crc=new CRC32();crc.update(bytes);if(crc.getValue()!=b.getLong("crc"))throw new IOException("فشل تحقق الجزء المنقول");
   try(RandomAccessFile f=new RandomAccessFile(part,"rw")){if(f.length()!=offset)throw new IOException("تغير موضع النقل؛ أعد اختيار الملف لاستكماله");f.seek(offset);f.write(bytes);f.getFD().sync();}return new JSONObject().put("offset",part.length());
  }
  if(op.equals("finish")){
   File dest=child(root,m.getString("path"));if(!part.isFile()||part.length()!=m.getLong("size")||dest.exists())throw new IOException("النقل غير مكتمل أو الملف موجود");
   File parent=dest.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("تعذر إنشاء مجلد الفيلم");
   if(!part.renameTo(dest))throw new IOException("تعذر تثبيت الملف؛ أعد المحاولة");write(new File(stage,id+".done"),m.put("savedAt",dest.lastModified()));mf.delete();return new JSONObject().put("saved",true).put("bytes",dest.length());
  }
  throw new IOException("عملية غير معروفة");
 }
}

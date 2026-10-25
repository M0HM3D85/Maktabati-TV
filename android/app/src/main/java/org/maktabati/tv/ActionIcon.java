package org.maktabati.tv;
import android.graphics.*;import android.graphics.drawable.Drawable;
/** Crisp monochrome action symbols; no emoji or platform button artwork. */
final class ActionIcon extends Drawable {
 final String kind;final Paint p=new Paint(3);int color=Color.WHITE;
 ActionIcon(String text){kind=text;p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
 public boolean isStateful(){return true;}
 protected boolean onStateChange(int[] states){int c=Color.WHITE;for(int s:states)if(s==android.R.attr.state_focused)c=CinemaStyle.BG;if(c==color)return false;color=c;invalidateSelf();return true;}
 void line(Canvas c,float... v){for(int i=0;i+3<v.length;i+=2)c.drawLine(v[i],v[i+1],v[i+2],v[i+3],p);}
 void path(Canvas c,float... v){Path q=new Path();q.moveTo(v[0],v[1]);for(int i=2;i<v.length;i+=2)q.lineTo(v[i],v[i+1]);c.drawPath(q,p);}
 public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);
  if(kind.contains("تشغيل")||kind.contains("استكمال")){path(c,8,5,19,12,8,19,8,5);}
  else if(kind.contains("ترجم")){c.drawRoundRect(3,5,21,19,3,3,p);line(c,6,10,11,10);line(c,14,10,18,10);line(c,6,14,9,14);line(c,12,14,18,14);}
  else if(kind.contains("بحث")||kind.contains("اختبار")){c.drawCircle(10,10,6,p);line(c,15,15,21,21);}
  else if(kind.contains("مفضل")){Path q=new Path();q.moveTo(12,20);q.cubicTo(1,13,1,5,7,5);q.cubicTo(10,5,12,8,12,8);q.cubicTo(12,8,14,5,17,5);q.cubicTo(23,5,23,13,12,20);c.drawPath(q,p);}
  else if(kind.contains("حذف")){line(c,4,6,20,6);path(c,7,6,8,21,16,21,17,6);path(c,9,6,9,3,15,3,15,6);line(c,10,10,10,17);line(c,14,10,14,17);}
  else if(kind.contains("رجوع")||kind.contains("عودة")||kind.contains("تراجع")){path(c,10,5,17,12,10,19);line(c,4,12,17,12);}
  else if(kind.contains("إلغاء")||kind.contains("إغلاق")){line(c,6,6,18,18);line(c,18,6,6,18);}
  else if(kind.contains("لاحق")){path(c,6,3,18,3,18,21,12,17,6,21,6,3);}
  else if(kind.contains("مشاهد")||kind.contains("حفظ")||kind.contains("حسن")){path(c,4,12,9,17,20,6);}
  else if(kind.contains("إعداد")||kind.contains("رمز")){line(c,5,3,5,21);line(c,12,3,12,21);line(c,19,3,19,21);c.drawCircle(5,8,2,p);c.drawCircle(12,16,2,p);c.drawCircle(19,10,2,p);}
  else if(kind.contains("معلومات")||kind.contains("IMDb")||kind.contains("وصف")){c.drawCircle(12,12,9,p);line(c,12,11,12,17);c.drawPoint(12,7,p);}
  else if(kind.contains("ربط")){c.drawRoundRect(3,8,14,16,4,4,p);c.drawRoundRect(10,8,21,16,4,4,p);}
  else {c.drawRoundRect(3,4,21,20,2,2,p);line(c,8,4,8,20);line(c,12,9,17,9);line(c,12,14,17,14);}
  c.restore();}
 public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

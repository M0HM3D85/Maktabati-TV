package org.maktabati.tv;
import android.app.AlertDialog;import android.content.Context;import android.view.View;import android.widget.Button;
final class CinemaDialogs {
 static class Builder extends AlertDialog.Builder {
  Builder(Context c){super(c);}
  @Override public AlertDialog create(){AlertDialog d=super.create();d.getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewDetachedFromWindow(View v){}public void onViewAttachedToWindow(View v){v.post(()->{for(int id:new int[]{-1,-2,-3}){Button b=d.getButton(id);if(b!=null){CinemaStyle.action(b);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);android.view.ViewGroup.LayoutParams lp=b.getLayoutParams();lp.height=CinemaStyle.dp(b.getContext(),32);b.setLayoutParams(lp);}}});}});return d;}
 }
}

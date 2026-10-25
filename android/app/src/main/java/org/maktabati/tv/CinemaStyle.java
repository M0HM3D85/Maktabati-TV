package org.maktabati.tv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.TextView;

final class CinemaStyle {
    static final int BG=0xff090b10, MUTED=0xffacb4c2;
    private static Typeface regular,medium;
    static int dp(Context c,int n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
    static void font(TextView v,boolean bold){
        try {
            if(regular==null)regular=Typeface.createFromAsset(v.getContext().getAssets(),"Tajawal-Regular.ttf");
            if(medium==null)medium=Typeface.createFromAsset(v.getContext().getAssets(),"Tajawal-Medium.ttf");
            v.setTypeface(bold?medium:regular);
        }catch(RuntimeException ignored){v.setTypeface(null,bold?1:0);}
    }
    static GradientDrawable box(Context c,int color,int radius,int stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));
        if(stroke!=0)d.setStroke(dp(c,2),stroke);return d;
    }
    static void action(TextView v){
        Context c=v.getContext();font(v,true);
        StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},box(c,Color.WHITE,9,0));
        s.addState(new int[]{},box(c,0x402b303c,9,0));v.setBackground(s);
        v.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{BG,Color.WHITE}));
        v.setStateListAnimator(null);v.setElevation(0);v.setFocusable(true);
        v.setTextSize(13);v.setIncludeFontPadding(false);
        v.setPadding(dp(c,11),dp(c,3),dp(c,11),dp(c,3));
        icon(v);v.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int n,int after){}public void onTextChanged(CharSequence s,int a,int before,int n){}public void afterTextChanged(android.text.Editable e){icon(v);}});
    }
    private static void icon(TextView v){Context c=v.getContext();ActionIcon icon=new ActionIcon(v.getText().toString());int n=dp(c,16);icon.setBounds(0,0,n,n);icon.setState(v.getDrawableState());v.setCompoundDrawablePadding(dp(c,6));v.setCompoundDrawablesRelative(icon,null,null,null);}
}

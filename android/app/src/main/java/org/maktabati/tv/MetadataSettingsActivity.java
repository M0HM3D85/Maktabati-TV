package org.maktabati.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Simple private film-information connection; no credential is embedded or logged. */
public final class MetadataSettingsActivity extends Activity {
    private static final int BG=CinemaStyle.BG;
    private static final int FOCUS=Color.WHITE;
    private LocalMetadataStore metadata;
    private TextView status;
    private Button connect;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        metadata=new LocalMetadataStore(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.setBackgroundColor(BG);
        setContentView(scroll);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(46),dp(35),dp(46),dp(34));
        scroll.addView(root);
        addText(root,"إعدادات معلومات الأفلام",32,Color.WHITE);
        addText(root,"يمكنك إضافة الملخصات والبوسترات والتقييمات العربية إلى مكتبتك.",20,Color.rgb(210,225,223));
        status=addText(root,"",19,FOCUS);
        connect=button(root,"ربط خدمة المعلومات",()->enterToken());
        button(root,"اختبار الاتصال",()->testConnection());
        button(root,"إلغاء ربط الخدمة",()->{
            new CinemaDialogs.Builder(this)
                .setTitle("إلغاء الربط")
                .setMessage("سيُحذف رمز الاتصال من التلفزيون. تبقى معلومات الأفلام التي حفظتها سابقًا.")
                .setPositiveButton("إلغاء الربط",(d,w)->{
                    metadata.disconnect();updateState();
                })
                .setNegativeButton("تراجع",null).show();
        });
        button(root,"عودة إلى مكتبتي",this::finish);
        addText(root,"المعلومات مقدمة من TMDB (The Movie Database).",15,Color.rgb(168,191,193));
        addText(root,TmdbService.CREDIT,13,Color.rgb(150,175,176));
        addText(root,"خدمة TMDB اختيارية. لا تُرسل أسماء الأفلام إلى الخدمة إلا عندما تختار البحث عن معلومات فيلم.",15,Color.rgb(168,191,193));
        updateState();
        connect.requestFocus();
    }
    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private TextView addText(LinearLayout root,String content,int size,int color){
        TextView view=new TextView(this);
        CinemaStyle.font(view,false);view.setText(content);view.setTextSize(size);view.setTextColor(color);
        view.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        view.setPadding(dp(4),dp(8),dp(4),dp(8));
        root.addView(view,new LinearLayout.LayoutParams(-1,-2));
        return view;
    }
    private Button button(LinearLayout root,String title,Runnable action){
        Button b=new Button(this);
        b.setText(title);b.setTextSize(19);b.setAllCaps(false);
        b.setFocusable(true);
        b.setTextColor(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_focused},new int[]{}},
                new int[]{BG,Color.WHITE}));
        GradientDrawable normal=new GradientDrawable();
        normal.setColor(Color.rgb(25,48,58));
        normal.setCornerRadius(dp(14));
        normal.setStroke(dp(1),Color.rgb(77,113,122));
        GradientDrawable active=new GradientDrawable();
        active.setColor(FOCUS);active.setCornerRadius(dp(14));
        active.setStroke(dp(2),Color.WHITE);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},active);
        states.addState(new int[]{},normal);
        CinemaStyle.action(b);
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(290),dp(36));
        p.topMargin=dp(10);
        root.addView(b,p);
        return b;
    }
    private void updateState(){
        boolean connected=metadata.configured();
        status.setText(connected?
                "يوجد رمز محفوظ. اختبر الاتصال للتحقق من صلاحية الربط.":
                "لم تُربط خدمة المعلومات بعد. يمكنك مشاهدة أفلامك بدونها.");
        connect.setText(connected?"تغيير رمز الاتصال":"ربط خدمة المعلومات");
    }
    private void enterToken(){
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setHint("ألصق رمز القراءة الخاص بحساب TMDB");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(dp(18),dp(12),dp(18),dp(12));
        LinearLayout wrapper=new LinearLayout(this);
        wrapper.setPadding(dp(20),dp(12),dp(20),dp(5));
        wrapper.addView(input,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new CinemaDialogs.Builder(this)
            .setTitle("ربط معلومات الأفلام")
            .setMessage("احصل على رمز API Read Access Token من themoviedb.org/settings/api. يُحفظ الرمز مشفرًا على التلفزيون فقط، ولا يُضاف إلى التطبيق أو GitHub.")
            .setView(wrapper)
            .setPositiveButton("حفظ",null).setNegativeButton("إلغاء",(d,w)->input.setText("")).create();
        dialog.setOnShowListener(ignored->{
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{metadata.saveToken(input.getText().toString());input.setText("");updateState();dialog.dismiss();notice("حُفظ رمز الربط مشفرًا. اختر اختبار الاتصال للتحقق منه.");}
                catch(MetadataFailure.Credential e){input.setError(MetadataFailure.message(e));}
                catch(Exception e){input.setError("تعذر حفظ الرمز مشفرًا. لم نغيّر الربط السابق.");}
            });
        });dialog.show();
    }
    private void testConnection(){
        // This is a public test title, not a local filename, path or personal library item.
        new CinemaDialogs.Builder(this)
            .setTitle("اختبار الاتصال")
            .setMessage("سيختبر التطبيق الاتصال بخدمة TMDB باستخدام عنوان فيلم عام. لن يرسل أسماء أفلامك.")
            .setPositiveButton("اختبار",(d,w)->new Thread(()->{
                try{
                    if(!metadata.configured()){
                        TmdbService.probeNetwork();
                        diagnostic("HTTPS_OK_NO_TOKEN");
                        runOnUiThread(()->notice("اتصال HTTPS بخدمة TMDb يعمل على التلفزيون. لا يوجد رمز قراءة محفوظ؛ اربط الخدمة لإضافة معلومات الأفلام. [MISSING]"));
                    }else{
                        TmdbService.search(metadata.token(),false,"Star Wars");
                        diagnostic("AUTHENTICATED_SEARCH_OK");
                        runOnUiThread(()->notice("نجح الاتصال والبحث باستخدام الرمز المحفوظ. يمكنك جلب معلومات الأفلام."));
                    }
                }catch(Exception e){
                    diagnostic(MetadataFailure.code(e));
                    runOnUiThread(()->notice(MetadataFailure.message(e)));
                }
            },"metadata-check").start())
            .setNegativeButton("إلغاء",null).show();
    }
    private void notice(String text){
        if(isFinishing()||isDestroyed())return;
        new CinemaDialogs.Builder(this).setTitle("مكتبتي").setMessage(text)
            .setPositiveButton("حسنًا",null).show();
    }
    private void diagnostic(String code){
        // Fixed enum-like code only. Never persist tokens, headers, URLs or exception text here.
        getSharedPreferences("metadata_diagnostic_v1",MODE_PRIVATE).edit().putString("result",code).apply();
    }
}


package org.maktabati.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cinematic 10-foot Arabic RTL details screen. Fully usable offline.
 * No metadata network request unless the owner taps TMDb Search explicitly.
 */
public final class MediaDetailsActivity extends Activity {
    private static final int BG=CinemaStyle.BG;
    private static final int GOLD=Color.WHITE;
    private static final int LIGHT=Color.rgb(244,249,247);
    private static final int MUTED=Color.rgb(184,206,207);
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private LocalMetadataStore metadataStore;
    private ViewingStore viewingStore;
    private String mediaId;
    private String title="", filename="",seriesTitle="";
    private Uri video,poster;
    private Integer year;
    private boolean series;
    private ArrayList<String> subtitles, subtitleNames;
    private TmdbService.Detail detail;
    private ImageView posterImage,backdropImage;
    private TextView titleView,originalView,factsView,storyView,castView;
    private Button tmdbButton,imdbButton,playButton,subtitleButton;
    private Button favoriteButton,watchlistButton,watchedButton;
    private String currentPosterKey="";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        metadataStore=new LocalMetadataStore(this);
        viewingStore=new ViewingStore(this);
        Intent intent=getIntent();
        title=intent.getStringExtra("title");
        if(title==null)title="عمل غير معروف";
        seriesTitle=intent.getStringExtra("series_title");
        if(seriesTitle==null)seriesTitle="";
        filename=intent.getStringExtra("filename");
        if(filename==null)filename="";
        series=intent.getBooleanExtra("is_series",false);
        int y=intent.getIntExtra("year",-1);year=y>0?y:null;
        String mediaUri=intent.getStringExtra("video_uri");
        if(TextUtils.isEmpty(mediaUri)){finish();return;}
        video=Uri.parse(mediaUri);
        mediaId=MediaIdentity.fromUri(mediaUri);
        String posterString=intent.getStringExtra("poster_uri");
        poster=TextUtils.isEmpty(posterString)?null:Uri.parse(posterString);
        subtitles=intent.getStringArrayListExtra("subtitle_uris");
        subtitleNames=intent.getStringArrayListExtra("subtitle_names");
        if(subtitles==null)subtitles=new ArrayList<>();
        if(subtitleNames==null)subtitleNames=new ArrayList<>();
        String name=series&&!seriesTitle.isEmpty()?seriesTitle:title;
        detail=metadataStore.load(name,year,series);
        createScreen();
        renderMetadata();
        if(poster!=null)loadImage(poster,posterImage,"poster_local");
        else if(detail!=null)loadRemotePoster();
        if(detail!=null)loadRemoteBackdrop();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int sp,int color,boolean bold){
        TextView v=new TextView(this);
        v.setText(value);v.setTextSize(sp);v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        CinemaStyle.font(v,bold);v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        return v;
    }
    private GradientDrawable box(int color,int radius,int stroke){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(2),stroke);
        return d;
    }
    private Button action(String value,boolean primary,Runnable click){
        Button b=new Button(this);b.setAllCaps(false);b.setText(value);b.setTextSize(15);
        CinemaStyle.action(b);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setOnClickListener(v->click.run());return b;
    }
    private void addAction(LinearLayout group,Button button){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(32));p.setMarginEnd(dp(9));group.addView(button,p);
    }
    private LinearLayout actionRow(LinearLayout parent){
        LinearLayout row=new LinearLayout(this);row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.setPadding(0,dp(7),0,dp(7));parent.addView(row);return row;
    }
    private void createScreen(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);setContentView(scroll);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);root.setPadding(dp(36),dp(16),dp(36),dp(20));scroll.addView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.library_mark);header.addView(mark,new LinearLayout.LayoutParams(dp(28),dp(28)));
        TextView brand=text("  مكتبتي",24,LIGHT,true);header.addView(brand,new LinearLayout.LayoutParams(0,dp(42),1));
        Button back=action("رجوع",false,this::finish);header.addView(back,new LinearLayout.LayoutParams(dp(85),dp(36)));root.addView(header);
        FrameLayout hero=new FrameLayout(this);root.addView(hero,new LinearLayout.LayoutParams(-1,dp(280)));
        backdropImage=new ImageView(this);backdropImage.setScaleType(ImageView.ScaleType.CENTER_CROP);hero.addView(backdropImage,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0x33090b10,0xdd090b10,0xff090b10}));hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        View fade=new View(this);fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00090b10,0x11090b10,0xff090b10}));hero.addView(fade,new FrameLayout.LayoutParams(-1,-1));
        posterImage=new ImageView(this);posterImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(140),dp(210),Gravity.LEFT|Gravity.CENTER_VERTICAL);pp.leftMargin=dp(25);hero.addView(posterImage,pp);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(Gravity.RIGHT);info.setPadding(0,dp(15),0,0);
        hero.addView(info,new FrameLayout.LayoutParams(dp(570),-1,Gravity.RIGHT));
        TextView eyebrow=text(series?"من مسلسلاتك":"من مجموعة أفلامك",14,MUTED,false);info.addView(eyebrow,new LinearLayout.LayoutParams(-1,dp(26)));
        titleView=text(title,34,LIGHT,true);titleView.setMaxLines(2);titleView.setEllipsize(TextUtils.TruncateAt.END);info.addView(titleView,new LinearLayout.LayoutParams(-1,dp(76)));
        originalView=text("",14,MUTED,false);originalView.setSingleLine(true);info.addView(originalView,new LinearLayout.LayoutParams(-1,dp(24)));
        factsView=text("",15,0xffd8deea,true);factsView.setMaxLines(2);info.addView(factsView,new LinearLayout.LayoutParams(-1,dp(42)));
        storyView=text("",17,LIGHT,false);storyView.setMaxLines(4);storyView.setLineSpacing(dp(3),1.15f);storyView.setEllipsize(TextUtils.TruncateAt.END);storyView.setGravity(Gravity.RIGHT|Gravity.TOP);
        info.addView(storyView,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=actionRow(root);
        playButton=action("تشغيل",true,()->launchVideo(-1));
        subtitleButton=action("الترجمات",false,this::chooseSubtitle);
        tmdbButton=action("جلب المعلومات",false,this::startMetadataLookup);
        imdbButton=action("IMDb",false,this::openImdb);
        addAction(actions,playButton);addAction(actions,subtitleButton);addAction(actions,tmdbButton);addAction(actions,imdbButton);
        LinearLayout personal=actionRow(root);
        favoriteButton=action("أضف للمفضلة",false,()->toggleState(ViewingStore.FAVORITE));
        watchlistButton=action("شاهد لاحقًا",false,()->toggleState(ViewingStore.WATCHLIST));
        watchedButton=action("تمت المشاهدة",false,()->toggleState(ViewingStore.WATCHED));
        addAction(personal,favoriteButton);addAction(personal,watchlistButton);addAction(personal,watchedButton);
        castView=text("",16,MUTED,false);castView.setPadding(0,dp(14),0,dp(14));root.addView(castView);
        LinearLayout settings=actionRow(root);addAction(settings,action("إعدادات معلومات الأفلام",false,this::configureTmdb));
        addAction(settings,action("حذف مجلد الفيلم",false,this::requestDeleteMovie));
        addAction(settings,action("الوصف الأصلي",false,()->{if(detail!=null)new CinemaDialogs.Builder(this).setTitle("الوصف الأصلي من TMDb").setMessage(detail.overview).setPositiveButton("إغلاق",null).show();}));
        refreshStateButtons();playButton.requestFocus();
    }

    private void requestDeleteMovie(){
        if(series){new CinemaDialogs.Builder(this).setMessage("حذف مجلدات المسلسلات غير متاح لحماية بقية الحلقات.").setPositiveButton("حسنًا",null).show();return;}
        if(checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            new CinemaDialogs.Builder(this).setTitle("صلاحية الكتابة على القرص").setMessage("امنح مكتبتي صلاحية التخزين العادية. بعدها نتحقق من قابلية حذف مجلد هذا الفيلم قبل عرض التأكيد النهائي.")
                .setNegativeButton("إلغاء",null).setPositiveButton("منح الصلاحية",(d,w)->requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},782)).show();return;
        }
        work.execute(()->{try{
            if(!ReadOnlyMediaProvider.AUTHORITY.equals(video.getAuthority()))throw new java.io.IOException("الحذف متاح للمكتبة المقروءة مباشرة من USB فقط");
            java.util.List<String> parts=video.getPathSegments();java.io.File root=null;
            for(UsbVolumes.Volume v:UsbVolumes.removable(this))if(parts.size()>1&&v.uuid.equals(parts.get(0)))root=v.root;
            if(root==null)throw new java.io.IOException("القرص غير متصل");
            java.io.File file=root;for(int i=1;i<parts.size();i++)file=new java.io.File(file,parts.get(i));
            final MovieDeletion.Plan plan=MovieDeletion.preview(root,file);
            runOnUiThread(()->{if(isFinishing())return;android.app.AlertDialog dialog=new CinemaDialogs.Builder(this).setTitle("حذف نهائي لمجلد الفيلم")
                .setMessage(plan.folder.getName()+"\n"+plan.count+" ملف · "+String.format(java.util.Locale.US,"%.1f",plan.bytes/1048576.0)+" MB\n\nسيُحذف الفيديو والترجمات وجميع ملفات هذا المجلد نهائيًا. لا يمكن التراجع.")
                .setNegativeButton("إلغاء",null).setPositiveButton("حذف المجلد نهائيًا",(d,w)->work.execute(()->{try{MovieDeletion.execute(plan);new java.io.File(getFilesDir(),"library-index-v1.json").delete();getSharedPreferences("library_changes",0).edit().putBoolean("refresh",true).apply();runOnUiThread(()->{android.widget.Toast.makeText(this,"تم حذف مجلد الفيلم",android.widget.Toast.LENGTH_LONG).show();finish();});}catch(Exception e){deleteError(e.getMessage());}})).create();dialog.show();dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).requestFocus();});
        }catch(Exception e){deleteError(e.getMessage());}});
    }
    private void deleteError(String message){runOnUiThread(()->{if(!isFinishing())new CinemaDialogs.Builder(this).setTitle("لم يكتمل الحذف").setMessage(message).setPositiveButton("حسنًا",null).show();});}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==782&&results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)requestDeleteMovie();}

    private void renderMetadata(){
        String heading=series&&!seriesTitle.isEmpty()?seriesTitle:title;
        titleView.setText(detail!=null && !detail.title.isEmpty()?detail.title:MediaDisplay.title(heading));
        originalView.setText(detail!=null && !detail.original.isEmpty() &&
                !detail.original.equalsIgnoreCase(detail.title)?detail.original:"");
        String facts=year==null?"":year.toString();
        if(detail!=null){
            if(detail.runtime>0)facts+=(facts.isEmpty()?"":"   ·   ")+detail.runtime+" دقيقة";
            if(!detail.genres.isEmpty())facts+=(facts.isEmpty()?"":"   ·   ")+detail.genres;
            if(detail.votes>0)facts+="\n★ "+
                    String.format(Locale.US,"%.1f",detail.score)+"/10  TMDb";
        }
        factsView.setText(facts+PlaybackProgress.label(this,mediaId));
        if(detail!=null){
            storyView.setText(ArabicOverview.text(this,detail));
            String crew=(detail.director.isEmpty()?"":"الإخراج / التأليف: "+detail.director+"\n")+
                    (detail.cast.isEmpty()?"":"البطولة: "+detail.cast);
            castView.setText(crew.isEmpty()?"":crew);
        }else{
            storyView.setText("أضف قصة الفيلم وصوره وتقييمه من «جلب المعلومات» متى أردت.");
            castView.setText("تظهر أسماء الممثلين وصنّاع العمل عند جلب تفاصيل الفيلم.");
        }
        imdbButton.setVisibility(detail!=null && detail.imdbId.matches("tt[0-9]{5,12}")?View.VISIBLE:View.GONE);
        tmdbButton.setText(detail==null?"جلب المعلومات":"تعديل معلومات الفيلم");
    }
    private String displayTitle(){
        String heading=series&&!seriesTitle.isEmpty()?seriesTitle:title;
        return detail!=null&&!detail.title.isEmpty()?detail.title:heading;
    }
    private void toggleState(int flag){
        try {
            ViewingStore.State s=viewingStore.state(mediaId);
            boolean current=flag==ViewingStore.FAVORITE?s.favorite:
                    flag==ViewingStore.WATCHLIST?s.watchlist:s.watched;
            viewingStore.setFlag(mediaId,displayTitle(),flag,!current);
            refreshStateButtons();
        }catch(RuntimeException e){
            message("تعذر حفظ التغيير محليًا. لم تتغير الملفات الموجودة على USB.");
        }
    }
    private void refreshStateButtons(){
        ViewingStore.State s=viewingStore.state(mediaId);
        playButton.setText(s.lastOpened>0?"استكمال في VLC":"تشغيل");
        favoriteButton.setText(s.favorite?"ضمن المفضلة":"أضف للمفضلة");
        watchlistButton.setText(s.watchlist?"محفوظ للمشاهدة لاحقًا":"شاهد لاحقًا");
        watchedButton.setText(s.watched?"شاهدته":"تحديد تمت المشاهدة");
    }
    private void configureTmdb(){
        startActivity(new Intent(this,MetadataSettingsActivity.class));
    }
    private void startMetadataLookup(){
        if(!metadataStore.configured()){
            configureTmdb();return;
        }
        String initial=MediaDisplay.title(series&&!seriesTitle.isEmpty()?seriesTitle:title);
        EditText searchBox=new EditText(this);
        searchBox.setSingleLine(true);searchBox.setText(initial);
        searchBox.setSelectAllOnFocus(true);
        LinearLayout wrap=new LinearLayout(this);
        wrap.setPadding(dp(18),dp(5),dp(18),dp(5));wrap.addView(searchBox);
        new CinemaDialogs.Builder(this)
            .setTitle("البحث عن معلومات "+(series?"المسلسل":"الفيلم"))
            .setMessage("سيرسل هذا العنوان فقط إلى TMDb عبر اتصال مشفر. "+
                    "اختر العمل الصحيح من النتائج قبل حفظ المعلومات.")
            .setView(wrap)
            .setPositiveButton("بحث",(dialog,which)->searchTmdb(searchBox.getText().toString().trim()))
            .setNegativeButton("إلغاء",null).show();
    }
    private void searchTmdb(String query){
        if(query.isEmpty()){message("أدخل اسم الفيلم أو المسلسل.");return;}
        tmdbButton.setEnabled(false);tmdbButton.setText("جارٍ البحث…");
        work.execute(()->{
            try{
                String token=metadataStore.token();
                List<TmdbService.Candidate> candidates=TmdbService.search(token,series,query);
                runOnUiThread(()->{
                    tmdbButton.setEnabled(true);renderMetadata();
                    if(candidates.isEmpty()){message("لا توجد نتائج. جرّب الاسم الإنجليزي أو سنة مختلفة.");return;}
                    String[] labels=new String[candidates.size()];
                    for(int i=0;i<labels.length;i++)labels[i]=candidates.get(i).label();
                    new CinemaDialogs.Builder(this)
                            .setTitle("اختر الفيلم أو المسلسل المطابق")
                            .setItems(labels,(d,index)->fetchDetails(candidates.get(index)))
                            .setNegativeButton("إلغاء",null).show();
                });
            }catch(Exception e){runOnUiThread(()->{
                tmdbButton.setEnabled(true);renderMetadata();
                message("تعذر البحث في TMDb: "+safeMessage(e));
            });}
        });
    }
    private void fetchDetails(TmdbService.Candidate selected){
        tmdbButton.setEnabled(false);tmdbButton.setText("جارٍ جلب التفاصيل…");
        work.execute(()->{
            try{
                TmdbService.Detail fetched=TmdbService.details(metadataStore.token(),series,selected.id);
                String key=series&&!seriesTitle.isEmpty()?seriesTitle:title;
                metadataStore.save(key,year,series,fetched);
                runOnUiThread(()->{
                    detail=fetched;renderMetadata();
                    if(poster==null)loadRemotePoster();
                    loadRemoteBackdrop();
                    message("تمت إضافة معلومات الفيلم وصوره بنجاح.");
                });
            }catch(Exception e){runOnUiThread(()->{renderMetadata();message("تعذر جلب التفاصيل: "+safeMessage(e));});}
        });
    }
    private static String safeMessage(Exception e){
        return MetadataFailure.message(e);
    }
    private void openImdb(){
        if(detail==null||!detail.imdbId.matches("tt[0-9]{5,12}")){message("لا يتوفر رابط IMDb لهذا العمل.");return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.imdb.com/title/"+detail.imdbId+"/")));}
        catch(ActivityNotFoundException e){message("لا يوجد متصفح ويب على التلفزيون. يمكنك فتح IMDb من الهاتف لاحقًا.");}
    }
    private void chooseSubtitle(){
        if(subtitles.isEmpty()){message("لا توجد ترجمة خارجية لهذا العمل.");return;}
        String[] names=new String[subtitles.size()+2];
        names[0]="تلقائي — الترجمة الأولى المطابقة";
        names[1]="بدون ترجمة خارجية";
        for(int i=0;i<subtitles.size();i++)names[i+2]=
                i<subtitleNames.size()?subtitleNames.get(i):"ترجمة "+(i+1);
        new CinemaDialogs.Builder(this).setTitle("اختيار الترجمة")
            .setItems(names,(d,index)->launchVideo(index==0?0:index==1?-1:index-2))
            .setNegativeButton("إلغاء",null).show();
    }
    private void launchVideo(int subtitleIndex){
        Intent i=new Intent(Intent.ACTION_VIEW);
        i.setPackage("org.videolan.vlc");
        i.setDataAndType(video,filename.toLowerCase(Locale.ROOT).endsWith(".mkv")?
                "video/x-matroska":"video/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        String displayTitle=detail!=null && !detail.title.isEmpty()?detail.title:title;
        i.putExtra("title",displayTitle);
        ClipData grants=ClipData.newUri(getContentResolver(),"Local video",video);
        if(subtitleIndex>=0 && subtitleIndex<subtitles.size()){
            Uri sub=Uri.parse(subtitles.get(subtitleIndex));
            i.putExtra("subtitles_location",sub.toString());
            grants.addItem(new ClipData.Item(sub));
        }
        i.setClipData(grants);
        try{
            PlaybackProgress.prepare(this,i,mediaId);startActivityForResult(i,PlaybackProgress.REQUEST);
            viewingStore.recordLaunch(mediaId,displayTitle());
        }catch(ActivityNotFoundException e){
            i.setPackage(null);
            try{
                startActivity(Intent.createChooser(i,"اختر مشغل الفيديو"));
                viewingStore.recordLaunch(mediaId,displayTitle());
            }
            catch(ActivityNotFoundException err){message("لم نجد مشغل فيديو.");}
        }catch(SecurityException ex){message("لا توجد صلاحية لفتح هذا الملف في VLC.");}
    }
    private void loadRemotePoster(){
        if(detail!=null){
            Uri uri=TmdbService.image(detail.poster,"w500");
            if(uri!=null)loadImage(uri,posterImage,"poster_tmdb");
        }
    }
    private void loadRemoteBackdrop(){
        if(detail!=null && metadataStore.configured()){
            Uri uri=TmdbService.image(detail.backdrop,"w780");
            if(uri!=null)loadImage(uri,backdropImage,"backdrop_tmdb");
        }
    }
    private void loadImage(Uri uri,ImageView target,String purpose){
        if(uri==null)return;
        String key=purpose+uri.toString();
        target.setTag(key);currentPosterKey=key;
        work.execute(()->{
            Bitmap loaded=null;
            try{
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inJustDecodeBounds=true;
                try(InputStream first=openImageInput(uri)){
                    BitmapFactory.decodeStream(first,null,options);
                }
                if(options.outWidth<=0 || options.outHeight<=0)return;
                int maxWidth="backdrop_tmdb".equals(purpose)?900:460;
                int maxHeight="backdrop_tmdb".equals(purpose)?550:650;
                int sample=1;
                while(options.outWidth/sample>maxWidth*2||options.outHeight/sample>maxHeight*2)sample*=2;
                options.inJustDecodeBounds=false;options.inSampleSize=sample;
                options.inPreferredConfig=Bitmap.Config.RGB_565;
                try(InputStream in=openImageInput(uri)){loaded=BitmapFactory.decodeStream(in,null,options);}
            }catch(Exception ignored){}
            if(loaded==null && "content".equals(uri.getScheme())){
                try{
                    loaded=ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(getContentResolver(),uri),
                        (decoder,info,src)->{
                            int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                            if(w>0&&h>0){
                                float k=Math.min(1.0f,Math.min(460f/w,650f/h));
                                decoder.setTargetSize(Math.max(1,Math.round(w*k)),
                                                      Math.max(1,Math.round(h*k)));
                            }
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        });
                }catch(Exception ignored){}
            }
            if(loaded!=null){
                final Bitmap b=loaded;runOnUiThread(()->{
                    if(!isFinishing() && key.equals(target.getTag()))target.setImageBitmap(b);
                });
            }
        });
    }
    private InputStream openImageInput(Uri uri)throws Exception{
        if("content".equals(uri.getScheme()))return getContentResolver().openInputStream(uri);
        if(!"https".equals(uri.getScheme()) ||
                !"image.tmdb.org".equals(uri.getHost()) ||
                !metadataStore.configured())throw new SecurityException("Images not permitted");
        HttpURLConnection c=(HttpURLConnection)new URL(uri.toString()).openConnection();
        c.setConnectTimeout(7000);c.setReadTimeout(9000);
        c.setInstanceFollowRedirects(false);
        if(c.getResponseCode()!=200){c.disconnect();throw new java.io.IOException("Invalid image");}
        int max=4*1024*1024;
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=c.getInputStream()){
            byte[] bytes=new byte[8192];int read;
            while((read=in.read(bytes))>=0){
                if(out.size()+read>max)throw new java.io.IOException("Too large image");
                out.write(bytes,0,read);
            }
        }finally{c.disconnect();}
        return new java.io.ByteArrayInputStream(out.toByteArray());
    }
    private void message(String v){
        new CinemaDialogs.Builder(this).setTitle("مكتبتي").setMessage(v)
                .setPositiveButton("حسنًا",null).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==PlaybackProgress.REQUEST){PlaybackProgress.accept(this,mediaId,result,data);renderMetadata();}}
    @Override public void onBackPressed(){super.onBackPressed();}
    @Override protected void onDestroy(){
        work.shutdownNow();
        if(viewingStore!=null)viewingStore.close();
        super.onDestroy();
    }
}

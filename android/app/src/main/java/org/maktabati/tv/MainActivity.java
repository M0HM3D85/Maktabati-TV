package org.maktabati.tv;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android TV 9: standalone app; NEVER claims HOME and NEVER changes TV settings or USB files. */
public final class MainActivity extends Activity {
    private static final int PICK_FOLDER = 12;
    private static final int READ_USB_PERMISSION = 15;
    private String playbackId;
    private boolean nextScanAll = true;
    private String localQuery="";
    private int selectedYear=-1,sortOrder=0;
    private boolean directUsbSelected = false;
    private static final int BACKGROUND = CinemaStyle.BG;
    private static final int ACCENT = Color.WHITE;
    private static final String PREF_TREE = "document_tree";
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final ExecutorService pictures = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> bitmaps = new LruCache<String,Bitmap>(8*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap value) { return value.getByteCount(); }
    };
    private LinearLayout layout,toolbar;
    private TextView summary,headline,clock;
    private CinemaRows shelves;
    private Object selectedEntry;
    private TextView heroAction;
    private Runnable pendingHero;
    private ImageView previewPoster;
    private TextView previewTitle,previewMeta,previewText;
    private LocalMetadataStore previewCache;
    private ViewingStore viewingStore;
    private LinearLayout collections;
    private DocumentTreeScanner.Result library = new DocumentTreeScanner.Result();
    private DocumentTreeScanner.Show openShow;
    private String screen = "movies";
    private Uri selectedTree;
    private boolean scanning = false;
    private volatile boolean fetchingInfo = false;
    private volatile boolean cancelInfo = false;
    private final Locale sa=Locale.forLanguageTag("ar-SA");
    private final Runnable clockTick=new Runnable() {
        @Override public void run() {
            if (clock!=null) clock.setText(new SimpleDateFormat("h:mm a",sa).format(new Date()));
            ui.postDelayed(this,60000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        previewCache=new LocalMetadataStore(this);
        viewingStore=new ViewingStore(this);
        buildUi();
        selectedTree=loadTree();
        ui.post(clockTick);
        nextScanAll=true;
        sortOrder=getPreferences(MODE_PRIVATE).getInt("library_sort",0);
        directUsbSelected=getPreferences(MODE_PRIVATE).getBoolean("direct_usb",false);
        summary.setText("جارٍ فتح الفهرس المحفوظ...");
        work.execute(()->{DocumentTreeScanner.Result cached=LibraryIndex.load(this);
            if(cached!=null){for(DocumentTreeScanner.Media m:cached.movies){MediaDisplay.title(m.title);previewCache.load(m.title,m.year,false);}for(DocumentTreeScanner.Show sh:cached.shows)MediaDisplay.title(sh.title);}
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(cached!=null){library=cached;renderMovies();summary.setText("الفهرس المحفوظ · تحديث القرص في الخلفية");}if(selectedTree!=null||directUsbSelected||checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED)scanTree();else renderEmpty("أفلامك في مكان واحد. اختر «إضافة المكتبة» لتبدأ.");});});
        if(state!=null)playbackId=state.getString("playbackId");

    }
    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density*n); }

    private void buildUi() {
        getWindow().getDecorView().setSystemUiVisibility(5894);
        android.widget.FrameLayout scene=new android.widget.FrameLayout(this);
        scene.setBackgroundColor(BACKGROUND);setContentView(scene);
        previewPoster=new ImageView(this);previewPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        scene.addView(previewPoster,new android.widget.FrameLayout.LayoutParams(-1,dp(335)));
        View shade=new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x11090b10,0xdd090b10,0xff090b10}));
        scene.addView(shade,new android.widget.FrameLayout.LayoutParams(-1,dp(340)));
        View fade=new View(this);fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00090b10,0x22090b10,0xff090b10}));
        scene.addView(fade,new android.widget.FrameLayout.LayoutParams(-1,dp(355)));
        layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);layout.setPadding(dp(36),dp(16),dp(36),dp(8));
        scene.addView(layout,new android.widget.FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        layout.addView(header,new LinearLayout.LayoutParams(-1,dp(44)));
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.library_mark);
        header.addView(mark,new LinearLayout.LayoutParams(dp(30),dp(30)));
        headline=makeText("مكتبتي",24,Color.WHITE);CinemaStyle.font(headline,true);
        LinearLayout.LayoutParams brand=new LinearLayout.LayoutParams(dp(92),-1);brand.setMarginStart(dp(10));header.addView(headline,brand);
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        header.addView(toolbar,new LinearLayout.LayoutParams(0,-1,1));
        addButton("اكتشف",v->renderMovies());addButton("المسلسلات",v->renderShows());
        addButton("المفضلة",v->renderPersonal("favorites"));addButton("لاحقًا",v->renderPersonal("watchlist"));
        addButton("بحث وتصفية",v->queryMenu());addButton("المكتبة",v->libraryMenu());
        clock=makeText("",15,CinemaStyle.MUTED);header.addView(clock);
        android.widget.FrameLayout hero=new android.widget.FrameLayout(this);
        layout.addView(hero,new LinearLayout.LayoutParams(-1,dp(208)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.RIGHT);info.setPadding(0,dp(18),0,0);
        android.widget.FrameLayout.LayoutParams inf=new android.widget.FrameLayout.LayoutParams(dp(430),-1,Gravity.RIGHT);hero.addView(info,inf);
        previewMeta=makeText("مجموعة أفلامك ومسلسلاتك",13,CinemaStyle.MUTED);info.addView(previewMeta,new LinearLayout.LayoutParams(-1,dp(23)));
        previewTitle=makeText("قصتك التالية تبدأ هنا",34,Color.WHITE);CinemaStyle.font(previewTitle,true);previewTitle.setMaxLines(2);
        info.addView(previewTitle,new LinearLayout.LayoutParams(-1,dp(70)));
        previewText=makeText("أضف مكتبتك لتستعرض أعمالك وتختار ما تشاهده.",16,0xffd0d4dc);
        previewText.setLineSpacing(dp(3),1.15f);previewText.setMaxLines(2);previewText.setEllipsize(TextUtils.TruncateAt.END);info.addView(previewText,new LinearLayout.LayoutParams(-1,dp(52)));
        heroAction=makeText("إضافة المكتبة",16,Color.WHITE);heroAction.setGravity(Gravity.CENTER);
        CinemaStyle.action(heroAction);heroAction.setOnClickListener(v->{
            if(selectedEntry instanceof DocumentTreeScanner.Media){DocumentTreeScanner.Media m=(DocumentTreeScanner.Media)selectedEntry;launchVideo(m,true,autoSubtitle(m));}
            else if(selectedEntry instanceof DocumentTreeScanner.Show)renderEpisodes((DocumentTreeScanner.Show)selectedEntry);
            else if(!localQuery.isEmpty()||selectedYear>0){localQuery="";selectedYear=-1;refreshSection();}
            else chooseLibrary();
        });info.addView(heroAction,new LinearLayout.LayoutParams(dp(144),dp(32)));
        summary=makeText("أفلام ومسلسلات من مجموعتك الخاصة",12,CinemaStyle.MUTED);
        layout.addView(summary,new LinearLayout.LayoutParams(-1,dp(22)));
        shelves=new CinemaRows(this,new CinemaRows.Callback(){
            public String title(Object o){return entryTitle(o);}
            public void picture(ImageView im,Object o){loadPoster(im,entryPoster(o,false));}
            public void selected(Object o){selectedEntry=o;if(pendingHero!=null)ui.removeCallbacks(pendingHero);pendingHero=()->previewEntry(o);ui.postDelayed(pendingHero,220);}
            public void open(Object o){selectedEntry=o;if(o instanceof DocumentTreeScanner.Show)renderEpisodes((DocumentTreeScanner.Show)o);else showDetails((DocumentTreeScanner.Media)o);}
        });layout.addView(shelves,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void libraryMenu(){
        new CinemaDialogs.Builder(this).setTitle("مكتبتي").setItems(new String[]{"إضافة المكتبة","تحديث المكتبة","آخر ما فتحته","تمت مشاهدته","جلب المعلومات","إعدادات المعلومات","منح الوصول للقرص للحذف والنقل"},(d,n)->{
            switch(n){case 0:chooseLibrary();break;case 1:scanTree();break;case 2:renderPersonal("recent");break;case 3:renderPersonal("watched");break;case 4:fetchLibraryDetails();break;case 5:startActivity(new Intent(this,MetadataSettingsActivity.class));break;case 6:UsbAccess.request(this);break;}
        }).setNegativeButton("إغلاق",null).show();
    }
    private String entryTitle(Object o){
        if(o instanceof DocumentTreeScanner.Show)return MediaDisplay.title(((DocumentTreeScanner.Show)o).title);
        DocumentTreeScanner.Media m=(DocumentTreeScanner.Media)o;TmdbService.Detail d=previewCache.load(m.series?m.seriesName:m.title,m.year,m.series);
        String name=d!=null&&!d.title.isEmpty()?d.title:MediaDisplay.title(m.title);
        return m.series&&m.episode>=0?name+" · الحلقة "+m.episode:name;
    }
    private Uri entryPoster(Object o,boolean hero){
        if(o instanceof DocumentTreeScanner.Show)return ((DocumentTreeScanner.Show)o).posterUri;
        DocumentTreeScanner.Media m=(DocumentTreeScanner.Media)o;
        if(m.posterUri!=null)return m.posterUri;
        TmdbService.Detail d=previewCache.load(m.series?m.seriesName:m.title,m.year,m.series);
        if(d==null||!previewCache.configured())return null;
        return TmdbService.image(hero&&!d.backdrop.isEmpty()?d.backdrop:d.poster,hero?"w780":"w342");
    }
    private TextView makeText(String text,int sp,int color) {
        TextView label=new TextView(this);
        label.setText(text);label.setTextSize(sp);label.setTextColor(color);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setSingleLine(false);CinemaStyle.font(label,false);label.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        return label;
    }
    private void addButton(String label,View.OnClickListener action){
        TextView b=makeText(label,15,Color.WHITE);b.setGravity(Gravity.CENTER);CinemaStyle.action(b);
        b.setOnClickListener(action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(30));p.setMarginEnd(dp(6));toolbar.addView(b,p);
    }
    private void fetchLibraryDetails(){
        if(fetchingInfo){
            cancelInfo=true;
            summary.setText("سيُوقف جلب المعلومات بعد الفيلم الحالي.");
            return;
        }
        if(library.movies.isEmpty()&&library.shows.isEmpty()){
            showMessage("أضف أفلامك إلى المكتبة أولًا.");
            return;
        }
        if(!previewCache.configured()){
            new CinemaDialogs.Builder(this)
                .setTitle("إضافة معلومات الأفلام")
                .setMessage("اربط خدمة معلومات الأفلام من الإعدادات أولًا، ثم ارجع لجلب الصور والملخصات.")
                .setPositiveButton("فتح الإعدادات",(d,w)->startActivity(
                        new Intent(this,MetadataSettingsActivity.class)))
                .setNegativeButton("إلغاء",null).show();
            return;
        }
        new CinemaDialogs.Builder(this)
            .setTitle("إضافة صور وقصص أفلامك")
            .setMessage("هل تريد البحث عن معلومات جميع الأفلام والمسلسلات في خدمة TMDB؟ سيُرسل عنوان كل عمل للبحث فقط، ولن يُرسل مسار الملفات أو محتويات USB. نحفظ نتيجة التطابق الواضح فقط؛ ويمكنك تصحيح بقية الأفلام بنفسك من تفاصيلها.\n\nاضغط الزر مرة ثانية لإيقاف البحث.")
            .setPositiveButton("جلب المعلومات",(dialog,which)->runInformationFetch())
            .setNegativeButton("إلغاء",null).show();
    }
    private void runInformationFetch(){
        final java.util.LinkedHashMap<String,DocumentTreeScanner.Media> unique=new java.util.LinkedHashMap<>();
        for(DocumentTreeScanner.Media m:library.movies)unique.put("movie:"+m.title+":"+m.year,m);
        for(DocumentTreeScanner.Show show:library.shows)for(DocumentTreeScanner.Media m:show.episodes)unique.put("tv:"+m.seriesName+":"+m.year,m);
        final List<DocumentTreeScanner.Media> movies=new ArrayList<>(unique.values());
        fetchingInfo=true;
        cancelInfo=false;
        summary.setText("جارٍ إضافة معلومات الأفلام...");
        work.execute(()->{
            int saved=0,needsReview=0,already=0;
            String error=null;
            try{
                String token=previewCache.token();
                if(token==null)throw new IllegalStateException("Missing connection");
                for(int i=0;i<movies.size() && !cancelInfo;i++){
                    DocumentTreeScanner.Media movie=movies.get(i);
                    String metadataTitle=movie.series?movie.seriesName:movie.title;
                    if(previewCache.load(metadataTitle,movie.year,movie.series)!=null){
                        already++;continue;
                    }
                    try{
                        String searchTitle=MediaDisplay.title(metadataTitle);
                        List<TmdbService.Candidate> matches=TmdbService.search(token,movie.series,searchTitle);
                        TmdbService.Candidate selected=MetadataMatcher.unambiguous(matches,searchTitle,movie.year);
                        if(selected==null){
                            needsReview++;
                        }else{
                            TmdbService.Detail data=TmdbService.details(token,movie.series,selected.id);
                            previewCache.save(metadataTitle,movie.year,movie.series,data);
                            saved++;
                        }
                        final int progress=i+1;
                        ui.post(()->summary.setText("جلب معلومات المكتبة  "+progress+" / "+movies.size()));
                        Thread.sleep(420);
                    }catch(java.io.IOException ex){
                        error=MetadataFailure.message(ex)+" · النتائج السابقة محفوظة؛ أعد جلب المعلومات لاستكمال الباقي.";
                        break;
                    }catch(InterruptedException ex){
                        Thread.currentThread().interrupt();
                        error="توقف التحديث.";
                        break;
                    }catch(Exception ex){
                        needsReview++;
                    }
                }
            }catch(Exception e){
                error="تعذر قراءة إعداد الاتصال. افتح الإعدادات وأعد ربط خدمة معلومات الأفلام.";
            }
            final int good=saved,review=needsReview,existing=already;
            final boolean stopped=cancelInfo;
            final String problem=error;
            ui.post(()->{
                fetchingInfo=false;
                if(isFinishing()||isDestroyed())return;
                renderMovies();
                summary.setText("أضفنا معلومات "+good+" عملًا"+
                        (existing>0?" · محفوظة مسبقًا: "+existing:"")+
                        (review>0?" · تحتاج اختيارًا يدويًا: "+review:"")+
                        (stopped?" · تم الإيقاف":""));
                if(problem!=null)showMessage(problem);
                else if(review>0)showMessage("بعض الأفلام لها أكثر من تطابق أو لم تظهر في البحث. افتح كل فيلم منها واختر «جلب المعلومات» لتحديد العمل الصحيح.");
            });
        });
    }
    private void chooseLibrary(){scanUsb(true);}
    private void queryMenu(){
        String[] options={"بحث بالاسم أو السنة",selectedYear>0?"السنة: "+selectedYear:"السنة: الكل","ترتيب العرض","إلغاء البحث والتصفية"};
        new CinemaDialogs.Builder(this).setTitle("بحث وتصفية").setItems(options,(d,n)->{
            if(n==0){
                android.widget.EditText input=new android.widget.EditText(this);input.setSingleLine(true);input.setText(localQuery);input.setHint("اسم العمل أو سنة الإنتاج");
                input.setPadding(dp(18),dp(12),dp(18),dp(12));CinemaStyle.font(input,false);
                new CinemaDialogs.Builder(this).setTitle("ابحث في مكتبتك").setView(input).setPositiveButton("بحث",(dialog,w)->{localQuery=input.getText().toString().trim();refreshSection();}).setNegativeButton("إلغاء",null).show();
            }else if(n==1)yearMenu();
            else if(n==2)new CinemaDialogs.Builder(this).setTitle("ترتيب العرض").setSingleChoiceItems(new String[]{"الاسم: أ إلى ي / A إلى Z","الاسم: ي إلى أ / Z إلى A","السنة: الأحدث أولًا","السنة: الأقدم أولًا"},sortOrder,(dialog,w)->{sortOrder=w;getPreferences(MODE_PRIVATE).edit().putInt("library_sort",w).apply();dialog.dismiss();refreshSection();}).setNegativeButton("رجوع",null).show();
            else {localQuery="";selectedYear=-1;refreshSection();}
        }).setNegativeButton("إغلاق",null).show();
    }
    private void yearMenu(){
        java.util.TreeSet<Integer> years=new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        for(DocumentTreeScanner.Media m:library.movies)if(m.year!=null)years.add(m.year);
        for(DocumentTreeScanner.Show sh:library.shows)for(DocumentTreeScanner.Media m:sh.episodes)if(m.year!=null)years.add(m.year);
        List<Integer> values=new ArrayList<>(years);String[] labels=new String[values.size()+1];labels[0]="كل السنوات";
        for(int i=0;i<values.size();i++)labels[i+1]=values.get(i).toString();
        new CinemaDialogs.Builder(this).setTitle("تصفية حسب السنة").setSingleChoiceItems(labels,values.indexOf(selectedYear)+1,(d,n)->{selectedYear=n==0?-1:values.get(n-1);d.dismiss();refreshSection();}).setNegativeButton("رجوع",null).show();
    }
    private void refreshSection(){
        if("shows".equals(screen))renderShows();else if("episodes".equals(screen)&&openShow!=null)renderEpisodes(openShow);
        else if("movies".equals(screen))renderMovies();else renderPersonal(screen);
        if(!localQuery.isEmpty()||selectedYear>0)summary.setText("نتائج مكتبتك"+(!localQuery.isEmpty()?" · "+localQuery:"")+(selectedYear>0?" · "+selectedYear:"")+" · بحث محلي");
    }
    private String searchTitle(Object o){
        String raw=o instanceof DocumentTreeScanner.Show?((DocumentTreeScanner.Show)o).title:((DocumentTreeScanner.Media)o).title;
        return entryTitle(o)+" "+MediaDisplay.title(raw);
    }
    private Integer entryYear(Object o){
        if(o instanceof DocumentTreeScanner.Media)return ((DocumentTreeScanner.Media)o).year;
        DocumentTreeScanner.Show sh=(DocumentTreeScanner.Show)o;for(DocumentTreeScanner.Media m:sh.episodes)if(m.year!=null)return m.year;return null;
    }
    private List<Object> filtered(List<?> items){
        List<Object> out=new ArrayList<>();for(Object o:items)if(LibraryQuery.matches(searchTitle(o),entryYear(o),localQuery,selectedYear))out.add(o);
        out.sort((a,b)->LibraryQuery.compare(entryTitle(a),entryYear(a),entryTitle(b),entryYear(b),sortOrder));return out;
    }
    private void scanUsb(boolean wholeDrive) {
        if (scanning) return;
        nextScanAll=wholeDrive;
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            new CinemaDialogs.Builder(this)
                    .setTitle("إذن قراءة USB")
                    .setMessage("لإظهار أفلامك وصورها يحتاج «مكتبتي» إلى إذن القراءة فقط. لن يغيّر أي فيلم أو ترجمة على القرص، ويمكنك إلغاء الإذن من إعدادات التلفزيون.")
                    .setPositiveButton("طلب إذن القراءة",(d,w)->requestPermissions(
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},READ_USB_PERMISSION))
                    .setNegativeButton("إلغاء",null).show();
            return;
        }
        scanning=true;
        summary.setText("جارٍ ترتيب أعمالك...");
        work.execute(()->{
            try {
                UsbFileScanner.Scan scan=UsbFileScanner.scan(getApplicationContext(),wholeDrive);
                if(!scan.result.truncated&&scan.result.unreadableFolders==0)LibraryIndex.save(this,scan.result);
                runOnUiThread(()->{
                    scanning=false;
                    directUsbSelected=true;
                    getPreferences(MODE_PRIVATE).edit().putBoolean("direct_usb",true).putBoolean("scan_all",wholeDrive).apply();
                    library=scan.result;
                    renderMovies();
                    DocumentTreeScanner.Result result=scan.result;
                    summary.setText("اكتشفنا "+result.movies.size()+" فيلمًا و"+
                            result.shows.size()+" مسلسلًا"+(result.truncated?" · قد تكون بعض الأعمال غير ظاهرة":""));
                });
            }catch(RuntimeException e){
                runOnUiThread(()->{
                    scanning=false;
                    if(library.videoCount==0)showMessage("تعذر عرض المكتبة الآن. تحقق من اتصال قرص الأفلام وإذن القراءة ثم أعد المحاولة.");
                    summary.setText("القرص غير متاح · نعرض الفهرس المحفوظ؛ التشغيل يتطلب توصيل القرص");
                });
            }
        });
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==READ_USB_PERMISSION) {
            if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)scanUsb(nextScanAll);
            else showMessage("لم تُمنح صلاحية القراءة؛ لم يتم فحص الهاردسك.");
        }
    }

    private void pickFolder() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {startActivityForResult(intent,PICK_FOLDER);}
        catch(ActivityNotFoundException ex) {
            showMessage("اختيار المجلدات غير متاح في نظام هذا الجهاز.");
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==UsbAccess.REQUEST){if(result==RESULT_OK&&data!=null)try{UsbAccess.accept(this,data);work.execute(()->{boolean writable=UsbAccess.probe(this);runOnUiThread(()->showMessage(writable?"نجح اختبار الكتابة على القرص عبر صلاحية النظام":"مُنحت الصلاحية لكن تعذر اختبار الكتابة"));});}catch(Exception e){showMessage("تعذر الاحتفاظ بصلاحية القرص");}return;}
        if(request==PlaybackProgress.REQUEST){PlaybackProgress.accept(this,playbackId,result,data);playbackId=null;if(selectedEntry!=null)previewEntry(selectedEntry);return;}
        if (request!=PICK_FOLDER || result!=RESULT_OK || data==null || data.getData()==null)return;
        Uri tree=data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getPreferences(MODE_PRIVATE).edit().putString(PREF_TREE,tree.toString()).apply();
            selectedTree=tree;
            scanTree();
        } catch(SecurityException e) {
            showMessage("تعذر الاحتفاظ بإذن قراءة USB. يرجى اختيار مصدر يدعم صلاحية القراءة المستمرة.");
        }
    }
    private Uri loadTree() {
        SharedPreferences p=getPreferences(MODE_PRIVATE);
        String value=p.getString(PREF_TREE,null);
        if (TextUtils.isEmpty(value))return null;
        Uri uri=Uri.parse(value);
        for (android.content.UriPermission permission:getContentResolver().getPersistedUriPermissions()) {
            if(permission.isReadPermission() && uri.equals(permission.getUri()))return uri;
        }
        return null;
    }
    private void scanTree() {
        if (scanning) return;
        if (directUsbSelected || selectedTree==null) {scanUsb(nextScanAll);return;}
        scanning=true;
        summary.setText("جارٍ ترتيب أعمالك...");
        Uri requested=selectedTree;
        work.execute(()-> {
            try {
                DocumentTreeScanner.Result result=DocumentTreeScanner.scan(getContentResolver(),requested);
                if(!result.truncated&&result.unreadableFolders==0)LibraryIndex.save(this,result);
                runOnUiThread(()->{
                    scanning=false;
                    library=result;
                    renderMovies();
                    summary.setText("اكتشفنا "+result.movies.size()+" فيلمًا و"+
                            result.shows.size()+" مسلسلًا"+(result.truncated?" · قد تكون بعض الأعمال غير ظاهرة":""));
                });
            } catch(RuntimeException e) {
                runOnUiThread(()->{
                    scanning=false;
                    showMessage("تعذرت قراءة مجلد USB. تأكد من اتصال القرص ومنح صلاحية القراءة.");
                    summary.setText("فشل الفهرسة — يمكن إعادة اختيار USB");
                });
            }
        });
    }
    private void renderEmpty(String msg) {
        shelves.show(new ArrayList<>());
        summary.setText(msg);
        headline.setText("مكتبتي");
    }
    private void renderMovies() {
        screen="movies";openShow=null;headline.setText("مكتبتي");
        List<CinemaRows.Shelf> rows=new ArrayList<>();
        if(!library.movies.isEmpty())rows.add(new CinemaRows.Shelf("أفلامك",filtered(library.movies)));
        if(!library.shows.isEmpty())rows.add(new CinemaRows.Shelf("مسلسلاتك",filtered(library.shows)));
        List<Object> favorites=personalEntries(viewingStore.idsFor(ViewingStore.FAVORITE));
        List<Object> later=personalEntries(viewingStore.idsFor(ViewingStore.WATCHLIST));
        List<Object> recent=personalEntries(viewingStore.recentlyOpened(80));
        recent.removeIf(o->!LibraryQuery.matches(searchTitle(o),entryYear(o),localQuery,selectedYear));
        if(!favorites.isEmpty())rows.add(new CinemaRows.Shelf("المفضلة",filtered(favorites)));
        if(!later.isEmpty())rows.add(new CinemaRows.Shelf("شاهد لاحقًا",filtered(later)));
        if(!recent.isEmpty())rows.add(new CinemaRows.Shelf("آخر ما فتحته",recent));
        rows.removeIf(row->row.items.isEmpty());
        shelves.show(rows);if(!rows.isEmpty())previewEntry(rows.get(0).items.get(0));
        else emptySelection();
        summary.setText("مجموعتك · "+library.movies.size()+" فيلمًا · "+library.shows.size()+" مسلسلًا");
    }
    private void renderShows() {
        screen="shows";openShow=null;headline.setText("مكتبتي");
        showEntries(filtered(library.shows));
        summary.setText("المسلسلات: "+library.shows.size()+" · اضغط OK لعرض المواسم والحلقات");
    }
    private void renderPersonal(String target){
        screen=target;openShow=null;
        String heading="favorites".equals(target)?"المفضلة":
                "watchlist".equals(target)?"شاهد لاحقًا":
                "watched".equals(target)?"تمت مشاهدته":"آخر ما فتحته";
        headline.setText("مكتبتي");
        List<String> ids="recent".equals(target)?viewingStore.recentlyOpened(80):
                viewingStore.idsFor("favorites".equals(target)?ViewingStore.FAVORITE:
                "watchlist".equals(target)?ViewingStore.WATCHLIST:ViewingStore.WATCHED);
        Map<String,DocumentTreeScanner.Media> available=new HashMap<>();
        for(DocumentTreeScanner.Media item:library.movies)
            available.put(MediaIdentity.fromUri(item.videoUri.toString()),item);
        for(DocumentTreeScanner.Show show:library.shows)
            for(DocumentTreeScanner.Media episode:show.episodes)
                available.put(MediaIdentity.fromUri(episode.videoUri.toString()),episode);
        List<Object> entries=new ArrayList<>();
        for(String id:ids){
            DocumentTreeScanner.Media item=available.get(id);
            if(item!=null)entries.add(item);
        }
        entries=filtered(entries);
        showEntries(entries);
        if(ids.isEmpty())summary.setText("لا توجد أعمال في «"+heading+"» حتى الآن.");
        else if(entries.isEmpty())summary.setText("أعمال محفوظة في «"+heading+
                "». افحص USB نفسه لإظهار الصور وتشغيل الأعمال.");
        else summary.setText(heading+" · "+entries.size()+" عمل متاح"+
                (ids.size()>entries.size()?" · بعض الأعمال محفوظة لكن USB غير متاح":""));
    }
    private void renderEpisodes(DocumentTreeScanner.Show show) {
        screen="episodes";openShow=show;
        headline.setText("مكتبتي");
        showEntries(filtered(show.episodes));
        summary.setText("عدد الحلقات: "+show.episodes.size()+" · اضغط رجوع للعودة");
    }
    private List<Object> personalEntries(List<String> ids){
        if(ids.isEmpty())return new ArrayList<>();
        Map<String,Object> map=new HashMap<>();
        for(DocumentTreeScanner.Media m:library.movies)map.put(MediaIdentity.fromUri(m.videoUri.toString()),m);
        for(DocumentTreeScanner.Show show:library.shows)for(DocumentTreeScanner.Media m:show.episodes)map.put(MediaIdentity.fromUri(m.videoUri.toString()),m);
        List<Object> entries=new ArrayList<>();for(String id:ids)if(map.containsKey(id))entries.add(map.get(id));return entries;
    }
    private void showEntries(List<Object> entries){
        List<CinemaRows.Shelf> rows=new ArrayList<>();
        if("episodes".equals(screen)){
            java.util.TreeMap<Integer,List<Object>> seasons=new java.util.TreeMap<>();
            for(Object o:entries){DocumentTreeScanner.Media m=(DocumentTreeScanner.Media)o;
                if(!seasons.containsKey(m.season))seasons.put(m.season,new ArrayList<>());seasons.get(m.season).add(o);}
            for(Map.Entry<Integer,List<Object>> e:seasons.entrySet()){
                e.getValue().sort((a,b)->Integer.compare(((DocumentTreeScanner.Media)a).episode,((DocumentTreeScanner.Media)b).episode));
                rows.add(new CinemaRows.Shelf(e.getKey()<0?"الحلقات":"الموسم "+e.getKey(),e.getValue()));
            }
        }else rows.add(new CinemaRows.Shelf("shows".equals(screen)?"مسلسلاتك":"favorites".equals(screen)?"المفضلة":"watchlist".equals(screen)?"شاهد لاحقًا":"recent".equals(screen)?"آخر ما فتحته":"تمت مشاهدته",entries));
        shelves.show(rows);
        if(!entries.isEmpty())previewEntry(entries.get(0));
        else emptySelection();
    }
    private void emptySelection(){
        if(pendingHero!=null)ui.removeCallbacks(pendingHero);
        selectedEntry=null;previewPoster.setTag(null);previewPoster.setImageDrawable(null);previewMeta.setText("");
        boolean narrowed=!localQuery.isEmpty()||selectedYear>0;
        previewTitle.setText(narrowed?"لا توجد نتائج":"مكتبتك تنتظرك");
        previewText.setText(narrowed?"جرّب اسمًا آخر أو ألغِ التصفية لعرض بقية أعمالك.":"لا توجد أعمال متاحة في هذا القسم. يمكنك تحديث المكتبة من القائمة.");
        heroAction.setText(narrowed?"إلغاء التصفية":"إضافة المكتبة");
    }
    private void previewEntry(Object entry){
        selectedEntry=entry;previewTitle.setText(entryTitle(entry));previewTitle.setTextSize(entryTitle(entry).length()>26?27:34);
        if(entry instanceof DocumentTreeScanner.Show){DocumentTreeScanner.Show sh=(DocumentTreeScanner.Show)entry;previewMeta.setText(sh.episodes.size()+" حلقة · من مكتبتك");previewText.setText("اختر موسمًا واكتشف الحلقة التالية.");heroAction.setText("استعراض الحلقات");}
        else {DocumentTreeScanner.Media m=(DocumentTreeScanner.Media)entry;TmdbService.Detail d=previewCache.load(m.series?m.seriesName:m.title,m.year,m.series);
            String facts=(m.year==null?"من مكتبتك":m.year.toString())+(d!=null&&d.votes>0?"   ·   TMDb "+String.format(Locale.US,"%.1f",d.score):"");
            previewMeta.setText(facts+PlaybackProgress.label(this,MediaIdentity.fromUri(m.videoUri.toString())));previewText.setText(d!=null&&!d.overview.isEmpty()?ArabicOverview.text(this,d):"جاهز للمشاهدة من مكتبتك. افتح التفاصيل للتشغيل والترجمة.");heroAction.setText(viewingStore.state(MediaIdentity.fromUri(m.videoUri.toString())).lastOpened>0?"استكمال في VLC":"تشغيل");}
        Uri heroUri=entryPoster(entry,true);
        // A portrait USB cover remains a portrait, rather than being enlarged into a blurry backdrop.
        boolean wide=heroUri!=null&&"https".equals(heroUri.getScheme())&&heroUri.toString().contains("/w780/");
        previewPoster.setScaleType(wide?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_START);
        previewPoster.setPadding(wide?0:dp(42),wide?0:dp(64),0,0);
        loadPoster(previewPoster,heroUri);
    }
    private void showDetails(DocumentTreeScanner.Media item) {
        // The whole screen now belongs to a dedicated cinematic UI.
        // Keeping MainActivity on the stack restores the previous grid/D-pad position.
        Intent show=new Intent(this,MediaDetailsActivity.class);
        show.putExtra("title",item.title);
        show.putExtra("series_title",item.seriesName==null?"":item.seriesName);
        show.putExtra("is_series",item.series);
        show.putExtra("year",item.year==null?-1:item.year);
        show.putExtra("filename",item.fileName);
        show.putExtra("video_uri",item.videoUri.toString());
        show.putExtra("poster_uri",item.posterUri==null?"":item.posterUri.toString());
        ArrayList<String> uriList=new ArrayList<>();
        ArrayList<String> labels=new ArrayList<>();
        for(int n=0;n<item.subtitles.size();n++){
            DocumentTreeScanner.Subtitle subtitle=item.subtitles.get(n);
            uriList.add(subtitle.uri.toString());
            labels.add(MediaDisplay.subtitle(subtitle.fileName,n));
        }
        show.putStringArrayListExtra("subtitle_uris",uriList);
        show.putStringArrayListExtra("subtitle_names",labels);
        startActivity(show);
    }
    private DocumentTreeScanner.Subtitle autoSubtitle(DocumentTreeScanner.Media item){
        for(DocumentTreeScanner.Subtitle subtitle:item.subtitles)
            if(SubtitleMatcher.chooseAutomaticScore(subtitle.score)>=0)return subtitle;
        return null;
    }
    private void chooseSubtitle(DocumentTreeScanner.Media item){
        List<String> choices=new ArrayList<>();
        List<DocumentTreeScanner.Subtitle> available=item.subtitles;
        choices.add("اختيار الترجمة تلقائيًا");
        choices.add("بدون ترجمة خارجية");
        for(int n=0;n<available.size();n++)choices.add(MediaDisplay.subtitle(available.get(n).fileName,n));
        choices.add("مشغل آخر");
        new CinemaDialogs.Builder(this)
                .setTitle("اختر الترجمة للفيلم")
                .setItems(choices.toArray(new String[0]),(dialog,which)->{
                    if(which==0)launchVideo(item,true,autoSubtitle(item));
                    else if(which==1)launchVideo(item,true,null);
                    else if(which==choices.size()-1)launchVideo(item,false,null);
                    else launchVideo(item,true,available.get(which-2));
                }).setNegativeButton("إلغاء",null).show();
    }
    private void launchVideo(DocumentTreeScanner.Media item,boolean preferVlc,
                             DocumentTreeScanner.Subtitle subtitle) {
        Intent play=new Intent(Intent.ACTION_VIEW);
        play.setDataAndType(item.videoUri,videoMime(item.fileName));
        // Both SAF and local provider grant temporary read access to external players.
        play.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData grants=ClipData.newUri(getContentResolver(),"USB video",item.videoUri);
        // Current VLC for Android accepts the title and subtitle location as STRING extras.
        // Passing the URI grant for both entries lets VLC open the adjacent SRT/ASS.
        String displayName=item.series ? item.seriesName+" · "+item.title : item.title;
        if(!item.series && item.year!=null)displayName+=" ("+item.year+")";
        play.putExtra("title",displayName);
        play.putExtra("item_title",displayName);
        if(preferVlc && subtitle!=null){
            play.putExtra("subtitles_location",subtitle.uri.toString());
            grants.addItem(new ClipData.Item(subtitle.uri));
        }
        play.setClipData(grants);
        if(preferVlc) play.setPackage("org.videolan.vlc");
        try {
            if(preferVlc){playbackId=MediaIdentity.fromUri(item.videoUri.toString());PlaybackProgress.prepare(this,play,playbackId);startActivityForResult(play,PlaybackProgress.REQUEST);}else startActivity(Intent.createChooser(play,"اختر مشغل الفيديو"));
            viewingStore.recordLaunch(MediaIdentity.fromUri(item.videoUri.toString()),entryTitle(item));
        } catch(ActivityNotFoundException e) {
            if(preferVlc) {
                new CinemaDialogs.Builder(this)
                    .setMessage("لم نجد VLC. هل تريد اختيار مشغل آخر؟")
                    .setPositiveButton("اختيار مشغل",(d,w)->launchVideo(item,false,null))
                    .setNegativeButton("إلغاء",null).show();
            } else showMessage("لم نجد تطبيقًا يستطيع تشغيل هذا النوع من الفيديو.");
        } catch(SecurityException e) {
            showMessage("مزود USB لم يسمح بفتح الملف في المشغل الخارجي.");
        }
    }
    private static String videoMime(String filename) {
        String lower=filename.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".mkv"))return "video/x-matroska";
        if(lower.endsWith(".avi"))return "video/x-msvideo";
        if(lower.endsWith(".webm"))return "video/webm";
        if(lower.endsWith(".mp4")||lower.endsWith(".m4v"))return "video/mp4";
        return "video/*";
    }
    private void showMessage(String text) {
        new CinemaDialogs.Builder(this).setTitle("مكتبتي").setMessage(text)
                .setPositiveButton("حسنًا",null).show();
    }

    private void loadPoster(ImageView image,Uri uri) {
        image.setImageDrawable(null);
        final boolean hero=image==previewPoster;
        final int maxW=hero?1000:320,maxH=hero?600:460;
        String key=uri==null?"":uri.toString()+"|"+maxW;
        image.setTag(key);
        if(uri==null)return;
        Bitmap hit=bitmaps.get(key);
        if(hit!=null) {image.setImageBitmap(hit);return;}
        pictures.execute(()->{
            if(!key.equals(image.getTag())||Thread.currentThread().isInterrupted())return;
            Bitmap bitmap=null;
            try {
                if("https".equals(uri.getScheme())){
                    if(!"image.tmdb.org".equals(uri.getHost()) || !previewCache.configured())return;
                    HttpsURLConnection connection=null;
                    try{
                        connection=(HttpsURLConnection)new URL(uri.toString()).openConnection();
                        connection.setConnectTimeout(4000);
                        connection.setReadTimeout(4500);
                        connection.setInstanceFollowRedirects(false);
                        if(connection.getResponseCode()==200){
                            BitmapFactory.Options opts=new BitmapFactory.Options();
                            opts.inPreferredConfig=Bitmap.Config.RGB_565;
                            opts.inSampleSize=hero?1:2;
                            try(InputStream in=connection.getInputStream()){
                                bitmap=BitmapFactory.decodeStream(in,null,opts);
                            }
                        }
                    }finally{if(connection!=null)connection.disconnect();}
                } else {
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inJustDecodeBounds=true;
                try(InputStream in=getContentResolver().openInputStream(uri)) {
                    if(in==null)return;
                    BitmapFactory.decodeStream(in,null,options);
                }
                int sample=1;
                while(options.outWidth/sample>maxW || options.outHeight/sample>maxH) sample*=2;
                options.inJustDecodeBounds=false;
                options.inSampleSize=sample;
                options.inPreferredConfig=Bitmap.Config.RGB_565;
                try(InputStream in=getContentResolver().openInputStream(uri)) {
                    if(in!=null)bitmap=BitmapFactory.decodeStream(in,null,options);
                }
                }
            } catch(Exception ignored) {}
            if(bitmap==null && !"https".equals(uri.getScheme())){
                try{
                    bitmap=ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(getContentResolver(),uri),
                            (decoder,info,source)->{
                                int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                                if(w>0 && h>0){
                                    float scale=Math.min(1f,Math.min((float)maxW/w,(float)maxH/h));
                                    decoder.setTargetSize(Math.max(1,Math.round(w*scale)),
                                            Math.max(1,Math.round(h*scale)));
                                }
                                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                            });
                }catch(Exception ignored){}
            }
            if(bitmap!=null) {
                Bitmap ready=bitmap;
                ui.post(()->{
                    bitmaps.put(key,ready);
                    if(!isFinishing() && key.equals(image.getTag()))image.setImageBitmap(ready);
                });
            }
        });
    }
    @Override public void onBackPressed() {
        if("episodes".equals(screen))renderShows();
        else if("shows".equals(screen))renderMovies();
        else super.onBackPressed();
    }
    @Override protected void onResume(){
        super.onResume();if(getSharedPreferences("library_changes",0).getBoolean("refresh",false)){getSharedPreferences("library_changes",0).edit().putBoolean("refresh",false).apply();scanTree();}if(previewCache!=null)previewCache.clearMemory();if(selectedEntry!=null)previewEntry(selectedEntry);
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("playbackId",playbackId);}
    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        work.shutdownNow();pictures.shutdownNow();
        bitmaps.evictAll();
        if(viewingStore!=null)viewingStore.close();
        super.onDestroy();
    }
}


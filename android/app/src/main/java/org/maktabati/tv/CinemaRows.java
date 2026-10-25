package org.maktabati.tv;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/** Both axes recycle views; a large USB library never creates all poster views at once. */
final class CinemaRows extends RecyclerView {
    static final class Shelf {
        final String title; final List<Object> items;
        Shelf(String t,List<Object> i){title=t;items=i;}
    }
    interface Callback {
        String title(Object item);
        void picture(ImageView image,Object item);
        void selected(Object item);
        void open(Object item);
    }
    private final Callback callback;
    private final List<Shelf> shelves=new ArrayList<>();
    CinemaRows(Context c,Callback cb){
        super(c);callback=cb;setLayoutDirection(LAYOUT_DIRECTION_RTL);
        setLayoutManager(new LinearLayoutManager(c));setAdapter(new ShelfAdapter());
        setClipToPadding(false);setPadding(0,0,0,dp(12));setItemAnimator(null);
    }
    private int dp(int n){return CinemaStyle.dp(getContext(),n);}
    void show(List<Shelf> data){shelves.clear();shelves.addAll(data);getAdapter().notifyDataSetChanged();scrollToPosition(0);}
    private final class ShelfHolder extends ViewHolder {
        final TextView heading;final RecyclerView row;
        ShelfHolder(LinearLayout root,TextView h,RecyclerView r){super(root);heading=h;row=r;}
    }
    private final class ShelfAdapter extends Adapter<ShelfHolder>{
        public int getItemCount(){return shelves.size();}
        public ShelfHolder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout root=new LinearLayout(getContext());root.setOrientation(LinearLayout.VERTICAL);
            root.setLayoutParams(new RecyclerView.LayoutParams(-1,dp(247)));
            TextView h=new TextView(getContext());h.setTextColor(Color.WHITE);h.setTextSize(20);
            CinemaStyle.font(h,true);h.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            root.addView(h,new LinearLayout.LayoutParams(-1,dp(34)));
            RecyclerView r=new RecyclerView(getContext());r.setLayoutDirection(LAYOUT_DIRECTION_RTL);
            r.setLayoutManager(new LinearLayoutManager(getContext(),HORIZONTAL,false));
            r.setItemAnimator(null);r.setClipToPadding(false);r.setPadding(dp(3),dp(5),dp(3),dp(8));
            r.setPreserveFocusAfterLayout(true);
            root.addView(r,new LinearLayout.LayoutParams(-1,dp(213)));
            return new ShelfHolder(root,h,r);
        }
        public void onBindViewHolder(ShelfHolder h,int p){Shelf s=shelves.get(p);h.heading.setText(s.title);h.row.setAdapter(new PosterAdapter(s.items));}
    }
    private final class PosterHolder extends ViewHolder {
        final ImageView image;final TextView title;Object item;
        PosterHolder(LinearLayout root,ImageView im,TextView text){
            super(root);image=im;title=text;root.setFocusable(true);
            root.setOnFocusChangeListener((v,focus)->{
                v.setBackground(CinemaStyle.box(getContext(),focus?0x22ffffff:Color.TRANSPARENT,10,focus?Color.WHITE:0));
                if(focus&&item!=null)callback.selected(item);
            });
            root.setOnClickListener(v->{if(item!=null)callback.open(item);});
        }
    }
    private final class PosterAdapter extends Adapter<PosterHolder>{
        final List<Object> items;PosterAdapter(List<Object> data){items=data;}
        public int getItemCount(){return items.size();}
        public PosterHolder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout card=new LinearLayout(getContext());card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(4),dp(4),dp(4),dp(4));
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(dp(124),dp(201));lp.setMarginEnd(dp(13));card.setLayoutParams(lp);
            ImageView im=new ImageView(getContext());im.setScaleType(ImageView.ScaleType.CENTER_CROP);
            im.setBackground(CinemaStyle.box(getContext(),0xff1b2130,7,0));im.setClipToOutline(true);
            card.addView(im,new LinearLayout.LayoutParams(-1,dp(160)));
            TextView title=new TextView(getContext());title.setTextColor(0xffe9ecf1);title.setTextSize(13);
            CinemaStyle.font(title,true);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);card.addView(title,new LinearLayout.LayoutParams(-1,dp(30)));
            return new PosterHolder(card,im,title);
        }
        public void onBindViewHolder(PosterHolder h,int pos){h.item=items.get(pos);h.title.setText(callback.title(h.item));h.itemView.setContentDescription(h.title.getText());callback.picture(h.image,h.item);}
        public void onViewRecycled(PosterHolder h){h.image.setTag(null);h.image.setImageDrawable(null);h.item=null;}
    }
}

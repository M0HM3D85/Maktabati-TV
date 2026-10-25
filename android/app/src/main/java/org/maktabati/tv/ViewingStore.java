package org.maktabati.tv;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * Private on-device viewing state. No USB writes and no external data sync.
 * Last opened = accepted playback handoff, NOT proof of watch time or completion.
 */
final class ViewingStore extends SQLiteOpenHelper {
    private static final String DB="my_media_user_state.db";
    private static final int VERSION=1;
    static final int FAVORITE=1;
    static final int WATCHLIST=2;
    static final int WATCHED=3;
    static final class State {
        final boolean favorite,watchlist,watched;
        final long lastOpened;
        State(boolean favorite,boolean watchlist,boolean watched,long lastOpened){
            this.favorite=favorite;this.watchlist=watchlist;this.watched=watched;
            this.lastOpened=lastOpened;
        }
    }
    ViewingStore(Context context) {super(context.getApplicationContext(),DB,null,VERSION);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE viewing ("
            +"media_id TEXT PRIMARY KEY NOT NULL,"
            +"display_title TEXT NOT NULL DEFAULT '',"
            +"favorite INTEGER NOT NULL DEFAULT 0,"
            +"watchlist INTEGER NOT NULL DEFAULT 0,"
            +"watched INTEGER NOT NULL DEFAULT 0,"
            +"last_opened INTEGER NOT NULL DEFAULT 0,"
            +"open_count INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX viewing_last_opened_idx ON viewing(last_opened DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to){
        // Migration must be additive; no DROP TABLE, no user-history loss.
        if(from>to)throw new IllegalStateException("Unsupported database downgrade");
        if(from!=to)throw new IllegalStateException("Explicit database migration required");
    }
    private void ensure(SQLiteDatabase db,String id,String display){
        ContentValues row=new ContentValues();
        row.put("media_id",id);
        row.put("display_title",display==null?"":display);
        db.insertWithOnConflict("viewing",null,row,SQLiteDatabase.CONFLICT_IGNORE);
    }
    State state(String id){
        try(Cursor c=getReadableDatabase().query("viewing",
            new String[]{"favorite","watchlist","watched","last_opened"},
            "media_id=?",new String[]{id},null,null,null)){
            if(c.moveToFirst())return new State(c.getInt(0)==1,c.getInt(1)==1,c.getInt(2)==1,c.getLong(3));
        }
        return new State(false,false,false,0);
    }
    void setFlag(String id,String title,int flag,boolean value){
        if(flag!=FAVORITE && flag!=WATCHLIST && flag!=WATCHED)
            throw new IllegalArgumentException("Invalid viewing-state flag");
        String col=flag==FAVORITE?"favorite":flag==WATCHLIST?"watchlist":"watched";
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            ensure(db,id,title);
            ContentValues v=new ContentValues();
            v.put(col,value?1:0);
            db.update("viewing",v,"media_id=?",new String[]{id});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void recordLaunch(String id,String title){
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            ensure(db,id,title);
            ContentValues v=new ContentValues();
            v.put("display_title",title==null?"":title);
            v.put("last_opened",System.currentTimeMillis());
            db.update("viewing",v,"media_id=?",new String[]{id});
            db.execSQL("UPDATE viewing SET open_count=open_count+1 WHERE media_id=?",
                    new Object[]{id});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    List<String> idsFor(int flag){
        String where=flag==FAVORITE?"favorite=1":flag==WATCHLIST?"watchlist=1":"watched=1";
        List<String> ids=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("viewing",
                new String[]{"media_id"},where,null,null,null,"display_title COLLATE NOCASE ASC")){
            while(c.moveToNext())ids.add(c.getString(0));
        }
        return ids;
    }
    List<String> recentlyOpened(int limit){
        List<String> ids=new ArrayList<>();
        int count=Math.max(1,Math.min(limit,80));
        try(Cursor c=getReadableDatabase().query("viewing",
                new String[]{"media_id"},"last_opened>0",null,null,null,
                "last_opened DESC",Integer.toString(count))){
            while(c.moveToNext())ids.add(c.getString(0));
        }
        return ids;
    }
}


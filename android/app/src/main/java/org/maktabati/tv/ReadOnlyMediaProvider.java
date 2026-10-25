package org.maktabati.tv;

import android.Manifest;
import android.content.pm.PackageManager;
import android.provider.MediaStore;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Delivers USB files as content:// with per-Intent read grant. Never writes. */
public final class ReadOnlyMediaProvider extends ContentProvider {
    static final String AUTHORITY="org.maktabati.tv.local";

    static Uri uriFor(UsbVolumes.Volume volume, File file) {
        String relative;
        try {
            String prefix=volume.root.getCanonicalPath()+File.separator;
            String canonical=file.getCanonicalPath();
            if(!canonical.startsWith(prefix))throw new IllegalArgumentException("Outside USB");
            relative=canonical.substring(prefix.length());
        }catch(IOException e){throw new IllegalArgumentException(e);}
        Uri.Builder builder=new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(volume.uuid);
        for(String part:relative.split("/"))builder.appendPath(part);
        return builder.build();
    }
    private File resolve(Uri uri) throws FileNotFoundException {
        if(!"content".equals(uri.getScheme()) || !AUTHORITY.equals(uri.getAuthority()))
            throw new FileNotFoundException("Unknown URI");
        List<String> parts=uri.getPathSegments();
        if(parts.size()<2)throw new FileNotFoundException("No file");
        Context context=getContext();
        if(context==null)throw new FileNotFoundException("No context");
        UsbVolumes.Volume volume=null;
        for(UsbVolumes.Volume v:UsbVolumes.removable(context)) {
            if(v.uuid.equals(parts.get(0)))volume=v;
        }
        if(volume==null)throw new FileNotFoundException("No removable USB");
        File f=volume.root;
        for(int i=1;i<parts.size();i++)f=new File(f,parts.get(i));
        try {
            if(!f.getCanonicalPath().startsWith(volume.root.getCanonicalPath()+File.separator))
                throw new FileNotFoundException("Outside USB root");
        }catch(IOException e){throw new FileNotFoundException("Bad path");}
        if(!f.isFile() || !f.canRead())throw new FileNotFoundException("Unreadable file");
        return f;
    }
    @Override public boolean onCreate(){return true;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read-only");
        return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){
        String last=uri.getLastPathSegment();
        if(last==null)return "application/octet-stream";
        String n=last.toLowerCase(Locale.ROOT);
        if(n.endsWith(".mkv"))return "video/x-matroska";
        if(n.endsWith(".mp4") || n.endsWith(".m4v"))return "video/mp4";
        if(n.endsWith(".avi"))return "video/x-msvideo";
        if(n.endsWith(".webm"))return "video/webm";
        if(n.endsWith(".srt"))return "application/x-subrip";
        if(n.endsWith(".ass") || n.endsWith(".ssa"))return "text/x-ssa";
        if(n.endsWith(".vtt"))return "text/vtt";
        if(n.endsWith(".idx") || n.endsWith(".sub"))return "application/octet-stream";
        if(n.endsWith(".jpg") || n.endsWith(".jpeg"))return "image/jpeg";
        if(n.endsWith(".png"))return "image/png";
        if(n.endsWith(".webp"))return "image/webp";
        if(n.endsWith(".bmp"))return "image/bmp";
        if(n.endsWith(".gif"))return "image/gif";
        if(n.endsWith(".heic"))return "image/heic";
        if(n.endsWith(".heif"))return "image/heif";
        if(n.endsWith(".avif"))return "image/avif";
        if(n.endsWith(".tif") || n.endsWith(".tiff"))return "image/tiff";
        return "application/octet-stream";
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        try {
            File f=resolve(uri);
            String[] names=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor cursor=new MatrixCursor(names);
            Object[] row=new Object[names.length];
            // VLC 3.7.1 queries MediaStore.Video.Media.DATA before starting playback.
            // Android 9 VLC can read the same removable USB directly when the user has
            // granted it storage read permission. Allow ONLY VLC to resolve to a path.
            // Other players retain grant-scoped content:// access (no path disclosure).
            boolean vlcCanRead=VlcPathDisclosurePolicy.allowed(
                    getCallingPackage(),
                    getContext()!=null && getContext().checkCallingPermission(
                            Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED);
            for(int i=0;i<names.length;i++){
                if(OpenableColumns.DISPLAY_NAME.equals(names[i]))row[i]=f.getName();
                else if(OpenableColumns.SIZE.equals(names[i]))row[i]=f.length();
                else if(MediaStore.MediaColumns.DATA.equals(names[i]) && vlcCanRead)
                    row[i]=f.getCanonicalPath();
            }
            cursor.addRow(row);
            return cursor;
        }catch(IOException e){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues value){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues value,String select,String[] args){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String select,String[] args){throw new UnsupportedOperationException("Read only");}
}


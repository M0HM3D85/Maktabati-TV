package org.maktabati.tv;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Discover mounted removable volumes via public Android 9 APIs only. */
final class UsbVolumes {
    static final class Volume {
        final String uuid;
        final File root;
        Volume(String uuid) { this.uuid=uuid; this.root=new File("/storage",uuid); }
    }
    static List<Volume> removable(Context context) {
        List<Volume> result=new ArrayList<>();
        StorageManager sm=(StorageManager)context.getSystemService(Context.STORAGE_SERVICE);
        if(sm==null)return result;
        for(StorageVolume v:sm.getStorageVolumes()) {
            String uuid=v.getUuid(),state=v.getState();
            if(!v.isRemovable() || v.isPrimary() || uuid==null ||
                !uuid.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{3,127}") ||
                !(Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))) continue;
            Volume candidate=new Volume(uuid);
            if(candidate.root.isDirectory() && candidate.root.canRead())result.add(candidate);
        }
        return result;
    }
}


package org.maktabati.tv;

/**
 * Restrict legacy MediaStore._data path projection to VLC with OS-approved USB read.
 * All other recipients keep the content:// read-grant path (no raw path disclosure).
 */
final class VlcPathDisclosurePolicy {
    static boolean allowed(String callerPackage,boolean hasExternalStorageReadPermission){
        return hasExternalStorageReadPermission && "org.videolan.vlc".equals(callerPackage);
    }
}


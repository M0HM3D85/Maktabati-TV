package org.maktabati.tv;

import static org.junit.Assert.*;
import org.junit.Test;

public final class VlcPathDisclosurePolicyTest {
    @Test public void grantsPathToPermittedVlcOnly() {
        assertTrue(VlcPathDisclosurePolicy.allowed("org.videolan.vlc",true));
    }
    @Test public void rejectsVlcWithoutAndroidStoragePermission() {
        assertFalse(VlcPathDisclosurePolicy.allowed("org.videolan.vlc",false));
    }
    @Test public void refusesOtherPlayersEvenWithReadPermission() {
        assertFalse(VlcPathDisclosurePolicy.allowed("com.example.player",true));
    }
    @Test public void refusesNullOrSpoofedPackage() {
        assertFalse(VlcPathDisclosurePolicy.allowed(null,true));
        assertFalse(VlcPathDisclosurePolicy.allowed("org.videolan.vlc.evil",true));
    }
}


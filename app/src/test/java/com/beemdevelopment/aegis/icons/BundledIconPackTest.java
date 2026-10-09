package com.beemdevelopment.aegis.icons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.UUID;

// a plain Application: AegisApplication installs the bundled packs on its own thread
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class BundledIconPackTest {
    private static final UUID AEGIS_ICONS = UUID.fromString("c1018b93-4e8c-490a-b575-30dde62a833e");

    private final Context _context = ApplicationProvider.getApplicationContext();

    @Test
    public void testInstallsTheBundledPackOnce() throws Exception {
        IconPackManager manager = new IconPackManager(_context);
        manager.installBundledPacks(_context);

        IconPack pack = find(manager);
        assertNotNull(pack);
        assertEquals(1007, pack.getIcons().size());
        for (IconPack.Icon icon : pack.getIcons()) {
            assertTrue(icon.getFile().isFile());
        }
        assertFalse(pack.getSuggestedIcons("Google").isEmpty());

        // a later start finds the same pack and leaves it alone
        File packJson = new File(pack.getDirectory(), "pack.json");
        long modified = packJson.lastModified();
        IconPackManager restarted = new IconPackManager(_context);
        restarted.installBundledPacks(_context);
        assertEquals(pack, find(restarted));
        assertEquals(modified, packJson.lastModified());
    }

    @Test
    public void testRemovedPackStaysRemoved() throws Exception {
        IconPackManager manager = new IconPackManager(_context);
        manager.installBundledPacks(_context);
        manager.removeIconPack(find(manager));

        IconPackManager restarted = new IconPackManager(_context);
        restarted.installBundledPacks(_context);
        assertNull(find(restarted));
    }

    @Test
    public void testInterruptedInstallIsRedone() throws Exception {
        IconPackManager manager = new IconPackManager(_context);
        manager.installBundledPacks(_context);
        IconPack pack = find(manager);
        File iconsDir = new File(_context.getFilesDir(), "icons");
        assertTrue(new File(pack.getDirectory(), "pack.json").delete());
        assertTrue(new File(iconsDir, AEGIS_ICONS + ".bundled").delete());

        IconPackManager restarted = new IconPackManager(_context);
        assertNull(find(restarted));
        restarted.installBundledPacks(_context);
        assertEquals(pack, find(restarted));
    }

    private static IconPack find(IconPackManager manager) {
        for (IconPack pack : manager.getIconPacks()) {
            if (pack.getUUID().equals(AEGIS_ICONS)) {
                return pack;
            }
        }
        return null;
    }
}

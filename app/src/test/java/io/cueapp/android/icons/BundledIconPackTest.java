package io.cueapp.android.icons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import io.cueapp.android.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
public class BundledIconPackTest {
    private final Context _context = ApplicationProvider.getApplicationContext();
    private final List<String> _bundled = Arrays.asList(_context.getResources().getStringArray(R.array.bundled_icon_packs));

    @Test
    public void testInstallsEveryBundledPackOnceInPreferredOrder() throws Exception {
        IconPackManager manager = new IconPackManager(_context);
        manager.installBundledPacks(_context);

        assertEquals(_bundled, uuids(manager));
        for (IconPack pack : manager.getIconPacks()) {
            assertFalse(pack.getIcons().isEmpty());
            for (IconPack.Icon icon : pack.getIcons()) {
                assertTrue(icon.getFile().isFile());
            }
        }
        assertFalse(manager.getIconPacks().get(0).getSuggestedIcons("Google").isEmpty());

        // a later start finds the same packs and leaves them alone
        File packJson = new File(manager.getIconPacks().get(0).getDirectory(), "pack.json");
        long modified = packJson.lastModified();
        IconPackManager restarted = new IconPackManager(_context);
        restarted.installBundledPacks(_context);
        assertEquals(manager.getIconPacks(), restarted.getIconPacks());
        assertEquals(modified, packJson.lastModified());
    }

    @Test
    public void testRemovedPackStaysRemoved() throws Exception {
        IconPackManager manager = new IconPackManager(_context);
        manager.installBundledPacks(_context);
        manager.removeIconPack(find(manager, _bundled.get(0)));

        IconPackManager restarted = new IconPackManager(_context);
        restarted.installBundledPacks(_context);
        assertNull(find(restarted, _bundled.get(0)));
        assertEquals(_bundled.subList(1, _bundled.size()), uuids(restarted));
    }

    @Test
    public void testInterruptedInstallIsRedone() throws Exception {
        IconPackManager manager = new IconPackManager(_context);
        manager.installBundledPacks(_context);
        IconPack pack = find(manager, _bundled.get(0));
        File iconsDir = new File(_context.getFilesDir(), "icons");
        assertTrue(new File(pack.getDirectory(), "pack.json").delete());
        assertTrue(new File(iconsDir, pack.getUUID() + ".bundled").delete());

        IconPackManager restarted = new IconPackManager(_context);
        assertNull(find(restarted, _bundled.get(0)));
        restarted.installBundledPacks(_context);
        assertEquals(pack, find(restarted, _bundled.get(0)));
    }

    private static List<String> uuids(IconPackManager manager) {
        return manager.getIconPacks().stream().map(p -> p.getUUID().toString()).collect(Collectors.toList());
    }

    private static IconPack find(IconPackManager manager, String uuid) {
        for (IconPack pack : manager.getIconPacks()) {
            if (pack.getUUID().equals(UUID.fromString(uuid))) {
                return pack;
            }
        }
        return null;
    }
}

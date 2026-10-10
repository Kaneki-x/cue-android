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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    @Test
    public void testRetiredBundledPackIsRemovedButNewerImportStays() throws Exception {
        List<String> retired = Arrays.asList(_context.getResources().getStringArray(R.array.retired_icon_packs));
        File iconsDir = new File(_context.getFilesDir(), "icons");
        // as an earlier version left them: one bundled copy with its marker, and one the user
        // imported in a version newer than the one that was bundled
        writePack(iconsDir, retired.get(0), 3);
        writeMarker(iconsDir, retired.get(0), 3);
        writePack(iconsDir, retired.get(1), 9);
        writeMarker(iconsDir, retired.get(1), 5);

        IconPackManager manager = new IconPackManager(_context);
        assertTrue(find(manager, retired.get(0)) != null);
        manager.installBundledPacks(_context);
        assertNull(find(manager, retired.get(0)));
        assertFalse(new File(iconsDir, retired.get(0)).exists());
        assertFalse(new File(iconsDir, retired.get(0) + ".bundled").exists());
        assertTrue(find(manager, retired.get(1)) != null);
        assertTrue(uuids(manager).containsAll(_bundled));
    }

    private static void writePack(File iconsDir, String uuid, int version) throws Exception {
        File dir = new File(iconsDir, uuid + "/" + version);
        assertTrue(dir.mkdirs());
        String json = String.format("{\"uuid\": \"%s\", \"name\": \"Old %s\", \"version\": %d, \"icons\": []}", uuid, uuid, version);
        Files.write(new File(dir, "pack.json").toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeMarker(File iconsDir, String uuid, int version) throws Exception {
        Files.write(new File(iconsDir, uuid + ".bundled").toPath(), Integer.toString(version).getBytes(StandardCharsets.UTF_8));
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

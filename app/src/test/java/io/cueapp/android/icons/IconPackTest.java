package io.cueapp.android.icons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
public class IconPackTest {
    @Test
    public void testSuggestionsPutExactThenContainingMatchesFirst() throws JSONException {
        JSONArray icons = new JSONArray();
        for (String name : new String[] { "Steam", "Steam Guard", "Steamworks" }) {
            icons.put(new JSONObject()
                    .put("name", name)
                    .put("filename", name + ".svg")
                    .put("issuer", new JSONArray().put(name.toLowerCase())));
        }
        IconPack pack = IconPack.fromJson(new JSONObject()
                .put("uuid", "22222222-2222-2222-2222-222222222222")
                .put("name", "Test Pack")
                .put("version", 1)
                .put("icons", icons));

        // the same order as IconPack.suggestions(for:) in apple/Packages/CueVault
        assertEquals(Arrays.asList("Steam", "Steamworks", "Steam Guard"), names(pack.getSuggestedIcons("steam")));
        assertEquals(Arrays.asList("Steam Guard", "Steam"), names(pack.getSuggestedIcons("STEAM GUARD")));
        assertEquals(Arrays.asList("Steamworks", "Steam Guard", "Steam"), names(pack.getSuggestedIcons("stea")));
    }

    @Test
    public void testContainedMatchesOnlyAsWholeWords() throws JSONException {
        JSONArray icons = new JSONArray();
        for (String name : new String[] { "C", "ROS", "GitHub", "QQ" }) {
            icons.put(new JSONObject()
                    .put("name", name)
                    .put("filename", name + ".svg")
                    .put("issuer", new JSONArray().put(name)));
        }
        IconPack pack = IconPack.fromJson(new JSONObject()
                .put("uuid", "33333333-3333-3333-3333-333333333333")
                .put("name", "Test Pack")
                .put("version", 1)
                .put("icons", icons));

        // the same cases as IconTests.testContainedMatchesOnlyAsWholeWords on iOS
        assertTrue(pack.getSuggestedIcons("Microsoft").isEmpty());
        assertEquals(Arrays.asList("GitHub"), names(pack.getSuggestedIcons("GitHub Enterprise")));
        assertEquals(Arrays.asList("GitHub"), names(pack.getSuggestedIcons("work-github")));
        assertEquals(Arrays.asList("QQ"), names(pack.getSuggestedIcons("腾讯QQ")));
        assertTrue(pack.getSuggestedIcons("githubber").isEmpty());
    }

    @Test
    public void testContainingMatchesOnlyWhereAWordStarts() throws JSONException {
        JSONArray icons = new JSONArray();
        for (String name : new String[] { "Mutasibank", "Bank of America", "GitHub", "Google Pay" }) {
            icons.put(new JSONObject()
                    .put("name", name)
                    .put("filename", name + ".svg")
                    .put("issuer", new JSONArray().put(name)));
        }
        IconPack pack = IconPack.fromJson(new JSONObject()
                .put("uuid", "44444444-4444-4444-4444-444444444444")
                .put("name", "Test Pack")
                .put("version", 1)
                .put("icons", icons));

        // the same cases as IconTests.testContainingMatchesOnlyWhereAWordStarts on iOS
        assertEquals(Arrays.asList("Bank of America"), names(pack.getSuggestedIcons("Bank")));
        assertEquals(Arrays.asList("GitHub"), names(pack.getSuggestedIcons("git")));
        assertEquals(Arrays.asList("Google Pay"), names(pack.getSuggestedIcons("pay")));
        assertTrue(pack.getSuggestedIcons("hub").isEmpty());
    }

    private static List<String> names(List<IconPack.Icon> icons) {
        return icons.stream().map(IconPack.Icon::getName).collect(Collectors.toList());
    }

    @Test
    public void testMaliciousIconPackCannotOverwriteVaultFile() throws IOException, JSONException {
        final String VAULT_CONTENTS = "VAULT-CONTENTS";
        final String MALICIOUS_FILENAME = "../../../cue.json";

        Context context = ApplicationProvider.getApplicationContext();
        File vaultFile = new File(context.getFilesDir(), "cue.json");
        Files.write(vaultFile.toPath(), VAULT_CONTENTS.getBytes(StandardCharsets.UTF_8));

        JSONObject iconJson = new JSONObject();
        iconJson.put("filename", MALICIOUS_FILENAME);
        iconJson.put("issuer", new JSONArray().put("Test"));

        JSONObject packJson = new JSONObject();
        packJson.put("uuid", "11111111-1111-1111-1111-111111111111");
        packJson.put("name", "Test Pack");
        packJson.put("version", 1);
        packJson.put("icons", new JSONArray().put(iconJson));

        IconPack iconPack = IconPack.fromJson(packJson);
        assertEquals(MALICIOUS_FILENAME, iconPack.getIcons().get(0).getRelativeFilename());

        File zipFile = File.createTempFile("iconpack", ".zip");
        zipFile.delete();
        try (ZipFile zip = new ZipFile(zipFile)) {
            addEntry(zip, "pack.json", packJson.toString());
            addEntry(zip, MALICIOUS_FILENAME, "MALICIOUS-VAULT-CONTENTS");
        }

        IconPackManager manager = new IconPackManager(context);
        IconPackException importError = null;
        try {
            manager.importPack(zipFile);
        } catch (IconPackException e) {
            importError = e;
        }

        String contentsAfter = new String(Files.readAllBytes(vaultFile.toPath()), StandardCharsets.UTF_8);
        assertEquals("Vault file must not be overwritten by icon pack import", VAULT_CONTENTS, contentsAfter);
        assertNotNull("Expected the malicious icon pack import to be rejected", importError);
    }

    private static void addEntry(ZipFile zip, String nameInZip, String content) throws ZipException {
        ZipParameters params = new ZipParameters();
        params.setFileNameInZip(nameInZip);
        zip.addStream(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), params);
    }
}

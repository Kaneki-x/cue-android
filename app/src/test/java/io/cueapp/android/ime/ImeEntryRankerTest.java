package io.cueapp.android.ime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.cueapp.android.otp.TotpInfo;
import io.cueapp.android.vault.VaultEntry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
public class ImeEntryRankerTest {
    @Test
    public void testDomainLabel() {
        assertEquals("github", ImeEntryRanker.getDomainLabel("github.com"));
        assertEquals("example", ImeEntryRanker.getDomainLabel("www.example.co.uk"));
        assertEquals("example", ImeEntryRanker.getDomainLabel("example.com.cn"));
        assertEquals("google", ImeEntryRanker.getDomainLabel("accounts.google.com"));
        assertEquals("github", ImeEntryRanker.getDomainLabel(" https://GitHub.com/login "));
        assertEquals("taobao", ImeEntryRanker.getDomainLabel("www.taobao.com:443"));
        assertNull(ImeEntryRanker.getDomainLabel(null));
        assertNull(ImeEntryRanker.getDomainLabel("qq.com"));
        assertNull(ImeEntryRanker.getDomainLabel("com"));
    }

    @Test
    public void testWebsiteMatchesTheApp() throws Exception {
        VaultEntry entry = entry("Work", "kaneki");
        Set<String> github = tokens("github");
        assertFalse(ImeEntryRanker.matches(entry, github));

        entry.setDomain("github.com");
        assertTrue(ImeEntryRanker.matches(entry, github));
        assertFalse(ImeEntryRanker.matches(entry, tokens("gitlab")));

        entry.setDomain("www.example.co.uk");
        assertTrue(ImeEntryRanker.matches(entry, tokens("example")));
    }

    @Test
    public void testTwoCharacterChineseNamesMatch() throws Exception {
        assertTrue(ImeEntryRanker.isLongEnough("微信"));
        assertTrue(ImeEntryRanker.isLongEnough("淘宝"));
        assertFalse(ImeEntryRanker.isLongEnough("qq"));
        assertFalse(ImeEntryRanker.isLongEnough("京"));
        assertTrue(ImeEntryRanker.isLongEnough("abc"));

        // the label of the app as one word, as the IME splits it
        assertTrue(ImeEntryRanker.matches(entry("淘宝", ""), tokens("手机淘宝")));
        assertTrue(ImeEntryRanker.matches(entry("京东", ""), tokens("京东")));
        assertTrue(ImeEntryRanker.matches(entry("微信", "me"), tokens("微信")));
        assertFalse(ImeEntryRanker.matches(entry("QQ", ""), tokens("qqmusic")));
    }

    @Test
    public void testSuggestedEntriesComeFirst() throws Exception {
        VaultEntry github = entry("Work", "a");
        github.setDomain("github.com");
        VaultEntry pinned = entry("Bank", "b");
        pinned.setIsFavorite(true);
        VaultEntry other = entry("Mail", "c");

        List<ImeEntryRanker.Ranked> ranked = ImeEntryRanker.rank(Arrays.asList(other, pinned, github), tokens("github"), Collections.emptyMap());
        assertEquals(Arrays.asList(github, pinned, other), ranked.stream().map(ImeEntryRanker.Ranked::getEntry).collect(Collectors.toList()));
        assertTrue(ranked.get(0).isSuggested());
        assertFalse(ranked.get(1).isSuggested());
    }

    private static VaultEntry entry(String issuer, String name) throws Exception {
        return new VaultEntry(new TotpInfo(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }), name, issuer);
    }

    private static Set<String> tokens(String... tokens) {
        return new HashSet<>(Arrays.asList(tokens));
    }
}

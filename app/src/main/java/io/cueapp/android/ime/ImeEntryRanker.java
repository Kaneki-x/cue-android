package io.cueapp.android.ime;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.cueapp.android.vault.VaultEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orders vault entries for the input method: entries that look like they belong to the
 * app that is currently asking for input come first, then favorites, then everything
 * else, each group sorted by how often they were used. An entry belongs to the app when its
 * issuer, its name or the main label of its website (github.com: "github") matches a word of
 * the app's package name or label.
 */
public class ImeEntryRanker {
    private static final Set<String> IGNORED_TOKENS = new HashSet<>(Arrays.asList(
            "com", "org", "net", "io", "co", "de", "cn", "uk", "app", "apps", "android", "mobile",
            "client", "www", "the", "inc", "ltd", "llc", "official", "beta", "free", "pro", "lite",
            "for", "and"
    ));

    // the second level of country domains such as example.co.uk or example.com.cn
    private static final Set<String> SECOND_LEVEL_LABELS = new HashSet<>(Arrays.asList(
            "co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go"
    ));

    private static final String[] BROWSER_MARKERS = {
            "chrome", "chromium", "firefox", "browser", "brave", "opera", "vivaldi", "duckduckgo",
            "com.microsoft.emmx", "org.mozilla", "kiwibrowser", "com.sec.android.app.sbrowser"
    };

    private ImeEntryRanker() {

    }

    public static class Ranked {
        private final VaultEntry _entry;
        private final boolean _suggested;

        Ranked(VaultEntry entry, boolean suggested) {
            _entry = entry;
            _suggested = suggested;
        }

        public VaultEntry getEntry() {
            return _entry;
        }

        public boolean isSuggested() {
            return _suggested;
        }
    }

    /**
     * Derives search tokens from the package that currently has input focus. Returns an
     * empty set for browsers, because the package name says nothing about the website.
     */
    @NonNull
    public static Set<String> getCallerTokens(Context context, @Nullable String packageName) {
        Set<String> tokens = new HashSet<>();
        if (packageName == null || packageName.equals(context.getPackageName())) {
            return tokens;
        }

        String lowerPackage = packageName.toLowerCase(Locale.ROOT);
        for (String marker : BROWSER_MARKERS) {
            if (lowerPackage.contains(marker)) {
                return tokens;
            }
        }

        for (String part : lowerPackage.split("\\.")) {
            addToken(tokens, part);
        }

        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            if (label != null) {
                for (String part : label.toString().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                    addToken(tokens, part);
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // the package may not be visible to us, the package name tokens will have to do
        }

        return tokens;
    }

    private static void addToken(Set<String> tokens, String token) {
        if (isLongEnough(token) && !IGNORED_TOKENS.contains(token)) {
            tokens.add(token);
        }
    }

    /**
     * Whether a word is long enough to match on: three letters, or two where it has letters
     * outside ASCII, so that two-character Chinese names (微信, 淘宝, 京东) match too.
     */
    static boolean isLongEnough(String token) {
        if (token.length() >= 3) {
            return true;
        }
        if (token.length() < 2) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) > 0x7F) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(VaultEntry entry, Set<String> callerTokens) {
        if (callerTokens.isEmpty()) {
            return false;
        }

        String issuer = entry.getIssuer().toLowerCase(Locale.ROOT).trim();
        String name = entry.getName().toLowerCase(Locale.ROOT).trim();
        String domainLabel = getDomainLabel(entry.getDomain());
        for (String token : callerTokens) {
            if (!issuer.isEmpty() && (issuer.contains(token) || (isLongEnough(issuer) && token.contains(issuer)))) {
                return true;
            }
            if (!name.isEmpty() && name.contains(token)) {
                return true;
            }
            if (domainLabel != null && domainLabel.equals(token)) {
                return true;
            }
        }

        return false;
    }

    /**
     * The label a website is known by, matched against the app's words: "github" for
     * github.com, "example" for www.example.co.uk. Null when there is none to match on.
     */
    @Nullable
    static String getDomainLabel(@Nullable String domain) {
        if (domain == null) {
            return null;
        }

        String host = domain.trim().toLowerCase(Locale.ROOT);
        // tolerate a pasted address: drop the scheme, the path and a port
        int scheme = host.indexOf("://");
        if (scheme != -1) {
            host = host.substring(scheme + 3);
        }
        int end = host.length();
        for (char c : new char[] { '/', ':', '?', '#' }) {
            int i = host.indexOf(c);
            if (i != -1) {
                end = Math.min(end, i);
            }
        }
        host = host.substring(0, end);

        List<String> labels = new ArrayList<>();
        for (String label : host.split("\\.")) {
            if (!label.isEmpty()) {
                labels.add(label);
            }
        }
        if (!labels.isEmpty() && labels.get(0).equals("www")) {
            labels.remove(0);
        }
        // drop the top-level domain, and a second level such as "co" in example.co.uk
        if (labels.size() > 1) {
            labels.remove(labels.size() - 1);
        }
        if (labels.size() > 1 && SECOND_LEVEL_LABELS.contains(labels.get(labels.size() - 1))) {
            labels.remove(labels.size() - 1);
        }
        if (labels.isEmpty()) {
            return null;
        }

        String label = labels.get(labels.size() - 1);
        return isLongEnough(label) && !IGNORED_TOKENS.contains(label) ? label : null;
    }

    @NonNull
    public static List<Ranked> rank(Collection<VaultEntry> entries, Set<String> callerTokens, Map<UUID, Integer> usageCounts) {
        List<Ranked> ranked = new ArrayList<>(entries.size());
        for (VaultEntry entry : entries) {
            ranked.add(new Ranked(entry, matches(entry, callerTokens)));
        }

        Comparator<Ranked> comparator = Comparator
                .comparing((Ranked r) -> !r.isSuggested())
                .thenComparing(r -> !r.getEntry().isFavorite())
                .thenComparing(r -> -usageCountOf(usageCounts, r.getEntry()))
                .thenComparing(r -> getTitle(r.getEntry()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.getEntry().getName(), String.CASE_INSENSITIVE_ORDER);
        Collections.sort(ranked, comparator);
        return ranked;
    }

    /**
     * The text an entry is primarily known by: its issuer, or its name if there is no issuer.
     */
    public static String getTitle(VaultEntry entry) {
        String issuer = entry.getIssuer().trim();
        return issuer.isEmpty() ? entry.getName().trim() : issuer;
    }

    private static int usageCountOf(Map<UUID, Integer> usageCounts, VaultEntry entry) {
        Integer count = usageCounts.get(entry.getUUID());
        return count != null ? count : 0;
    }
}

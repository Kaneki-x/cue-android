package io.cueapp.android;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import io.cueapp.android.crypto.CryptoUtils;
import io.cueapp.android.crypto.SCryptParameters;
import io.cueapp.android.otp.OtpInfo;
import io.cueapp.android.ui.views.EntryHolder;
import io.cueapp.android.vault.VaultEntry;
import io.cueapp.android.vault.VaultFileCredentials;
import io.cueapp.android.vault.VaultManager;
import io.cueapp.android.vault.VaultRepository;
import io.cueapp.android.vault.VaultRepositoryException;
import io.cueapp.android.vault.slots.PasswordSlot;
import io.cueapp.android.vault.slots.SlotException;
import io.cueapp.android.vectors.VaultEntries;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;

import java.lang.reflect.InvocationTargetException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;

public abstract class CueTest {
    public static final String VAULT_PASSWORD = "test";
    public static final String VAULT_PASSWORD_CHANGED = "test2";
    public static final String VAULT_BACKUP_PASSWORD = "something";
    public static final String VAULT_BACKUP_PASSWORD_CHANGED = "something2";

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Rule
    public final GrantPermissionRule permRule = getGrantPermissionRule();

    @Inject
    protected VaultManager _vaultManager;

    @Inject
    protected Preferences _prefs;

    @Before
    public void init() {
        hiltRule.inject();
    }

    private static GrantPermissionRule getGrantPermissionRule() {
        List<String> perms = new ArrayList<>();
        // NOTE: Disabled for now. See issue: #1047
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }*/
        return GrantPermissionRule.grant(perms.toArray(new String[0]));
    }

    protected CueApplicationBase getApp() {
        return (CueApplicationBase) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    }

    protected VaultRepository initEncryptedVault() {
        VaultFileCredentials creds = generateCredentials();
        return initVault(creds, VaultEntries.get());
    }

    protected VaultRepository initEmptyEncryptedVault() {
        VaultFileCredentials creds = generateCredentials();
        return initVault(creds, null);
    }

    protected VaultRepository initPlainVault() {
        return initVault(null, VaultEntries.get());
    }

    protected VaultRepository initEmptyPlainVault() {
        return initVault(null, null);
    }

    private VaultRepository initVault(@Nullable VaultFileCredentials creds, @Nullable List<VaultEntry> entries) {
        VaultRepository vault;
        try {
            vault = _vaultManager.initNew(creds);
        } catch (VaultRepositoryException e) {
            throw new RuntimeException(e);
        }

        if (entries != null) {
            for (VaultEntry entry : entries) {
                _vaultManager.getVault().addEntry(entry);
            }
        }

        try {
            _vaultManager.save();
        } catch (VaultRepositoryException e) {
            throw new RuntimeException(e);
        }

        _prefs.setIntroDone(true);
        return vault;
    }

    protected VaultFileCredentials generateCredentials() {
        PasswordSlot slot = new PasswordSlot();
        byte[] salt = CryptoUtils.generateSalt();
        SCryptParameters scryptParams = new SCryptParameters(
                CryptoUtils.CRYPTO_SCRYPT_N,
                CryptoUtils.CRYPTO_SCRYPT_r,
                CryptoUtils.CRYPTO_SCRYPT_p,
                salt
        );

        VaultFileCredentials creds = new VaultFileCredentials();
        try {
            SecretKey key = slot.deriveKey(VAULT_PASSWORD.toCharArray(), scryptParams);
            slot.setKey(creds.getKey(), CryptoUtils.createEncryptCipher(key));
        } catch (NoSuchAlgorithmException
                | InvalidKeyException
                | InvalidAlgorithmParameterException
                | NoSuchPaddingException
                | SlotException e) {
            throw new RuntimeException(e);
        }

        creds.getSlots().add(slot);
        return creds;
    }

    protected static <T extends OtpInfo> VaultEntry generateEntry(Class<T> type, String name, String issuer) {
        return generateEntry(type, name, issuer, 20);
    }

    protected static <T extends OtpInfo> VaultEntry generateEntry(Class<T> type, String name, String issuer, int secretLength) {
        byte[] secret = CryptoUtils.generateRandomBytes(secretLength);

        OtpInfo info;
        try {
            info = type.getConstructor(byte[].class).newInstance(secret);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        return new VaultEntry(info, name, issuer);
    }

    // source: https://stackoverflow.com/a/30338665
    protected static ViewAction clickChildViewWithId(final int id) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return null;
            }

            @Override
            public String getDescription() {
                return "Click on a child view with specified id.";
            }

            @Override
            public void perform(UiController uiController, View view) {
                View v = view.findViewById(id);
                v.performClick();
            }
        };
    }

    @NonNull
    protected static Matcher<RecyclerView.ViewHolder> withOtpType(Class<? extends OtpInfo> otpClass) {
        return new BoundedMatcher<RecyclerView.ViewHolder, EntryHolder>(EntryHolder.class) {
            @Override
            public boolean matchesSafely(EntryHolder holder) {
                return holder != null
                        && holder.getEntry() != null
                        && holder.getEntry().getInfo().getClass().equals(otpClass);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("with otp type '%s'", otpClass.getSimpleName()));
            }
        };
    }
}

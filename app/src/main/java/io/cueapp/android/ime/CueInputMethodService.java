package io.cueapp.android.ime;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import io.cueapp.android.Preferences;
import io.cueapp.android.R;
import io.cueapp.android.VibrationPatterns;
import io.cueapp.android.helpers.CodeFormatHelper;
import io.cueapp.android.helpers.ThemeHelper;
import io.cueapp.android.helpers.VibrationHelper;
import io.cueapp.android.otp.HotpInfo;
import io.cueapp.android.otp.OtpInfo;
import io.cueapp.android.otp.OtpInfoException;
import io.cueapp.android.ui.AuthActivity;
import io.cueapp.android.ui.MainActivity;
import io.cueapp.android.vault.VaultEntry;
import io.cueapp.android.vault.VaultManager;
import io.cueapp.android.vault.VaultRepositoryException;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * A keyboard that doesn't type: it shows the entries of the vault and inserts the code of
 * the entry the user taps into the focused text field, then switches back to the keyboard
 * that was active before.
 */
@AndroidEntryPoint
public class CueInputMethodService extends InputMethodService
        implements VaultManager.LockListener, ImeEntryAdapter.Listener {
    @Inject
    VaultManager _vaultManager;

    @Inject
    Preferences _prefs;

    private VibrationHelper _vibrationHelper;

    private String _themeKey;
    private View _root;
    private TextView _headerHint;
    private View _openAppButton;
    private RecyclerView _list;
    private View _stateView;
    private ImageView _stateIcon;
    private TextView _stateText;
    private MaterialButton _stateButton;
    private ChipGroup _chips;
    private View _chipsRow;
    private ImeEntryAdapter _adapter;

    private String _currentPackage;
    private String _chipsKey;

    @Override
    public void onCreate() {
        super.onCreate();
        _vibrationHelper = new VibrationHelper(this);
        _vaultManager.registerLockListener(this);
    }

    @Override
    public void onDestroy() {
        _vaultManager.unregisterLockListener(this);
        _vaultManager.setInputMethodVisible(false);
        super.onDestroy();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public View onCreateInputView() {
        Context context = ThemeHelper.createThemedContext(this, _prefs);
        _themeKey = ThemeHelper.getThemeKey(this, _prefs);

        _root = LayoutInflater.from(context).inflate(R.layout.ime_picker, null);
        _headerHint = _root.findViewById(R.id.header_hint);
        _list = _root.findViewById(R.id.list);
        _stateView = _root.findViewById(R.id.state_view);
        _stateIcon = _root.findViewById(R.id.state_icon);
        _stateText = _root.findViewById(R.id.state_text);
        _stateButton = _root.findViewById(R.id.state_button);
        _chips = _root.findViewById(R.id.chips);
        _chipsRow = _root.findViewById(R.id.chips_row);
        _chipsKey = null;

        _adapter = new ImeEntryAdapter(Glide.with(getApplicationContext()), this);
        // entries are keys: two columns of small tiles (the iOS keyboard draws the same)
        _list.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(context, 2));
        _list.setAdapter(_adapter);

        _openAppButton = _root.findViewById(R.id.btn_open_app);
        _openAppButton.setOnClickListener(v -> openApp());
        _root.findViewById(R.id.btn_switch_keyboard).setOnClickListener(v -> switchToPreviousKeyboard());
        _chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            Chip chip = group.findViewById(checkedIds.get(0));
            String filter = chip != null ? (String) chip.getTag() : null;
            if (!Objects.equals(filter, _adapter.getFilter())) {
                _adapter.setFilter(filter);
                _list.scrollToPosition(0);
                updateEmptyState();
            }
        });

        // The IME framework measures the input view with WRAP_CONTENT, which would let
        // the list grow to its full length. Pin the panel to a keyboard-like height and
        // keep it clear of the (gesture) navigation bar.
        FrameLayout wrapper = new FrameLayout(context);
        wrapper.setBackground(AppCompatResources.getDrawable(context, R.drawable.ime_panel_background));
        Window window = getWindow() != null ? getWindow().getWindow() : null;
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        int height = getResources().getDimensionPixelSize(R.dimen.ime_panel_height);
        wrapper.addView(_root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        ViewCompat.setOnApplyWindowInsetsListener(wrapper, (v, insets) -> {
            // With gesture navigation the system draws the collapse and switch-keyboard
            // buttons in a bar across the bottom of the keyboard, taller than the gesture
            // inset it reports; keep the keys above that bar, as Gboard does.
            int bottom = Math.max(insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom, getImeNavigationBarHeight());
            v.setPadding(0, 0, 0, bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        return wrapper;
    }

    /**
     * The height of the bar the system draws under a keyboard for its back and switcher
     * buttons (Android 13+), the framework's navigation_bar_frame_height, 48dp when it
     * cannot be read.
     */
    private int getImeNavigationBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_frame_height", "dimen", "android");
        if (id != 0) {
            return getResources().getDimensionPixelSize(id);
        }
        return Math.round(48 * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        if (_root == null || !Objects.equals(_themeKey, ThemeHelper.getThemeKey(this, _prefs))) {
            setInputView(onCreateInputView());
        }

        updateSecureFlag();

        String packageName = info != null ? info.packageName : null;
        if (!restarting || !Objects.equals(packageName, _currentPackage)) {
            _currentPackage = packageName;
            _adapter.setFilter(null);
            _chipsKey = null;
        }

        refresh();
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        _vaultManager.setInputMethodVisible(true);
        if (_root != null) {
            // the vault may have been unlocked through AuthActivity while we were hidden
            refresh();
        }
    }

    @Override
    public void onWindowHidden() {
        super.onWindowHidden();
        _vaultManager.setInputMethodVisible(false);
    }

    @Override
    public void onLocked(boolean userInitiated) {
        if (_root != null) {
            refresh();
        }
    }

    private void updateSecureFlag() {
        Window window = getWindow() != null ? getWindow().getWindow() : null;
        if (window == null) {
            return;
        }

        if (_prefs.isSecureScreenEnabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void refresh() {
        boolean deviceLocked = isDeviceLocked();
        _openAppButton.setVisibility(deviceLocked ? View.GONE : View.VISIBLE);
        if (deviceLocked) {
            // on the lock screen: nothing from the vault, and no way into the app from here
            _adapter.setEntries(Collections.emptyList());
            showState(R.drawable.ic_outline_lock_24, R.string.ime_device_locked, R.string.ime_switch_keyboard, v -> switchToPreviousKeyboard());
            return;
        }

        if (_currentPackage != null && _currentPackage.equals(getPackageName())) {
            // We're attached to one of our own text fields, most likely the password field
            // of AuthActivity. Our codes are of no use there, but a real keyboard is.
            showState(R.drawable.ic_outline_keyboard_24, R.string.ime_own_app_hint, R.string.ime_switch_keyboard, v -> switchToPreviousKeyboard());
            return;
        }

        if (_vaultManager.isVaultInitNeeded()) {
            showState(R.drawable.ic_cue_logo_small, R.string.ime_setup_required, R.string.ime_open_app, v -> openApp());
            return;
        }

        if (!_vaultManager.loadIfUnencrypted()) {
            _adapter.setEntries(Collections.emptyList());
            showState(R.drawable.ic_outline_lock_24, R.string.ime_vault_locked, R.string.unlock, v -> unlock());
            return;
        }

        _adapter.setCodeGrouping(_prefs.getCodeGroupSize());
        _adapter.setHideCodes(_prefs.isTapToRevealEnabled());
        _adapter.setShowIcons(_prefs.isIconVisible());

        Set<String> callerTokens = _prefs.isImeSuggestByAppEnabled()
                ? ImeEntryRanker.getCallerTokens(this, _currentPackage)
                : Collections.emptySet();
        List<ImeEntryRanker.Ranked> ranked = ImeEntryRanker.rank(
                _vaultManager.getVault().getEntries(), callerTokens, _prefs.getUsageCounts());
        _adapter.setEntries(ranked);

        boolean anySuggested = !ranked.isEmpty() && ranked.get(0).isSuggested();
        String callerLabel = anySuggested ? getCallerLabel() : "";
        _headerHint.setText(callerLabel.isEmpty() ? getString(R.string.ime_hint_choose) : callerLabel);

        buildChips();
        showList();
        updateEmptyState();
    }

    /**
     * Whether the keyboard is shown on the lock screen (or another place where the device
     * still has to be unlocked), where the vault must not be read or offered.
     */
    private boolean isDeviceLocked() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return keyguardManager != null && keyguardManager.isDeviceLocked();
    }

    private String getCallerLabel() {
        if (_currentPackage == null) {
            return "";
        }

        try {
            CharSequence label = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(_currentPackage, 0));
            return getString(R.string.ime_hint_suggested_for, label);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 全部, 置顶 and the groups in use, as in the main list (the iOS keyboard shows the same).
     * The row is hidden when 全部 would be the only chip.
     */
    private void buildChips() {
        boolean hasFavorites = _adapter.hasFavorites();
        Set<java.util.UUID> used = _adapter.getUsedGroups();
        List<io.cueapp.android.vault.VaultGroup> groups = new java.util.ArrayList<>();
        for (io.cueapp.android.vault.VaultGroup group : _vaultManager.getVault().getGroups()) {
            if (used.contains(group.getUUID())) {
                groups.add(group);
            }
        }
        StringBuilder key = new StringBuilder(String.valueOf(hasFavorites));
        for (io.cueapp.android.vault.VaultGroup group : groups) {
            key.append('/').append(group.getUUID()).append(group.getName());
        }
        _chipsRow.setVisibility(hasFavorites || !groups.isEmpty() ? View.VISIBLE : View.GONE);
        if (key.toString().equals(_chipsKey)) {
            return;
        }
        _chipsKey = key.toString();

        String filter = _adapter.getFilter();
        _chips.removeAllViews();
        addChip(null, getString(R.string.all), filter == null);
        if (hasFavorites) {
            addChip(ImeEntryAdapter.FILTER_FAVORITES, getString(R.string.ime_filter_favorites), ImeEntryAdapter.FILTER_FAVORITES.equals(filter));
        }
        for (io.cueapp.android.vault.VaultGroup group : groups) {
            String tag = ImeEntryAdapter.FILTER_GROUP_PREFIX + group.getUUID();
            addChip(tag, group.getName(), tag.equals(filter));
        }
        if (_chips.getCheckedChipIds().isEmpty()) {
            // the filtered group or the last pinned entry is gone
            _adapter.setFilter(null);
            _chips.check(_chips.getChildAt(0).getId());
        }
    }

    private Chip addChip(@Nullable String filter, String text, boolean checked) {
        Chip chip = (Chip) LayoutInflater.from(_chips.getContext()).inflate(R.layout.ime_chip, _chips, false);
        chip.setId(View.generateViewId());
        chip.setTag(filter);
        chip.setText(text);
        _chips.addView(chip);
        if (checked) {
            _chips.check(chip.getId());
        }
        return chip;
    }

    private void showList() {
        _stateView.setVisibility(View.GONE);
        _list.setVisibility(View.VISIBLE);
    }

    private void updateEmptyState() {
        if (_adapter.getItemCount() > 0) {
            _stateView.setVisibility(View.GONE);
            _list.setVisibility(View.VISIBLE);
            return;
        }

        _list.setVisibility(View.GONE);
        _stateView.setVisibility(View.VISIBLE);
        _stateIcon.setImageResource(R.drawable.ic_cue_logo_small);
        _stateIcon.setImageTintList(null);
        _stateText.setText(_adapter.hasEntries() ? R.string.ime_no_matches : R.string.ime_no_entries);
        _stateButton.setVisibility(View.GONE);
    }

    private void showState(int iconRes, int textRes, int buttonRes, View.OnClickListener listener) {
        _list.setVisibility(View.GONE);
        _chipsRow.setVisibility(View.GONE);
        _headerHint.setText("");
        _stateView.setVisibility(View.VISIBLE);
        _stateIcon.setImageResource(iconRes);
        _stateIcon.setImageTintList(iconRes == R.drawable.ic_cue_logo_small ? null
                : android.content.res.ColorStateList.valueOf(MaterialColors.getColor(_stateIcon, com.google.android.material.R.attr.colorOnSurfaceVariant)));
        _stateText.setText(textRes);
        _stateButton.setVisibility(View.VISIBLE);
        _stateButton.setText(buttonRes);
        _stateButton.setOnClickListener(listener);
    }

    private void unlock() {
        startActivity(AuthActivity.createExternalIntent(this));
    }

    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onEntryClick(VaultEntry entry) {
        if (isDeviceLocked() || !_vaultManager.isVaultLoaded()) {
            refresh();
            return;
        }

        OtpInfo info = entry.getInfo();
        String code;
        try {
            code = info.getOtp();
        } catch (OtpInfoException e) {
            Toast.makeText(this, R.string.error_all_caps, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!insertCode(code)) {
            return;
        }

        recordUsage(entry);
        _vibrationHelper.vibratePattern(this, VibrationPatterns.REFRESH_CODE);

        if (info instanceof HotpInfo) {
            // the code has been consumed, move on to the next counter value like the
            // refresh button in the main entry list does
            try {
                ((HotpInfo) info).incrementCounter();
                _vaultManager.saveAndBackup();
            } catch (OtpInfoException | VaultRepositoryException e) {
                e.printStackTrace();
                Toast.makeText(this, R.string.saving_error, Toast.LENGTH_LONG).show();
            }
        }

        if (_prefs.isImeSwitchBackEnabled()) {
            switchToPreviousKeyboard();
        } else {
            _adapter.markInserted(entry);
        }
    }

    /**
     * Inserts the code into the focused field. Numeric codes are sent as individual key
     * presses, so that forms with one box per digit (which move the focus after every
     * digit) receive all of them. Other codes are committed as text.
     */
    private boolean insertCode(String code) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }

        if (CodeFormatHelper.isNumeric(code) && !_prefs.isImeInsertAsTextEnabled()) {
            for (int i = 0; i < code.length(); i++) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_0 + (code.charAt(i) - '0'));
            }
        } else {
            ic.commitText(code, 1);
        }

        return true;
    }

    private void recordUsage(VaultEntry entry) {
        UUID uuid = entry.getUUID();

        Map<UUID, Integer> usageCounts = _prefs.getUsageCounts();
        Integer count = usageCounts.get(uuid);
        usageCounts.put(uuid, count != null ? count + 1 : 1);
        _prefs.setUsageCount(usageCounts);

        Map<UUID, Long> timestamps = _prefs.getLastUsedTimestamps();
        timestamps.put(uuid, new Date().getTime());
        _prefs.setLastUsedTimestamps(timestamps);
    }

    private void switchToPreviousKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!switchToPreviousInputMethod()) {
                requestHideSelf(0);
            }
            return;
        }

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        Window window = getWindow() != null ? getWindow().getWindow() : null;
        IBinder token = window != null ? window.getAttributes().token : null;
        if (imm == null || token == null || !imm.switchToLastInputMethod(token)) {
            requestHideSelf(0);
        }
    }
}

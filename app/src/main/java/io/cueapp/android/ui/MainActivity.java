package io.cueapp.android.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.res.ColorStateList;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import io.cueapp.android.GroupPlaceholderType;
import io.cueapp.android.Preferences;
import io.cueapp.android.R;
import io.cueapp.android.SortCategory;
import io.cueapp.android.helpers.SimpleTextWatcher;
import io.cueapp.android.helpers.BitmapHelper;
import io.cueapp.android.helpers.DropdownHelper;
import io.cueapp.android.helpers.FabMenuHelper;
import io.cueapp.android.helpers.FabScrollHelper;
import io.cueapp.android.helpers.PermissionHelper;
import io.cueapp.android.helpers.ViewHelper;
import io.cueapp.android.icons.IconType;
import io.cueapp.android.otp.GoogleAuthInfo;
import io.cueapp.android.otp.GoogleAuthInfoException;
import io.cueapp.android.otp.OtpInfoException;
import io.cueapp.android.ui.dialogs.Dialogs;
import io.cueapp.android.ui.fragments.preferences.BackupsPreferencesFragment;
import io.cueapp.android.ui.fragments.preferences.IntegrationsPreferencesFragment;
import io.cueapp.android.ui.fragments.preferences.PreferencesFragment;
import io.cueapp.android.ui.models.ErrorCardInfo;
import io.cueapp.android.ui.models.VaultGroupModel;
import io.cueapp.android.ui.tasks.IconOptimizationTask;
import io.cueapp.android.ui.tasks.QrDecodeTask;
import io.cueapp.android.ui.views.EntryListView;
import io.cueapp.android.util.ClipboardUtils;
import io.cueapp.android.util.TimeUtils;
import io.cueapp.android.util.UUIDMap;
import io.cueapp.android.vault.VaultEntry;
import io.cueapp.android.vault.VaultEntryIcon;
import io.cueapp.android.vault.VaultFile;
import io.cueapp.android.vault.VaultGroup;
import io.cueapp.android.vault.VaultRepository;
import io.cueapp.android.vault.VaultRepositoryException;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class MainActivity extends CueActivity implements EntryListView.Listener {
    // Permission request codes
    private static final int CODE_PERM_CAMERA = 0;

    // what to open once the vault is unlocked (doShortcutActions)
    public static final String EXTRA_ACTION = "action";
    // the keyboard's settings, from the system's keyboard settings (ImeSettingsActivity)
    public static final String ACTION_IME_SETTINGS = "ime_settings";

    private boolean _loaded;
    private boolean _isRecreated;
    private boolean _isDPadPressed;
    private boolean _isDoingIntro;
    private boolean _isAuthenticating;


    private List<VaultEntry> _selectedEntries;

    private Menu _menu;
    // Cue: the search field under the toolbar, as on iOS (was a SearchView in the toolbar)
    private View _searchBar;
    private EditText _searchField;
    private View _searchClear;
    private EntryListView _entryListView;

    private Collection<VaultGroup> _groups;
    private ChipGroup _groupChip;
    private Set<UUID> _groupFilter;
    // set while the chips are checked to match the filter, so their listeners stay quiet
    private boolean _updatingGroupChips;
    private Set<UUID> _prefGroupFilter;

    private FabScrollHelper _fabScrollHelper;
    private FabMenuHelper _fabMenuHelper;

    private ActionMode _actionMode;
    private ActionMode.Callback _actionModeCallbacks = new ActionModeCallbacks();

    private LockBackPressHandler _lockBackPressHandler;
    private SearchViewBackPressHandler _searchViewBackPressHandler;
    private ActionModeBackPressHandler _actionModeBackPressHandler;
    private FabMenuBackPressHandler _fabMenuBackPressHandler;

    private final ActivityResultLauncher<Intent> authResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                _isAuthenticating = false;
                if (activityResult.getResultCode() == RESULT_OK) {
                    onDecryptResult();
                }
            });

    private final ActivityResultLauncher<Intent> introResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                _isDoingIntro = false;
                if (activityResult.getResultCode() == RESULT_OK) {
                    onIntroResult();
                }
            });

    private final ActivityResultLauncher<Intent> scanResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                if (activityResult.getResultCode() != RESULT_OK || activityResult.getData() == null) {
                    return;
                }
                onScanResult(activityResult.getData());
            });

    private final ActivityResultLauncher<Intent> assignIconsResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                if (activityResult.getResultCode() != RESULT_OK || activityResult.getData() == null) {
                    return;
                }
                onAssignIconsResult();
            });

    private final ActivityResultLauncher<Intent> preferenceResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> onPreferencesResult());

    private final ActivityResultLauncher<Intent> editEntryResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                if (activityResult.getResultCode() != RESULT_OK || activityResult.getData() == null) {
                    return;
                }
                onEditEntryResult();
            });

    private final ActivityResultLauncher<Intent> addEntryResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                if (activityResult.getResultCode() != RESULT_OK || activityResult.getData() == null) {
                    return;
                }
                onAddEntryResult(activityResult.getData());
            });

    private final ActivityResultLauncher<Intent> codeScanResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                if (activityResult.getResultCode() == RESULT_OK && activityResult.getData() != null) {
                    onScanImageResult(activityResult.getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        ViewHelper.setupAppBarInsets(findViewById(R.id.app_bar_layout));
        _loaded = false;
        _isDPadPressed = false;
        _isDoingIntro = false;
        _isAuthenticating = false;
        if (savedInstanceState != null) {
            _isRecreated = true;
            _isDoingIntro = savedInstanceState.getBoolean("isDoingIntro");
            _isAuthenticating = savedInstanceState.getBoolean("isAuthenticating");
        }

        _lockBackPressHandler = new LockBackPressHandler();
        getOnBackPressedDispatcher().addCallback(this, _lockBackPressHandler);
        _searchViewBackPressHandler = new SearchViewBackPressHandler();
        getOnBackPressedDispatcher().addCallback(this, _searchViewBackPressHandler);
        setUpSearchField();
        _actionModeBackPressHandler = new ActionModeBackPressHandler();
        getOnBackPressedDispatcher().addCallback(this, _actionModeBackPressHandler);
        _fabMenuBackPressHandler = new FabMenuBackPressHandler();
        getOnBackPressedDispatcher().addCallback(this, _fabMenuBackPressHandler);

        _entryListView = (EntryListView) getSupportFragmentManager().findFragmentById(R.id.key_profiles);
        _entryListView.setListener(this);
        _entryListView.setCodeGroupSize(_prefs.getCodeGroupSize());
        _entryListView.setAccountNamePosition(_prefs.getAccountNamePosition());
        _entryListView.setShowIcon(_prefs.isIconVisible());
        _entryListView.setShowExpirationState(_prefs.getShowExpirationState());
        _entryListView.setShowNextCode(_prefs.getShowNextCode());
        _entryListView.setShowExpirationCountdown(_prefs.getShowExpirationCountdown());
        _entryListView.setOnlyShowNecessaryAccountNames(_prefs.onlyShowNecessaryAccountNames());
        _entryListView.setHighlightEntry(_prefs.isEntryHighlightEnabled());
        _entryListView.setPauseFocused(_prefs.isPauseFocusedEnabled());
        _entryListView.setTapToReveal(_prefs.isTapToRevealEnabled());
        _entryListView.setTapToRevealTime(_prefs.getTapToRevealTime());
        _entryListView.setViewMode(_prefs.getCurrentViewMode());
        _entryListView.setSortCategory(_prefs.getCurrentSortCategory(), false);
        _entryListView.setCopyBehavior(_prefs.getCopyBehavior());
        _entryListView.setSearchBehaviorMask(_prefs.getSearchBehaviorMask());
        _prefGroupFilter = _prefs.getGroupFilter();

         View scrimOverlayLayout = LayoutInflater.from(this).inflate(R.layout.scrim_layout, null);
         View scrimOverlay = scrimOverlayLayout.findViewById(R.id.scrim);
         addContentView(scrimOverlayLayout, new ViewGroup.LayoutParams(
             ViewGroup.LayoutParams.MATCH_PARENT,
             ViewGroup.LayoutParams.MATCH_PARENT
         ));

         View fabMenuLayout = LayoutInflater.from(this).inflate(R.layout.fab_menu, null);
         addContentView(fabMenuLayout, new ViewGroup.LayoutParams(
             ViewGroup.LayoutParams.MATCH_PARENT,
             ViewGroup.LayoutParams.MATCH_PARENT
         ));

        ViewGroup menuItemsContainer = fabMenuLayout.findViewById(R.id.fab_menu_items_container);
        FloatingActionButton fab = fabMenuLayout.findViewById(R.id.fab);

        LinkedHashMap<View, Runnable> actions = new LinkedHashMap<>();
        actions.put(fabMenuLayout.findViewById(R.id.fab_menu_item_scan), this::startScanActivity);
        actions.put(fabMenuLayout.findViewById(R.id.fab_menu_item_scan_image), this::startScanImageActivity);
        actions.put(fabMenuLayout.findViewById(R.id.fab_menu_item_enter), this::startEditEntryActivity);

        _fabMenuHelper = new FabMenuHelper(scrimOverlay, menuItemsContainer, fab, actions);
        _fabMenuHelper.setOnFabMenuStateChangeListener(_fabMenuBackPressHandler::setEnabled);

        _groupChip = findViewById(R.id.groupChipGroup);
        _fabScrollHelper = new FabScrollHelper(fab);
        _selectedEntries = new ArrayList<>();
    }

    public void setGroups(Collection<VaultGroup> groups) {
        _groups = groups;
        _groupChip.setVisibility(_groups.isEmpty() ? View.GONE : View.VISIBLE);

        if (_prefGroupFilter != null) {
            Set<UUID> groupFilter = cleanGroupFilter(_prefGroupFilter);
            _prefGroupFilter = null;
            if (!groupFilter.isEmpty()) {
                _groupFilter = groupFilter;
                _entryListView.setGroupFilter(groupFilter);
            }
        } else if (_groupFilter != null) {
            Set<UUID> groupFilter = cleanGroupFilter(_groupFilter);
            if (!_groupFilter.equals(groupFilter)) {
                _groupFilter = groupFilter;
                _entryListView.setGroupFilter(groupFilter);
            }
        }

        _entryListView.setGroups(groups);
        initializeGroups();
    }

    /**
     * Cue: 全部, each group, 无分组, as on iOS (GroupChips). The filter is remembered as soon as
     * it changes, as on iOS, instead of through a 保存 chip; tapping the only selected chip
     * goes back to 全部.
     */
    private void initializeGroups() {
        _groupChip.removeAllViews();
        _groupChip.setSingleSelection(!_prefs.isGroupMultiselectEnabled());
        _groupChip.setSelectionRequired(false);

        addChipTo(_groupChip, new VaultGroupModel(this, GroupPlaceholderType.ALL));
        for (VaultGroup group : _groups) {
            addChipTo(_groupChip, new VaultGroupModel(group));
        }
        addChipTo(_groupChip, new VaultGroupModel(this, GroupPlaceholderType.NO_GROUP));
    }

    private void applyGroupFilter(Set<UUID> groupFilter) {
        _groupFilter = groupFilter;
        _updatingGroupChips = true;
        for (int i = 0; i < _groupChip.getChildCount(); i++) {
            Chip chip = (Chip) _groupChip.getChildAt(i);
            Object tag = chip.getTag();
            if (tag == GroupPlaceholderType.ALL) {
                chip.setChecked(groupFilter.isEmpty());
            } else if (tag == GroupPlaceholderType.NO_GROUP) {
                chip.setChecked(groupFilter.contains(null));
            } else if (tag instanceof VaultGroupModel) {
                chip.setChecked(groupFilter.contains(((VaultGroupModel) tag).getUUID()));
            }
        }
        _updatingGroupChips = false;

        _entryListView.setGroupFilter(groupFilter);
        onSaveGroupFilter(groupFilter);
    }

    private Set<UUID> cleanGroupFilter(Set<UUID> groupFilter) {
        Set<UUID> groupUuids = _groups.stream().map(UUIDMap.Value::getUUID).collect(Collectors.toSet());

        return groupFilter.stream()
                .filter(g -> g == null || groupUuids.contains(g))
                .collect(Collectors.toSet());
    }

    private void addChipTo(ChipGroup chipGroup, VaultGroupModel group) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.chip_group_filter, null, false);
        chip.setText(group.getName());
        chip.setCheckable(true);
        // a check on the selected chips, as on iOS, in the checked text colour
        chip.setCheckedIconVisible(true);
        chip.setCheckedIconTint(ColorStateList.valueOf(MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnPrimary)));
        chip.setChecked(_groupFilter != null && _groupFilter.contains(group.getUUID()));

        if (group.isPlaceholder()) {
            GroupPlaceholderType groupPlaceholderType = group.getPlaceholderType();
            chip.setTag(groupPlaceholderType);

            if (groupPlaceholderType == GroupPlaceholderType.ALL) {
                chip.setChecked(_groupFilter == null || _groupFilter.isEmpty());
            } else if (groupPlaceholderType == GroupPlaceholderType.NO_GROUP) {
                chip.setChecked(_groupFilter != null && _groupFilter.contains(null));
            }
        } else {
            chip.setTag(group);
        }

        chip.setOnCheckedChangeListener((button, isChecked) -> {
            if (_updatingGroupChips) {
                return;
            }
            if (_actionMode != null) {
                _actionMode.finish();
            }

            // 全部 clears the filter; anything else filters by the checked chips, and
            // unchecking the last one is 全部 again
            boolean all = button.getTag() == GroupPlaceholderType.ALL;
            applyGroupFilter(all ? new HashSet<>() : getGroupFilter(chipGroup));
        });

        chipGroup.addView(chip);
    }

    private static Set<UUID> getGroupFilter(ChipGroup chipGroup) {
        // a group's UUID, or null for 无分组; 全部 adds nothing
        Set<UUID> groupFilter = new HashSet<>();
        for (int id : chipGroup.getCheckedChipIds()) {
            Object tag = chipGroup.findViewById(id).getTag();
            if (tag instanceof VaultGroupModel) {
                groupFilter.add(((VaultGroupModel) tag).getUUID());
            } else if (tag == GroupPlaceholderType.NO_GROUP) {
                groupFilter.add(null);
            }
        }
        return groupFilter;
    }

    @Override
    protected void onDestroy() {
        _entryListView.setListener(null);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        Map<UUID, Integer> usageMap = _entryListView.getUsageCounts();
        if (usageMap != null) {
            _prefs.setUsageCount(usageMap);
        }

        Map<UUID, Long> lastUsedMap = _entryListView.getLastUsedTimestamps();
        if (lastUsedMap != null) {
            _prefs.setLastUsedTimestamps(lastUsedMap);
        }

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle instance) {
        super.onSaveInstanceState(instance);
        instance.putBoolean("isDoingIntro", _isDoingIntro);
        instance.putBoolean("isAuthenticating", _isAuthenticating);

        if (_groupFilter != null) {
            instance.putSerializable("prefGroupFilter", new HashSet<>(_groupFilter));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (!PermissionHelper.checkResults(grantResults)) {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show();
            return;
        }

        if (requestCode == CODE_PERM_CAMERA) {
            startScanActivity();
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        _isDPadPressed = isDPadKey(keyCode);
        return super.onKeyDown(keyCode, event);
    }

    private static boolean isDPadKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
    }

    @Override
    public void onEntryListTouch() {
        _isDPadPressed = false;

        if (_searchField.hasFocus()) {
            if (ViewCompat.getRootWindowInsets(findViewById(android.R.id.content).getRootView()).isVisible(WindowInsetsCompat.Type.ime())) {
                InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (inputMethodManager != null && getCurrentFocus() != null) {
                    inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                }
            }
        }
    }

    private void onPreferencesResult() {
        // refresh the entire entry list if needed
        if (_loaded) {
            recreate();
        }
    }

    private void startEditEntryActivity() {
        String clip = ClipboardUtils.readText(this);
        if (clip != null) {
            GoogleAuthInfo parsed;
            try {
                parsed = GoogleAuthInfo.parseUri(clip.trim());
                String message = getString(
                        R.string.import_from_clipboard_message,
                        parsed.getAccountName(),
                        parsed.getIssuer()
                );

                Dialogs.showSecureDialog(new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.import_from_clipboard_title)
                        .setMessage(message)
                        .setPositiveButton(R.string.yes, (dialog, which) -> startEditEntryActivityForNew(new VaultEntry(parsed)))
                        .setNegativeButton(R.string.no,  (dialog, which) -> startEditEntryActivityForManual())
                        .create());

                return;
            } catch (GoogleAuthInfoException e) {
                Log.i("EntryActivity", "Clipboard did not contain a valid otpauth URI", e);
            }
        }

        startEditEntryActivityForManual();
    }

    private void startEditEntryActivityForNew(VaultEntry entry) {
        Intent intent = new Intent(this, EditEntryActivity.class);
        intent.putExtra("newEntry", entry);
        intent.putExtra("isManual", false);
        addEntryResultLauncher.launch(intent);
    }

    private void startEditEntryActivityForManual() {
        Intent intent = new Intent(this, EditEntryActivity.class);
        intent.putExtra("newEntry", VaultEntry.getDefault());
        intent.putExtra("isManual", true);
        addEntryResultLauncher.launch(intent);
    }

    private void startEditEntryActivity(VaultEntry entry) {
        Intent intent = new Intent(this, EditEntryActivity.class);
        intent.putExtra("entryUUID", entry.getUUID());
        editEntryResultLauncher.launch(intent);
    }

    private void startAssignIconsActivity(List<VaultEntry> entries) {
        ArrayList<UUID> assignIconEntriesIds = new ArrayList<>();
        Intent assignIconIntent = new Intent(getBaseContext(), AssignIconsActivity.class);
        for (VaultEntry entry : entries) {
            assignIconEntriesIds.add(entry.getUUID());
        }

        assignIconIntent.putExtra("entries", assignIconEntriesIds);
        assignIconsResultLauncher.launch(assignIconIntent);
    }

    private void startAssignGroupsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_select_group, null);
        TextInputLayout groupSelectionLayout = view.findViewById(R.id.group_selection_layout);
        AutoCompleteTextView groupsSelection = view.findViewById(R.id.group_selection_dropdown);
        TextInputLayout newGroupLayout = view.findViewById(R.id.text_group_name_layout);
        TextInputEditText newGroupText = view.findViewById(R.id.text_group_name);

        Collection<VaultGroup> groups = _vaultManager.getVault().getUsedGroups();
        List<VaultGroupModel> groupModels = new ArrayList<>();
        groupModels.add(new VaultGroupModel(this, GroupPlaceholderType.NEW_GROUP));
        groupModels.addAll(groups.stream().map(VaultGroupModel::new).collect(Collectors.toList()));
        DropdownHelper.fillDropdown(this, groupsSelection, groupModels);

        AtomicReference<VaultGroupModel> groupModelRef = new AtomicReference<>();
        groupsSelection.setOnItemClickListener((parent, view1, position, id) -> {
            VaultGroupModel groupModel = (VaultGroupModel) parent.getItemAtPosition(position);
            groupModelRef.set(groupModel);

            if (groupModel.isPlaceholder()) {
                newGroupLayout.setVisibility(View.VISIBLE);
                newGroupText.requestFocus();
            } else {
                newGroupLayout.setVisibility(View.GONE);
            }

            groupSelectionLayout.setError(null);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.assign_groups)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnPos.setOnClickListener(v -> {
                VaultGroupModel groupModel = groupModelRef.get();
                if (groupModel == null) {
                    groupSelectionLayout.setError(getString(R.string.error_required_field));
                    return;
                }

                if (groupModel.isPlaceholder()) {
                    String newGroupName = newGroupText.getText().toString().trim();
                    if (newGroupName.isEmpty()) {
                        newGroupLayout.setError(getString(R.string.error_required_field));
                        return;
                    }

                    VaultGroup group = new VaultGroup(newGroupName);
                    _vaultManager.getVault().addGroup(group);
                    groupModel = new VaultGroupModel(group);
                }

                for (VaultEntry selectedEntry : _selectedEntries) {
                    selectedEntry.addGroup(groupModel.getUUID());
                }

                dialog.dismiss();
                saveAndBackupVault();
                _actionMode.finish();
                setGroups(_vaultManager.getVault().getUsedGroups());
            });
        });

        Dialogs.showSecureDialog(dialog);
    }

    private void startIntroActivity() {
        if (!_isDoingIntro) {
            Intent intro = new Intent(this, IntroActivity.class);
            introResultLauncher.launch(intro);
            _isDoingIntro = true;
        }
    }

    private void onScanResult(Intent data) {
        List<VaultEntry> entries = (ArrayList<VaultEntry>) data.getSerializableExtra("entries");
        if (entries != null) {
            importScannedEntries(entries);
        }
    }

    private void onAddEntryResult(Intent data) {
        if (_loaded) {
            UUID entryUUID = (UUID) data.getSerializableExtra("entryUUID");
            VaultEntry entry = _vaultManager.getVault().getEntryByUUID(entryUUID);
            _entryListView.setEntries(_vaultManager.getVault().getEntries());
            _entryListView.onEntryAdded(entry);
        }
    }

    private void onEditEntryResult() {
        if (_loaded) {
            _entryListView.setEntries(_vaultManager.getVault().getEntries());
        }
    }

    private void onAssignIconsResult() {
        if (_loaded) {
            _entryListView.setEntries(_vaultManager.getVault().getEntries());
        }
    }

    private void onScanImageResult(Intent intent) {
        if (intent.getData() != null) {
            startDecodeQrCodeImages(Collections.singletonList(intent.getData()));
            return;
        }

        if (intent.getClipData() != null) {
            ClipData data = intent.getClipData();

            List<Uri> uris = new ArrayList<>();
            for (int i = 0; i < data.getItemCount(); i++) {
                ClipData.Item item = data.getItemAt(i);
                if (item.getUri() != null) {
                    uris.add(item.getUri());
                }
            }

            if (uris.size() > 0) {
                startDecodeQrCodeImages(uris);
            }
        }
    }

    private static CharSequence buildImportError(String fileName, Throwable e) {
        SpannableStringBuilder builder = new SpannableStringBuilder(String.format("%s:\n%s", fileName, e));
        builder.setSpan(new StyleSpan(Typeface.BOLD), 0, fileName.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    private void startDecodeQrCodeImages(List<Uri> uris) {
        QrDecodeTask task = new QrDecodeTask(this, (results) -> {
            List<CharSequence> errors = new ArrayList<>();
            List<VaultEntry> entries = new ArrayList<>();
            List<GoogleAuthInfo.Export> googleAuthExports = new ArrayList<>();

            for (QrDecodeTask.Result res : results) {
                if (res.getException() != null) {
                    errors.add(buildImportError(res.getFileName(), res.getException()));
                    continue;
                }

                try {
                    Uri scanned = Uri.parse(res.getResult().getText());
                    if (Objects.equals(scanned.getScheme(), GoogleAuthInfo.SCHEME_EXPORT)) {
                        GoogleAuthInfo.Export export = GoogleAuthInfo.parseExportUri(scanned);
                        for (GoogleAuthInfo info: export.getEntries()) {
                            VaultEntry entry = new VaultEntry(info);
                            entries.add(entry);
                        }
                        googleAuthExports.add(export);
                    } else {
                        GoogleAuthInfo info = GoogleAuthInfo.parseUri(res.getResult().getText());
                        VaultEntry entry = new VaultEntry(info);
                        entries.add(entry);
                    }
                } catch (GoogleAuthInfoException e) {
                    errors.add(buildImportError(res.getFileName(), e));
                }
            }

            final DialogInterface.OnClickListener dialogDismissHandler = (dialog, which) -> importScannedEntries(entries);
            if (!googleAuthExports.isEmpty()) {
                boolean isSingleBatch = GoogleAuthInfo.Export.isSingleBatch(googleAuthExports);
                if (!isSingleBatch && errors.size() > 0) {
                    errors.add(getString(R.string.unrelated_google_auth_batches_error));
                    Dialogs.showMultiErrorDialog(this, R.string.import_error_title, getString(R.string.no_tokens_can_be_imported), errors, null);
                    return;
                } else if (!isSingleBatch) {
                    Dialogs.showErrorDialog(this, R.string.import_google_auth_failure, getString(R.string.unrelated_google_auth_batches_error));
                    return;
                } else {
                    List<Integer> missingIndices = GoogleAuthInfo.Export.getMissingIndices(googleAuthExports);
                    if (missingIndices.size() != 0) {
                        Dialogs.showPartialGoogleAuthImportWarningDialog(this, missingIndices, entries.size(), errors, dialogDismissHandler);
                        return;
                    }
                }
            }

            if ((errors.size() > 0 && results.size() > 1) || errors.size() > 1) {
                Dialogs.showMultiErrorDialog(this, R.string.import_error_title, getString(R.string.unable_to_read_qrcode_files, uris.size() - errors.size(), uris.size()), errors, dialogDismissHandler);
            } else if (errors.size() > 0) {
                Dialogs.showErrorDialog(this, getString(R.string.unable_to_read_qrcode_file, results.get(0).getFileName()), errors.get(0), dialogDismissHandler);
            } else {
                importScannedEntries(entries);
            }
        });
        task.execute(getLifecycle(), uris);
    }

    private void importScannedEntries(List<VaultEntry> entries) {
        if (entries.size() == 1) {
            startEditEntryActivityForNew(entries.get(0));
        } else if (entries.size() > 1) {
            for (VaultEntry entry: entries) {
                _vaultManager.getVault().addEntry(entry);
            }

            if (saveAndBackupVault()) {
                Toast.makeText(this, getResources().getQuantityString(R.plurals.added_new_entries, entries.size(), entries.size()), Toast.LENGTH_LONG).show();
            }

            _entryListView.setEntries(_vaultManager.getVault().getEntries());
        }
    }

    private void updateSortCategoryMenu() {
        if (_menu != null) {
            SortCategory category = _prefs.getCurrentSortCategory();
            _menu.findItem(category.getMenuItem()).setChecked(true);
        }
    }

    private void onIntroResult() {
        loadEntries();
    }

    private void checkTimeSyncSetting() {
        boolean autoTime = Settings.Global.getInt(getContentResolver(), Settings.Global.AUTO_TIME, 1) == 1;
        if (!autoTime && _prefs.isTimeSyncWarningEnabled()) {
            Dialogs.showTimeSyncWarningDialog(this, (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_DATE_SETTINGS);
                startActivity(intent);
            });
        }
    }

    private void checkIconOptimization() {
        if (!_vaultManager.getVault().areIconsOptimized()) {
            Map<UUID, VaultEntryIcon> oldIcons = _vaultManager.getVault().getEntries().stream()
                    .filter(e -> e.getIcon() != null
                            && !e.getIcon().getType().equals(IconType.SVG)
                            && !BitmapHelper.isVaultEntryIconOptimized(e.getIcon()))
                    .collect(Collectors.toMap(VaultEntry::getUUID, VaultEntry::getIcon));

            if (!oldIcons.isEmpty()) {
                IconOptimizationTask task = new IconOptimizationTask(this, this::onIconsOptimized);
                task.execute(getLifecycle(), oldIcons);
            } else {
                onIconsOptimized(Collections.emptyMap());
            }
        }
    }

    /**
     * Cue: once, offer the matching pack icon to the entries that have none. The vault may
     * predate the bundled icon packs; new and imported entries are offered theirs anyway.
     * Waits until the packs are there (the first start installs them in the background).
     */
    private void offerAssignIcons() {
        if (_prefs.isAssignIconsOffered() || !_iconPackManager.hasIconPack()) {
            return;
        }
        _prefs.setAssignIconsOffered(true);

        List<VaultEntry> entries = _vaultManager.getVault().getEntries().stream()
                .filter(e -> !e.hasIcon() && _iconPackManager.getSuggestedIcon(e.getIssuer()) != null)
                .collect(Collectors.toList());
        if (entries.isEmpty()) {
            return;
        }

        Dialogs.showSecureDialog(new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_assign_icons_dialog_title)
                .setMessage(getResources().getQuantityString(R.plurals.assign_icons_offer_text, entries.size(), entries.size()))
                .setPositiveButton(android.R.string.yes, (dialog, which) -> startAssignIconsActivity(entries))
                .setNegativeButton(android.R.string.no, null)
                .create());
    }

    private void onIconsOptimized(Map<UUID, VaultEntryIcon> newIcons) {
        for (Map.Entry<UUID, VaultEntryIcon> mapEntry : newIcons.entrySet()) {
            VaultEntry entry = _vaultManager.getVault().getEntryByUUID(mapEntry.getKey());
            entry.setIcon(mapEntry.getValue());
        }

        _vaultManager.getVault().setIconsOptimized(true);
        saveAndBackupVault();

        if (!newIcons.isEmpty()) {
            _entryListView.setEntries(_vaultManager.getVault().getEntries());
        }
    }

    private void onDecryptResult() {
        _auditLogRepository.addVaultUnlockedEvent();

        loadEntries();
    }

    private void startScanActivity() {
        if (!PermissionHelper.request(this, CODE_PERM_CAMERA, Manifest.permission.CAMERA)) {
            return;
        }

        Intent scannerActivity = new Intent(getApplicationContext(), ScannerActivity.class);
        scanResultLauncher.launch(scannerActivity);
    }

    private void startScanImageActivity() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);
        galleryIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        galleryIntent.setDataAndType(android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*");

        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        fileIntent.setType("image/*");

        Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.select_picture));
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { fileIntent });
        _vaultManager.fireIntentLauncher(this, chooserIntent, codeScanResultLauncher);
    }

    private void startPreferencesActivity() {
        startPreferencesActivity(null, null);
    }

    private void startPreferencesActivity(Class<? extends PreferencesFragment> fragmentType, String preference) {
        Intent intent = new Intent(this, PreferencesActivity.class);
        intent.putExtra("fragment", fragmentType);
        intent.putExtra("pref", preference);
        preferenceResultLauncher.launch(intent);
    }

    private void doShortcutActions() {
        Intent intent = getIntent();
        String action = intent.getStringExtra(EXTRA_ACTION);
        if (action == null || !_vaultManager.isVaultLoaded()) {
            return;
        }

        switch (action) {
            case "scan":
                startScanActivity();
                break;
            case ACTION_IME_SETTINGS:
                startPreferencesActivity(IntegrationsPreferencesFragment.class, null);
                break;
        }

        intent.removeExtra(EXTRA_ACTION);
    }

    private void handleIncomingIntent() {
        if (!_vaultManager.isVaultLoaded()) {
            return;
        }

        Intent intent = getIntent();
        if (intent.getAction() == null) {
            return;
        }

        Uri uri;
        switch (intent.getAction()) {
            case Intent.ACTION_VIEW:
                uri = intent.getData();
                if (uri != null) {
                    intent.setData(null);
                    intent.setAction(null);

                    GoogleAuthInfo info;
                    try {
                        info = GoogleAuthInfo.parseUri(uri);
                    } catch (GoogleAuthInfoException e) {
                        e.printStackTrace();
                        Dialogs.showErrorDialog(this, R.string.unable_to_process_deeplink, e);
                        break;
                    }

                    VaultEntry entry = new VaultEntry(info);
                    startEditEntryActivityForNew(entry);
                }
                break;
            case Intent.ACTION_SEND:
                if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    intent.setAction(null);
                    intent.removeExtra(Intent.EXTRA_STREAM);

                    if (uri != null) {
                        startDecodeQrCodeImages(Collections.singletonList(uri));
                    }
                }
                if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                    String stringExtra = intent.getStringExtra(Intent.EXTRA_TEXT);
                    intent.setAction(null);
                    intent.removeExtra(Intent.EXTRA_TEXT);

                    if (stringExtra != null) {
                        GoogleAuthInfo info;
                        try {
                            info = GoogleAuthInfo.parseUri(stringExtra);
                        } catch (GoogleAuthInfoException e) {
                            Dialogs.showErrorDialog(this, R.string.unable_to_process_shared_text, e);
                            break;
                        }

                        VaultEntry entry = new VaultEntry(info);
                        startEditEntryActivityForNew(entry);
                    }
                }
                break;
            case Intent.ACTION_SEND_MULTIPLE:
                if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                    List<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    intent.setAction(null);
                    intent.removeExtra(Intent.EXTRA_STREAM);

                    if (uris != null) {
                        uris = uris.stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());

                        startDecodeQrCodeImages(uris);
                    }
                }
                break;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        _entryListView.onRefreshStop();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (_vaultManager.isVaultInitNeeded()) {
            if (_prefs.isIntroDone()) {
                Toast.makeText(this, getString(R.string.vault_not_found), Toast.LENGTH_SHORT).show();
            }
            startIntroActivity();
            return;
        }

        // If the vault is not loaded yet, try to load it now in case it's plain text
        if (!_vaultManager.isVaultLoaded()) {
            VaultFile vaultFile;
            try {
                vaultFile = VaultRepository.readVaultFile(this);
            } catch (VaultRepositoryException e) {
                e.printStackTrace();
                Dialogs.showErrorDialog(this, R.string.vault_load_error, e, (dialog, which) -> {
                    finish();
                });
                return;
            }

            if (!vaultFile.isEncrypted()) {
                try {
                    _vaultManager.loadFrom(vaultFile);
                } catch (VaultRepositoryException e) {
                    e.printStackTrace();
                    Dialogs.showErrorDialog(this, R.string.vault_load_error, e, (dialog, which) -> {
                        finish();
                    });
                    return;
                }
            }
        }

        if (!_vaultManager.isVaultLoaded()) {
            startAuthActivity(false);
        } else if (_loaded) {
            // update the list of groups in the entry list view so that the chip gets updated
            setGroups(_vaultManager.getVault().getUsedGroups());

            // update the usage counts in case they are edited outside of the EntryListView
            _entryListView.setUsageCounts(_prefs.getUsageCounts());

            _entryListView.setLastUsedTimestamps(_prefs.getLastUsedTimestamps());

            // refresh all codes to prevent showing old ones
            _entryListView.refresh(false);

            _entryListView.onRefreshStart();
        } else {
            loadEntries();
            checkTimeSyncSetting();
            checkIconOptimization();

            _entryListView.onRefreshStart();
        }

        if (_vaultManager.isVaultLoaded()) {
            offerAssignIcons();
        }

        _lockBackPressHandler.setEnabled(
            _vaultManager.isAutoLockEnabled(Preferences.AUTO_LOCK_ON_BACK_BUTTON)
        );

        handleIncomingIntent();
        updateLockIcon();
        updateSortCategoryMenu();
        doShortcutActions();
        updateErrorCard();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        _menu = menu;
        getMenuInflater().inflate(R.menu.menu_main, menu);

        updateLockIcon();
        updateSortCategoryMenu();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_settings) {
            startPreferencesActivity();
        } else if (itemId == R.id.action_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.action_lock) {
            _vaultManager.lock(true);
        } else {
            if (item.getGroupId() == R.id.action_sort_category) {
                item.setChecked(true);

                SortCategory sortCategory;
                int subItemId = item.getItemId();
                if (subItemId == R.id.menu_sort_alphabetically) {
                    sortCategory = SortCategory.ISSUER;
                } else if (subItemId == R.id.menu_sort_alphabetically_reverse) {
                    sortCategory = SortCategory.ISSUER_REVERSED;
                } else if (subItemId == R.id.menu_sort_alphabetically_name) {
                    sortCategory = SortCategory.ACCOUNT;
                } else if (subItemId == R.id.menu_sort_alphabetically_name_reverse) {
                    sortCategory = SortCategory.ACCOUNT_REVERSED;
                } else if (subItemId == R.id.menu_sort_usage_count) {
                    sortCategory = SortCategory.USAGE_COUNT;
                } else if (subItemId == R.id.menu_sort_last_used) {
                    sortCategory = SortCategory.LAST_USED;
                } else {
                    sortCategory = SortCategory.CUSTOM;
                }

                _entryListView.setSortCategory(sortCategory, true);
                _prefs.setCurrentSortCategory(sortCategory);
            }

            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    /**
     * Cue: the search field under the toolbar, as on iOS. It filters as you type; the search
     * key closes the keyboard, the clear button and back empty it. The group chips stay.
     */
    private void setUpSearchField() {
        _searchBar = findViewById(R.id.search_bar);
        _searchField = findViewById(R.id.search_field);
        _searchClear = findViewById(R.id.search_clear);

        _searchField.addTextChangedListener(new SimpleTextWatcher(s -> {
            String query = s.toString();
            _entryListView.setSearchFilter(query.isEmpty() ? null : query);
            updateSearchState();
        }));
        _searchField.setOnFocusChangeListener((v, hasFocus) -> {
            // the add button would sit on the keyboard, as iOS hides it while searching
            _fabScrollHelper.setVisible(!hasFocus);
            updateSearchState();
        });
        _searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(_searchField);
                _searchField.clearFocus();
                return true;
            }
            return false;
        });
        _searchClear.setOnClickListener(v -> clearSearch());
    }

    private void updateSearchState() {
        boolean active = _searchField.hasFocus() || _searchField.length() > 0;
        _searchClear.setVisibility(active ? View.VISIBLE : View.GONE);
        _searchViewBackPressHandler.setEnabled(active);
    }

    private void updateSearchBarVisibility() {
        boolean hasEntries = _vaultManager.isVaultLoaded() && !_vaultManager.getVault().getEntries().isEmpty();
        _searchBar.setVisibility(hasEntries ? View.VISIBLE : View.GONE);
        if (!hasEntries && (_searchField.hasFocus() || _searchField.length() > 0)) {
            clearSearch();
        }
    }

    private void focusSearchField() {
        _searchField.requestFocus();
        _searchField.post(() -> WindowCompat.getInsetsController(getWindow(), _searchField).show(WindowInsetsCompat.Type.ime()));
    }

    private void hideKeyboard(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void clearSearch() {
        hideKeyboard(_searchField);
        _searchField.setText(null);
        _searchField.clearFocus();
        updateSearchState();
    }

    private void loadEntries() {
        if (!_loaded) {
            setGroups(_vaultManager.getVault().getUsedGroups());
            _entryListView.setUsageCounts(_prefs.getUsageCounts());
            _entryListView.setLastUsedTimestamps(_prefs.getLastUsedTimestamps());
            _entryListView.setEntries(_vaultManager.getVault().getEntries());
            if (!_isRecreated) {
                _entryListView.runEntriesAnimation();
            }
            _loaded = true;
            updateSearchBarVisibility();
            if (_prefs.getFocusSearchEnabled() && !_isRecreated && _searchBar.getVisibility() == View.VISIBLE) {
                focusSearchField();
            }
        }
    }

    private void startAuthActivity(boolean inhibitBioPrompt) {
        if (!_isAuthenticating) {
            Intent intent = new Intent(this, AuthActivity.class);
            intent.putExtra("inhibitBioPrompt", inhibitBioPrompt);
            authResultLauncher.launch(intent);
            _isAuthenticating = true;
        }
    }

    private void updateLockIcon() {
        // hide the lock icon if the vault is not unlocked
        if (_menu != null && _vaultManager.isVaultLoaded()) {
            MenuItem item = _menu.findItem(R.id.action_lock);
            item.setVisible(_vaultManager.getVault().isEncryptionEnabled());
        }
    }

   private void updateErrorCard() {
       ErrorCardInfo info = null;
       // Cue: shown as the banner under the search field, as on iOS: a failure in the error
       // colours with 查看, the reminder in the secondary colours with 设置备份
       boolean error = true;

       Preferences.BackupResult backupRes = _prefs.getErroredBackupResult();
       if (backupRes != null) {
           info = new ErrorCardInfo(getString(R.string.backup_error_bar_message), view -> {
               Dialogs.showBackupErrorDialog(this, backupRes, (dialog, which) -> {
                   startPreferencesActivity(BackupsPreferencesFragment.class, "pref_backups");
               });
           });
       } else if (_prefs.isBackupsReminderNeeded() && _prefs.isBackupReminderEnabled()) {
           String text;
           Date date = _prefs.getLatestBackupOrExportTime();
           if (date != null) {
               text = getString(R.string.backup_reminder_bar_message_with_latest, TimeUtils.getElapsedSince(this, date));
           } else {
               text = getString(R.string.backup_reminder_bar_message);
           }
           error = false;
           info = new ErrorCardInfo(text, view -> {
               Dialogs.showSecureDialog(new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Cue_AlertDialog_Error)
                       .setTitle(R.string.backup_reminder_bar_dialog_title)
                       .setMessage(R.string.backup_reminder_bar_dialog_summary)
                       .setIconAttribute(android.R.attr.alertDialogIcon)
                       .setPositiveButton(R.string.backup_reminder_bar_dialog_accept, (dialog, whichButton) -> {
                           startPreferencesActivity(BackupsPreferencesFragment.class, "pref_backups");
                       })
                       .setNegativeButton(android.R.string.cancel, null)
                       .create());
           });
       } else if (_prefs.isPlaintextBackupWarningNeeded()) {
           info = new ErrorCardInfo(getString(R.string.backup_plaintext_export_warning), view -> showPlaintextExportWarningOptions());
       }

       showBackupBanner(info, error);
   }

    private void showBackupBanner(@Nullable ErrorCardInfo info, boolean error) {
        View banner = findViewById(R.id.backup_banner);
        if (info == null) {
            banner.setVisibility(View.GONE);
            return;
        }

        int background = MaterialColors.getColor(banner, error
                ? com.google.android.material.R.attr.colorErrorContainer
                : com.google.android.material.R.attr.colorSecondaryContainer);
        int foreground = MaterialColors.getColor(banner, error
                ? com.google.android.material.R.attr.colorOnErrorContainer
                : com.google.android.material.R.attr.colorOnSecondaryContainer);
        banner.setBackgroundTintList(ColorStateList.valueOf(background));

        ImageView icon = banner.findViewById(R.id.backup_banner_icon);
        icon.setImageResource(error ? R.drawable.ic_outline_error_24 : R.drawable.ic_outline_cloud_upload_24);
        icon.setImageTintList(ColorStateList.valueOf(foreground));
        TextView text = banner.findViewById(R.id.backup_banner_text);
        text.setText(info.getMessage());
        text.setTextColor(foreground);
        TextView action = banner.findViewById(R.id.backup_banner_action);
        action.setText(error ? R.string.backup_banner_view : R.string.backup_banner_set_up);
        action.setTextColor(foreground);
        ImageView chevron = banner.findViewById(R.id.backup_banner_chevron);
        chevron.setImageTintList(ColorStateList.valueOf(foreground));

        banner.setOnClickListener(info.getListener());
        banner.setVisibility(View.VISIBLE);
    }

    private void showPlaintextExportWarningOptions() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_plaintext_warning, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Cue_AlertDialog_Warning)
                .setTitle(R.string.backup_plaintext_export_warning)
                .setView(view)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        CheckBox checkBox = view.findViewById(R.id.checkbox_plaintext_warning);
        checkBox.setChecked(false);

        dialog.setOnShowListener(d -> {
            Button btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            btnPos.setOnClickListener(l -> {
                dialog.dismiss();

                _prefs.setIsPlaintextBackupWarningDisabled(checkBox.isChecked());
                _prefs.setIsPlaintextBackupWarningNeeded(false);

                updateErrorCard();
            });
        });

        Dialogs.showSecureDialog(dialog);
    }

    @Override
    public void onRestoreInstanceState(@Nullable Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }

        HashSet<UUID> filter = (HashSet<UUID>) savedInstanceState.getSerializable("prefGroupFilter");
        if (filter != null) {
            _prefGroupFilter = filter;
        }
    }

    @Override
    public void onEntryClick(VaultEntry entry) {
        if (_actionMode != null) {
            if (_selectedEntries.isEmpty()) {
                _actionMode.finish();
            } else {
                setFavoriteMenuItemVisiblity();
                setIsMultipleSelected(_selectedEntries.size() > 1);
            }
        }
    }

    @Override
    public void onSelect(VaultEntry entry) {
        _selectedEntries.add(entry);
    }

    @Override
    public void onDeselect(VaultEntry entry) {
        _selectedEntries.remove(entry);
    }

    private void setIsMultipleSelected(boolean multipleSelected) {
        _entryListView.setIsLongPressDragEnabled(!multipleSelected);
        _actionMode.getMenu().findItem(R.id.action_edit).setVisible(!multipleSelected);
        _actionMode.getMenu().findItem(R.id.action_copy).setVisible(!multipleSelected);
    }

    private void setAssignIconsMenuItemVisibility() {
        MenuItem assignIconsMenuItem = _actionMode.getMenu().findItem(R.id.action_assign_icons);
        assignIconsMenuItem.setVisible(_iconPackManager.hasIconPack());
    }

    private void setFavoriteMenuItemVisiblity() {
        MenuItem toggleFavoriteMenuItem = _actionMode.getMenu().findItem(R.id.action_toggle_favorite);

        if (_selectedEntries.size() == 1){
            if (_selectedEntries.get(0).isFavorite()) {
                toggleFavoriteMenuItem.setIcon(R.drawable.ic_filled_keep_24);
                toggleFavoriteMenuItem.setTitle(R.string.unfavorite);
            } else {
                toggleFavoriteMenuItem.setIcon(R.drawable.ic_outline_keep_24);
                toggleFavoriteMenuItem.setTitle(R.string.favorite);
            }
        } else {
            toggleFavoriteMenuItem.setIcon(R.drawable.ic_outline_keep_24);
            toggleFavoriteMenuItem.setTitle(String.format("%s / %s", getString(R.string.favorite), getString(R.string.unfavorite)));
        }
    }

    @Override
    public void onLongEntryClick(VaultEntry entry) {
        if (!_selectedEntries.isEmpty()) {
            return;
        }

        _selectedEntries.add(entry);
        _entryListView.setActionModeState(true, entry);
        startActionMode();
    }

    private void startActionMode() {
        _actionMode = startSupportActionMode(_actionModeCallbacks);
        _actionModeBackPressHandler.setEnabled(true);
        setFavoriteMenuItemVisiblity();
        setAssignIconsMenuItemVisibility();
    }

    @Override
    public void onEntryMove(VaultEntry entry1, VaultEntry entry2) {
        _vaultManager.getVault().moveEntry(entry1, entry2);
    }

    @Override
    public void onEntryDrop(VaultEntry entry) {
        saveVault();
    }

    @Override
    public void onEntryChange(VaultEntry entry) {
        saveAndBackupVault();
    }

    public void onEntryCopy(VaultEntry entry) {
        copyEntryCode(entry);
    }

    @Override
    public void onScroll(int dx, int dy) {
        if (!_isDPadPressed) {
            _fabScrollHelper.onScroll(dx, dy);
        }
    }

    @Override
    public void onListChange() {
        _fabScrollHelper.setVisible(!_searchField.hasFocus());
        updateSearchBarVisibility();
    }

    @Override
    public void onSaveGroupFilter(Set<UUID> groupFilter) {
        if (_vaultManager.getVault().isGroupsMigrationFresh()) {
            saveAndBackupVault();
        }
        _prefs.setGroupFilter(groupFilter);
    }

    @Override
    public void onLocked(boolean userInitiated) {
        if (_actionMode != null) {
            _actionMode.finish();
        }
        clearSearch();
        _searchBar.setVisibility(View.GONE);
        if (_fabMenuHelper != null && _fabMenuHelper.isOpen()) {
            _fabMenuHelper.close();
        }

        _entryListView.clearEntries();
        _loaded = false;

        if (userInitiated) {
            startAuthActivity(true);
        } else {
            super.onLocked(false);
        }
    }

    @Override
    protected boolean saveAndBackupVault() {
        boolean res = super.saveAndBackupVault();
        updateErrorCard();
        return res;
    }

    @SuppressLint("InlinedApi")
    private void copyEntryCode(VaultEntry entry) {
        String otp;
        try {
            otp = entry.getInfo().getOtp();
        } catch (OtpInfoException e) {
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("text/plain", otp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        clipboard.setPrimaryClip(clip);
        if (_prefs.isMinimizeOnCopyEnabled()) {
            moveTaskToBack(true);
        }
    }

    private class SearchViewBackPressHandler extends OnBackPressedCallback {
        public SearchViewBackPressHandler() {
            super(false);
        }

        @Override
        public void handleOnBackPressed() {
            clearSearch();
        }
    }

    private class LockBackPressHandler extends OnBackPressedCallback {
        public LockBackPressHandler() {
            super(false);
        }

        @Override
        public void handleOnBackPressed() {
            if (_vaultManager.isAutoLockEnabled(Preferences.AUTO_LOCK_ON_BACK_BUTTON)) {
                _vaultManager.lock(false);
            }
        }
    }

    private class ActionModeBackPressHandler extends OnBackPressedCallback {
        public ActionModeBackPressHandler() {
            super(false);
        }

        @Override
        public void handleOnBackPressed() {
            if (_actionMode != null) {
                _actionMode.finish();
            }
        }
    }

    private class FabMenuBackPressHandler extends OnBackPressedCallback {
        public FabMenuBackPressHandler() {
            super(false);
        }

        @Override
        public void handleOnBackPressed() {
            if (_fabMenuHelper != null && _fabMenuHelper.isOpen()) {
                _fabMenuHelper.close();
            }
        }
    }

    private class ActionModeCallbacks implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.menu_action_mode, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (_selectedEntries.size() == 0) {
                mode.finish();
                return true;
            }

            int itemId = item.getItemId();
            if (itemId == R.id.action_copy) {
                copyEntryCode(_selectedEntries.get(0));
                mode.finish();
            } else if (itemId == R.id.action_edit) {
                startEditEntryActivity(_selectedEntries.get(0));
                mode.finish();
            } else if (itemId == R.id.action_toggle_favorite) {
                for (VaultEntry entry : _selectedEntries) {
                    _vaultManager.getVault().editEntry(entry, newEntry -> {
                        newEntry.setIsFavorite(!newEntry.isFavorite());
                    });
                }

                saveAndBackupVault();
                _entryListView.setEntries(_vaultManager.getVault().getEntries());
                mode.finish();
            } else if (itemId == R.id.action_share_qr) {
                Intent intent = new Intent(getBaseContext(), TransferEntriesActivity.class);
                ArrayList<GoogleAuthInfo> authInfos = new ArrayList<>();
                for (VaultEntry entry : _selectedEntries) {
                    GoogleAuthInfo authInfo = new GoogleAuthInfo(entry.getInfo(), entry.getName(), entry.getIssuer());
                    authInfos.add(authInfo);

                    _auditLogRepository.addEntrySharedEvent(entry.getUUID().toString());
                }

                intent.putExtra("authInfos", authInfos);
                startActivity(intent);

                mode.finish();
            } else if (itemId == R.id.action_delete) {
                Dialogs.showDeleteEntriesDialog(MainActivity.this, _selectedEntries, (d, which) -> {
                    for (VaultEntry entry : _selectedEntries) {
                        _vaultManager.getVault().removeEntry(entry);
                    }
                    saveAndBackupVault();
                    _entryListView.setGroups(_vaultManager.getVault().getUsedGroups());
                    _entryListView.setEntries(_vaultManager.getVault().getEntries());
                    mode.finish();
                });
            } else if (itemId == R.id.action_select_all) {
                _selectedEntries = _entryListView.selectAllEntries();
                setFavoriteMenuItemVisiblity();
                setIsMultipleSelected(_selectedEntries.size() > 1);
            } else if (itemId == R.id.action_assign_icons) {
                startAssignIconsActivity(_selectedEntries);
                mode.finish();
            } else if (itemId == R.id.action_assign_groups) {
                startAssignGroupsDialog();
            } else {
                return false;
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            _entryListView.setActionModeState(false, null);
            _actionModeBackPressHandler.setEnabled(false);
            _selectedEntries.clear();
            _actionMode = null;
        }
    }
}

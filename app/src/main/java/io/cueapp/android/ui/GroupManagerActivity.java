package io.cueapp.android.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.cueapp.android.R;
import io.cueapp.android.ui.dialogs.Dialogs;
import io.cueapp.android.ui.views.GroupAdapter;
import io.cueapp.android.util.Cloner;
import io.cueapp.android.helpers.ViewHelper;
import io.cueapp.android.vault.VaultEntry;
import io.cueapp.android.vault.VaultGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GroupManagerActivity extends CueActivity implements GroupAdapter.Listener {
    private GroupAdapter _adapter;
    private HashSet<UUID> _removedGroups;
    // Cue: entries taken out of a group, by group, saved with the rest of this screen
    private HashMap<UUID, Set<UUID>> _removedEntries;
    private RecyclerView _groupsView;
    private View _emptyStateView;
    private BackPressHandler _backPressHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (abortIfOrphan(savedInstanceState)) {
            return;
        }
        setContentView(R.layout.activity_groups);
        setSupportActionBar(findViewById(R.id.toolbar));
        ViewHelper.setupAppBarInsets(findViewById(R.id.app_bar_layout));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        _backPressHandler = new BackPressHandler();
        getOnBackPressedDispatcher().addCallback(this, _backPressHandler);

        _removedGroups = new HashSet<>();
        _removedEntries = new HashMap<>();
        if (savedInstanceState != null) {
            List<String> removedGroups = savedInstanceState.getStringArrayList("removedGroups");
            if (removedGroups != null) {
                for (String uuid : removedGroups) {
                    _removedGroups.add(UUID.fromString(uuid));
                }
            }
            List<String> removedEntries = savedInstanceState.getStringArrayList("removedEntries");
            if (removedEntries != null) {
                for (String pair : removedEntries) {
                    String[] uuids = pair.split("/");
                    markRemoved(UUID.fromString(uuids[0]), UUID.fromString(uuids[1]));
                }
            }
        }

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(
                    @NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder) {

                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int draggedItemIndex = viewHolder.getBindingAdapterPosition();
                int targetIndex = target.getBindingAdapterPosition();

                _adapter.onItemMove(draggedItemIndex, targetIndex);

                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }
        });

        _adapter = new GroupAdapter(this);
        _groupsView = findViewById(R.id.list_groups);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        _groupsView.setLayoutManager(layoutManager);
        _groupsView.setAdapter(_adapter);
        _groupsView.setNestedScrollingEnabled(false);
        touchHelper.attachToRecyclerView(_groupsView);

        for (VaultGroup group : _vaultManager.getVault().getGroups()) {
            if (!_removedGroups.contains(group.getUUID())) {
                _adapter.addGroup(group);
            }
        }

        _emptyStateView = findViewById(R.id.vEmptyList);
        updateEmptyState();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> removed = new ArrayList<>();
        for (UUID uuid : _removedGroups) {
            removed.add(uuid.toString());
        }

        outState.putStringArrayList("removedGroups", removed);

        ArrayList<String> removedEntries = new ArrayList<>();
        for (Map.Entry<UUID, Set<UUID>> group : _removedEntries.entrySet()) {
            for (UUID entry : group.getValue()) {
                removedEntries.add(group.getKey() + "/" + entry);
            }
        }
        outState.putStringArrayList("removedEntries", removedEntries);
    }

    /**
     * The entries in the group, in the vault's order, without those already taken out of it here.
     */
    private List<VaultEntry> getEntriesInGroup(UUID group) {
        Set<UUID> removed = _removedEntries.get(group);
        List<VaultEntry> entries = new ArrayList<>();
        for (VaultEntry entry : _vaultManager.getVault().getEntries()) {
            if (entry.getGroups().contains(group) && (removed == null || !removed.contains(entry.getUUID()))) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private void markRemoved(UUID group, UUID entry) {
        Set<UUID> removed = _removedEntries.get(group);
        if (removed == null) {
            removed = new HashSet<>();
            _removedEntries.put(group, removed);
        }
        removed.add(entry);
    }

    @Override
    public int getEntryCount(VaultGroup group) {
        return getEntriesInGroup(group.getUUID()).size();
    }

    /**
     * Cue: the group's entries, to check the ones to take out of it; they stay in the vault.
     */
    @Override
    public void onShowGroupEntries(VaultGroup group) {
        List<VaultEntry> entries = getEntriesInGroup(group.getUUID());
        if (entries.isEmpty()) {
            Dialogs.showSecureDialog(new MaterialAlertDialogBuilder(this)
                    .setTitle(group.getName())
                    .setMessage(R.string.no_group_entries)
                    .setPositiveButton(android.R.string.ok, null)
                    .create());
            return;
        }

        String[] titles = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            VaultEntry entry = entries.get(i);
            if (entry.getIssuer().isEmpty() || entry.getName().isEmpty()) {
                titles[i] = entry.getIssuer().isEmpty() ? entry.getName() : entry.getIssuer();
            } else {
                titles[i] = String.format("%s (%s)", entry.getIssuer(), entry.getName());
            }
        }

        boolean[] checked = new boolean[entries.size()];
        Dialogs.showSecureDialog(new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.remove_from_group_title, group.getName()))
                .setMultiChoiceItems(titles, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.remove_from_group, (dialog, which) -> {
                    boolean changed = false;
                    for (int i = 0; i < entries.size(); i++) {
                        if (checked[i]) {
                            markRemoved(group.getUUID(), entries.get(i).getUUID());
                            changed = true;
                        }
                    }
                    if (changed) {
                        _adapter.refreshGroup(group.getUUID());
                        _backPressHandler.setEnabled(true);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create());
    }

    @Override
    public void onEditGroup(VaultGroup group) {
        Dialogs.TextInputListener onEditGroup = text -> {
            String newGroupName = new String(text).trim();
            if (!newGroupName.isEmpty()) {
                VaultGroup newGroup = Cloner.clone(group);
                newGroup.setName(newGroupName);
                _adapter.replaceGroup(group.getUUID(), newGroup);
                _backPressHandler.setEnabled(true);
            }
        };

        Dialogs.showTextInputDialog(GroupManagerActivity.this, R.string.rename_group, R.string.group_name_hint, onEditGroup, group.getName());
    }

    @Override
    public void onRemoveGroup(VaultGroup group) {
        Dialogs.showSecureDialog(new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Cue_AlertDialog_Warning)
                .setTitle(R.string.remove_group)
                .setMessage(R.string.remove_group_description)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                    _removedGroups.add(group.getUUID());
                    _adapter.removeGroup(group);
                    _backPressHandler.setEnabled(true);
                    updateEmptyState();
                })
                .setNegativeButton(android.R.string.no, null)
                .create());
    }

    public void onRemoveUnusedGroups() {
        Dialogs.showSecureDialog(new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Cue_AlertDialog_Warning)
                .setTitle(R.string.remove_unused_groups)
                .setMessage(R.string.remove_unused_groups_description)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                    Set<VaultGroup> unusedGroups = new HashSet<>(_vaultManager.getVault().getGroups());
                    unusedGroups.removeAll(_vaultManager.getVault().getUsedGroups());

                    for (VaultGroup group : unusedGroups) {
                        _removedGroups.add(group.getUUID());
                        _adapter.removeGroup(group);
                    }
                    _backPressHandler.setEnabled(true);
                    updateEmptyState();
                })
                .setNegativeButton(android.R.string.no, null)
                .create());
    }

    private void saveAndFinish() {
        for (Map.Entry<UUID, Set<UUID>> group : _removedEntries.entrySet()) {
            for (UUID uuid : group.getValue()) {
                if (_vaultManager.getVault().hasEntryByUUID(uuid)) {
                    _vaultManager.getVault().getEntryByUUID(uuid).removeGroup(group.getKey());
                }
            }
        }

        if (!_removedGroups.isEmpty()) {
            for (UUID uuid : _removedGroups) {
                _vaultManager.getVault().removeGroup(uuid);
            }
        }

        _vaultManager.getVault().replaceGroups(_adapter.getGroups());
        saveAndBackupVault();

        finish();
    }

    private void discardAndFinish() {
        if (_removedGroups.isEmpty() && _removedEntries.isEmpty()) {
            finish();
            return;
        }

        Dialogs.showDiscardDialog(this,
                (dialog, which) -> saveAndFinish(),
                (dialog, which) -> finish());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_groups, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            discardAndFinish();
        } else if (itemId == R.id.action_save) {
            saveAndFinish();
        } else if (itemId == R.id.action_delete_unused_groups) {
            onRemoveUnusedGroups();
        } else {
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private void updateEmptyState() {
        if (_adapter.getItemCount() > 0) {
            _groupsView.setVisibility(View.VISIBLE);
            _emptyStateView.setVisibility(View.GONE);
        } else {
            _groupsView.setVisibility(View.GONE);
            _emptyStateView.setVisibility(View.VISIBLE);
        }
    }

    private class BackPressHandler extends OnBackPressedCallback {
        public BackPressHandler() {
            super(false);
        }

        @Override
        public void handleOnBackPressed() {
            discardAndFinish();
        }
    }
}

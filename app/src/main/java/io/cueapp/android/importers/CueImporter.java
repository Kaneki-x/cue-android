package io.cueapp.android.importers;

import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;

import io.cueapp.android.R;
import io.cueapp.android.helpers.ContextHelper;
import io.cueapp.android.ui.dialogs.Dialogs;
import io.cueapp.android.ui.tasks.PasswordSlotDecryptTask;
import io.cueapp.android.util.IOUtils;
import io.cueapp.android.vault.VaultEntry;
import io.cueapp.android.vault.VaultEntryException;
import io.cueapp.android.vault.VaultFile;
import io.cueapp.android.vault.VaultFileCredentials;
import io.cueapp.android.vault.VaultFileException;
import io.cueapp.android.vault.VaultGroup;
import io.cueapp.android.vault.slots.PasswordSlot;
import io.cueapp.android.vault.slots.SlotList;
import com.topjohnwu.superuser.io.SuFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public class CueImporter extends DatabaseImporter {

    public CueImporter(Context context) {
        super(context);
    }

    @Override
    protected SuFile getAppPath() {
        throw new UnsupportedOperationException();
    }

    @Override
    public State read(InputStream stream, boolean isInternal) throws DatabaseImporterException {
        try {
            byte[] bytes = IOUtils.readAll(stream);
            VaultFile file = VaultFile.fromBytes(bytes);
            if (file.isEncrypted()) {
                return new EncryptedState(file);
            }
            return new DecryptedState(file.getContent());
        } catch (VaultFileException | IOException e) {
            throw new DatabaseImporterException(e);
        }
    }

    public static class EncryptedState extends State {
        private VaultFile _file;

        private EncryptedState(VaultFile file) {
            super(true);
            _file = file;
        }

        public SlotList getSlots() {
            return _file.getHeader().getSlots();
        }

        public State decrypt(VaultFileCredentials creds) throws DatabaseImporterException {
            JSONObject obj;
            try {
                obj = _file.getContent(creds);
            } catch (VaultFileException e) {
                throw new DatabaseImporterException(e);
            }

            return new DecryptedState(obj, creds);
        }

        public State decrypt(char[] password) throws DatabaseImporterException {
            List<PasswordSlot> slots = getSlots().findAll(PasswordSlot.class);
            PasswordSlotDecryptTask.Result result = PasswordSlotDecryptTask.decrypt(slots, password);
            VaultFileCredentials creds = new VaultFileCredentials(result.getKey(), getSlots());
            return decrypt(creds);
        }

        @Override
        public void decrypt(Context context, DecryptListener listener) {
            Dialogs.showPasswordInputDialog(context, R.string.enter_password_import_title, 0, (Dialogs.TextInputListener) password -> {
                List<PasswordSlot> slots = getSlots().findAll(PasswordSlot.class);
                PasswordSlotDecryptTask.Params params = new PasswordSlotDecryptTask.Params(slots, password);
                PasswordSlotDecryptTask task = new PasswordSlotDecryptTask(context, result -> {
                    try {
                        if (result == null) {
                            throw new DatabaseImporterException("Password incorrect");
                        }

                        VaultFileCredentials creds = new VaultFileCredentials(result.getKey(), getSlots());
                        State state = decrypt(creds);
                        listener.onStateDecrypted(state);
                    } catch (DatabaseImporterException e) {
                        listener.onError(e);
                    }
                });

                Lifecycle lifecycle = ContextHelper.getLifecycle(context);
                task.execute(lifecycle, params);
            }, (DialogInterface.OnCancelListener) dialog -> listener.onCanceled());
        }
    }

    public static class DecryptedState extends State {
        private JSONObject _obj;
        private VaultFileCredentials _creds;

        private DecryptedState(JSONObject obj) {
            this(obj, null);
        }

        private DecryptedState(JSONObject obj, VaultFileCredentials creds) {
            super(false);
            _obj = obj;
            _creds = creds;
        }

        @Nullable
        public VaultFileCredentials getCredentials() {
            return _creds;
        }

        @Override
        public Result convert() throws DatabaseImporterException {
            Result result = new Result();

            try {
                if (_obj.has("groups")) {
                    JSONArray groupArray = _obj.getJSONArray("groups");
                    for (int i = 0; i < groupArray.length(); i++) {
                        JSONObject groupObj = groupArray.getJSONObject(i);
                        try {
                            VaultGroup group = convertGroup(groupObj);
                            if (!result.getGroups().has(group)) {
                                result.addGroup(group);
                            }
                        } catch (DatabaseImporterEntryException e) {
                            result.addError(e);
                        }
                    }
                }

                JSONArray entryArray = _obj.getJSONArray("entries");
                for (int i = 0; i < entryArray.length(); i++) {
                    JSONObject entryObj = entryArray.getJSONObject(i);
                    try {
                        VaultEntry entry = convertEntry(entryObj);
                        for (UUID groupUuid : entry.getGroups()) {
                            if (!result.getGroups().has(groupUuid)) {
                                entry.getGroups().remove(groupUuid);
                            }
                        }
                        result.addEntry(entry);
                    } catch (DatabaseImporterEntryException e) {
                        result.addError(e);
                    }
                }
            } catch (JSONException e) {
                throw new DatabaseImporterException(e);
            }

            return result;
        }

        private static VaultEntry convertEntry(JSONObject obj) throws DatabaseImporterEntryException {
            try {
                return VaultEntry.fromJson(obj);
            } catch (VaultEntryException e) {
                throw new DatabaseImporterEntryException(e, obj.toString());
            }
        }

        private static VaultGroup convertGroup(JSONObject obj) throws DatabaseImporterEntryException {
            try {
                return VaultGroup.fromJson(obj);
            } catch (VaultEntryException e) {
                throw new DatabaseImporterEntryException(e, obj.toString());
            }
        }
    }
}

package io.cueapp.android.vault;

import androidx.annotation.NonNull;

import io.cueapp.android.crypto.CryptParameters;
import io.cueapp.android.crypto.CryptResult;
import io.cueapp.android.crypto.MasterKey;
import io.cueapp.android.crypto.MasterKeyException;
import io.cueapp.android.util.Cloner;
import io.cueapp.android.vault.slots.SlotList;

import java.io.Serializable;

public class VaultFileCredentials implements Serializable {
    private MasterKey _key;
    private SlotList _slots;

    public VaultFileCredentials() {
        _key = MasterKey.generate();
        _slots = new SlotList();
    }

    public VaultFileCredentials(MasterKey key, SlotList slots) {
        _key = key;
        _slots = slots;
    }

    public CryptResult encrypt(byte[] bytes) throws MasterKeyException {
        return _key.encrypt(bytes);
    }

    public CryptResult decrypt(byte[] bytes, CryptParameters params) throws MasterKeyException {
        return _key.decrypt(bytes, params);
    }

    public MasterKey getKey() {
        return _key;
    }

    public SlotList getSlots() {
        return _slots;
    }

    /**
     * Returns a copy of these VaultFileCredentials that is suitable for exporting.
     * In case there's a backup password slot, any regular password slots are stripped.
     */
    public VaultFileCredentials exportable() {
        return new VaultFileCredentials(_key, _slots.exportable());
    }

    @NonNull
    @Override
    public VaultFileCredentials clone() {
        return Cloner.clone(this);
    }
}

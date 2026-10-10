package io.cueapp.android.vault;

public class VaultEntryException extends Exception {
    public VaultEntryException(Throwable cause) {
        super(cause);
    }

    public VaultEntryException(String message) {
        super(message);
    }
}

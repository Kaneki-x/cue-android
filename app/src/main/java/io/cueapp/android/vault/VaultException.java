package io.cueapp.android.vault;

public class VaultException extends Exception {
    public VaultException(Throwable cause) {
        super(cause);
    }

    public VaultException(String message) {
        super(message);
    }
}

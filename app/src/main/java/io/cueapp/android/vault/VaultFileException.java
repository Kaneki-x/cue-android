package io.cueapp.android.vault;

public class VaultFileException extends Exception {
    public VaultFileException(Throwable cause) {
        super(cause);
    }

    public VaultFileException(String message) {
        super(message);
    }
}

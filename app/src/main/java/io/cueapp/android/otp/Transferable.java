package io.cueapp.android.otp;

import android.net.Uri;

public interface Transferable {
    Uri getUri() throws GoogleAuthInfoException;
}

package io.cueapp.android.ui.dialogs;

import android.content.Context;

import io.cueapp.android.R;

/**
 * The GNU GPL v3, which Cue for Android is distributed under (it is a modified Aegis
 * Authenticator). The text is android/LICENSE, linked into the assets.
 */
public class LicenseDialog extends SimpleWebViewDialog {
    public LicenseDialog() {
        super(R.string.license);
    }

    public static LicenseDialog create() {
        return new LicenseDialog();
    }

    @Override
    protected String getContent(Context context) {
        String license = readAssetAsString(context, "LICENSE");
        String html = readAssetAsString(context, "license.html");
        return String.format(html, license, getBackgroundColor(), getTextColor());
    }
}

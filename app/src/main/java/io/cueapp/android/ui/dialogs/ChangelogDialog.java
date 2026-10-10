package io.cueapp.android.ui.dialogs;

import android.content.Context;

import io.cueapp.android.R;

public class ChangelogDialog extends SimpleWebViewDialog {
    public ChangelogDialog() {
        super(R.string.changelog);
    }

    public static ChangelogDialog create() {
        return new ChangelogDialog();
    }

    @Override
    protected String getContent(Context context) {
        String content = readAssetAsString(context, "changelog.html");
        return String.format(content, getBackgroundColor(), getTextColor());
    }
}

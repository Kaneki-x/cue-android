package io.cueapp.android.ui;

import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.cueapp.android.BuildConfig;
import io.cueapp.android.R;
import io.cueapp.android.ui.dialogs.LicenseDialog;
import io.cueapp.android.ui.dialogs.Dialogs;
import io.cueapp.android.helpers.ViewHelper;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AboutActivity extends CueActivity {
    // GPL-3.0 §6: where the corresponding source is; §5: that this is a modified Aegis
    private static final String SOURCE_CODE = "https://github.com/Kaneki-x/cue-android";
    private static final String AEGIS = "https://github.com/beemdevelopment/Aegis";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (abortIfOrphan(savedInstanceState)) {
            return;
        }

        setContentView(R.layout.activity_about);
        setSupportActionBar(findViewById(R.id.toolbar));
        ViewHelper.setupAppBarInsets(findViewById(R.id.app_bar_layout));

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        View btnThirdPartyLicenses = findViewById(R.id.btn_third_party_licenses);
        btnThirdPartyLicenses.setOnClickListener(v -> {
            Intent intent = new Intent(this, LicensesActivity.class);
            startActivity(intent);
        });

        TextView appVersion = findViewById(R.id.app_version);
        appVersion.setText(getCurrentAppVersion());

        View btnAppVersion = findViewById(R.id.btn_app_version);
        btnAppVersion.setOnClickListener(v -> {
            copyToClipboard(getCurrentAppVersion(), R.string.version_copied);
        });

        // the privacy policy Google Play asks for; a plain dialog, so it shows without a web view
        View btnPrivacyPolicy = findViewById(R.id.btn_privacy_policy);
        btnPrivacyPolicy.setOnClickListener(v -> {
            Dialogs.showSecureDialog(new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.privacy_policy)
                    .setMessage(R.string.privacy_policy_text)
                    .setPositiveButton(android.R.string.ok, null)
                    .create());
        });

        findViewById(R.id.btn_license).setOnClickListener(v -> LicenseDialog.create()
                .setTheme(_themeHelper.getConfiguredTheme())
                .show(getSupportFragmentManager(), null));
        findViewById(R.id.btn_source_code).setOnClickListener(v -> openUrl(SOURCE_CODE));
        findViewById(R.id.btn_based_on_aegis).setOnClickListener(v -> openUrl(AEGIS));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.about_scroll_view), (targetView, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            targetView.setPadding(
                    0,
                    0,
                    0,
                    insets.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private static String getCurrentAppVersion() {
        if (BuildConfig.DEBUG) {
            return String.format("%s (%s) · %s (%s)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.GIT_HASH, BuildConfig.GIT_BRANCH);
        }

        // as on iOS: version (build)
        return String.format("%s (%s)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            copyToClipboard(url, R.string.url_copied);
        }
    }

    private void copyToClipboard(String text, @StringRes int messageId) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData data = ClipData.newPlainText("text/plain", text);
        clipboard.setPrimaryClip(data);
        Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }
}

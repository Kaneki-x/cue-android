package io.cueapp.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import io.cueapp.android.BuildConfig;
import io.cueapp.android.R;
import io.cueapp.android.crypto.pins.GuardianProjectFDroidRSA2048;
import io.cueapp.android.vault.VaultRepository;

import info.guardianproject.GuardianProjectRSA4096;
import info.guardianproject.trustedintents.TrustedIntents;

public class PanicResponderActivity extends CueActivity {
    public static final String PANIC_TRIGGER_ACTION = "info.guardianproject.panic.action.TRIGGER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!_prefs.isPanicTriggerEnabled()) {
            Toast.makeText(this, R.string.panic_trigger_ignore_toast, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent intent;
        if (!BuildConfig.TEST.get()) {
            TrustedIntents trustedIntents = TrustedIntents.get(this);
            trustedIntents.addTrustedSigner(GuardianProjectRSA4096.class);
            trustedIntents.addTrustedSigner(GuardianProjectFDroidRSA2048.class);

            intent = trustedIntents.getIntentFromTrustedSender(this);
        } else {
            intent = getIntent();
        }

        if (intent != null && PANIC_TRIGGER_ACTION.equals(intent.getAction())) {
            VaultRepository.deleteFile(this);
            _vaultManager.lock(false);
            finishApp();
            return;
        }

        finish();
    }

    private void finishApp() {
        ExitActivity.exitAppAndRemoveFromRecents(this);
        finishAndRemoveTask();
    }
}

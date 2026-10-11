package io.cueapp.android.ime;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import io.cueapp.android.ui.MainActivity;

/**
 * What the system's keyboard settings open for the Cue keyboard (res/xml/method.xml). It
 * shows nothing: it hands over to MainActivity, which unlocks the vault first and then opens
 * the keyboard settings, so they can't be reached or changed without unlocking.
 */
public class ImeSettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_IME_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

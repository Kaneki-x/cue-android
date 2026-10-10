package io.cueapp.android.ui.fragments.preferences;

import android.os.Bundle;

import io.cueapp.android.R;

public class MainPreferencesFragment extends PreferencesFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences);
    }
}

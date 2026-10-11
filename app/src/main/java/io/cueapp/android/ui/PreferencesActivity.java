package io.cueapp.android.ui;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import io.cueapp.android.R;
import io.cueapp.android.ui.fragments.preferences.AppearancePreferencesFragment;
import io.cueapp.android.ui.fragments.preferences.MainPreferencesFragment;
import io.cueapp.android.ui.fragments.preferences.PreferencesFragment;
import io.cueapp.android.helpers.ViewHelper;

public class PreferencesActivity extends CueActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private Fragment _fragment;
    private CharSequence _prefTitle;
    private boolean _titleFromScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // the settings read the vault (security, backups, audit log) and change how it is
        // protected, so they never open without it, whoever starts them
        if (abortIfVaultNotLoaded()) {
            return;
        }
        setContentView(R.layout.activity_preferences);
        setSupportActionBar(findViewById(R.id.toolbar));
        ViewHelper.setupAppBarInsets(findViewById(R.id.app_bar_layout));
        getSupportFragmentManager()
                .registerFragmentLifecycleCallbacks(new FragmentResumeListener(), true);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        if (savedInstanceState == null) {
            _fragment = new MainPreferencesFragment();
            _fragment.setArguments(getIntent().getExtras());

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content, _fragment)
                    .commit();

            PreferencesFragment requestedFragment = getRequestedFragment();
            if (requestedFragment != null) {
                _fragment = requestedFragment;
                showFragment(_fragment);
                _titleFromScreen = true;
            }
        } else {
            _fragment = getSupportFragmentManager().findFragmentById(R.id.content);
            _prefTitle = savedInstanceState.getCharSequence("prefTitle");
            if (_prefTitle != null) {
                setTitle(_prefTitle);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putCharSequence("prefTitle", _prefTitle);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
        } else {
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, Preference pref) {
        _fragment = getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), pref.getFragment());
        _fragment.setArguments(pref.getExtras());
        _fragment.setTargetFragment(caller, 0);
        showFragment(_fragment);

        _prefTitle = pref.getTitle();
        setTitle(_prefTitle);
        return true;
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
                .replace(R.id.content, fragment)
                .addToBackStack(null)
                .commit();
    }

    @SuppressWarnings("unchecked")
    private PreferencesFragment getRequestedFragment() {
        Class<? extends PreferencesFragment> fragmentType = (Class<? extends PreferencesFragment>) getIntent().getSerializableExtra("fragment");
        if (fragmentType == null) {
            return null;
        }

        try {
            return fragmentType.newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    private class FragmentResumeListener extends FragmentManager.FragmentLifecycleCallbacks {
        @Override
        public void onFragmentStarted(@NonNull FragmentManager fm, @NonNull Fragment f) {
            if (f instanceof MainPreferencesFragment) {
                setTitle(R.string.action_settings);
            } else if (f instanceof AppearancePreferencesFragment) {
                _prefTitle = getString(R.string.pref_section_appearance_title);
                setTitle(_prefTitle);
            } else if (_titleFromScreen && f instanceof PreferenceFragmentCompat
                    && ((PreferenceFragmentCompat) f).getPreferenceScreen() != null) {
                // a page opened directly (backups from the error bar, the keyboard from the
                // system settings) takes the title its screen has
                _titleFromScreen = false;
                _prefTitle = ((PreferenceFragmentCompat) f).getPreferenceScreen().getTitle();
                setTitle(_prefTitle);
            }
        }
    }
}

package io.cueapp.android;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnitRunner;

import io.cueapp.android.util.IOUtils;

public class CueTestRunner extends AndroidJUnitRunner {
    static {
        BuildConfig.TEST.set(true);
    }

    @Override
    public Application newApplication(ClassLoader cl, String name, Context context)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return Instrumentation.newApplication(CueTestApplication_Application.class, context);
    }

    @Override
    public void callApplicationOnCreate(Application app) {
        Context context = app.getApplicationContext();

        // clear internal storage so that there is no vault file
        IOUtils.clearDirectory(context.getFilesDir(), false);

        // clear preferences so that the intro is started from MainActivity
        ApplicationProvider.getApplicationContext().getFilesDir();
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .clear()
                .apply();

        super.callApplicationOnCreate(app);
    }
}

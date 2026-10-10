package io.cueapp.android.widget;

import io.cueapp.android.Preferences;
import io.cueapp.android.vault.VaultManager;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@EntryPoint
@InstallIn(SingletonComponent.class)
public interface WidgetEntryPoint {
    VaultManager getVaultManager();
    Preferences getPreferences();
}

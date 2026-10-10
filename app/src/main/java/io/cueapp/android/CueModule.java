package io.cueapp.android;

import android.content.Context;

import androidx.room.Room;

import io.cueapp.android.database.AppDatabase;
import io.cueapp.android.database.AuditLogDao;
import io.cueapp.android.database.AuditLogRepository;
import io.cueapp.android.icons.IconPackManager;
import io.cueapp.android.vault.VaultManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class CueModule {
    @Provides
    @Singleton
    public static IconPackManager provideIconPackManager(@ApplicationContext Context context) {
        IconPackManager manager = new IconPackManager(context);
        // install the packs that ship in the APK without holding up the first screen
        Thread thread = new Thread(() -> manager.installBundledPacks(context), "bundled-icon-packs");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return manager;
    }

    @Provides
    @Singleton
    public static AuditLogRepository provideAuditLogRepository(AppDatabase appDatabase) {
        AuditLogDao auditLogDao = appDatabase.auditLogDao();
        return new AuditLogRepository(auditLogDao);
    }

    @Provides
    @Singleton
    public static VaultManager provideVaultManager(@ApplicationContext Context context, AuditLogRepository auditLogRepository) {
        return new VaultManager(context, auditLogRepository);
    }

    @Provides
    public static Preferences providePreferences(@ApplicationContext Context context) {
        return new Preferences(context);
    }

    @Provides
    @Singleton
    public static AppDatabase provideAppDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(context.getApplicationContext(),
                        AppDatabase.class, "cue-db")
                .build();
    }
}

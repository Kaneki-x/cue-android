package io.cueapp.android.database;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import io.cueapp.android.EventType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
public class AuditLogDaoTest {
    private AppDatabase _db;
    private AuditLogDao _dao;

    @Before
    public void setUp() {
        _db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .setQueryExecutor(Runnable::run)
                .build();
        _dao = _db.auditLogDao();
    }

    @After
    public void tearDown() {
        _db.close();
    }

    @Test
    public void testShowsTheLast30Days() {
        long now = System.currentTimeMillis();
        _dao.insert(new AuditLogEntry(0, EventType.VAULT_EXPORTED, null, now - TimeUnit.DAYS.toMillis(29)));
        _dao.insert(new AuditLogEntry(0, EventType.VAULT_UNLOCKED, null, now));
        _dao.insert(new AuditLogEntry(0, EventType.VAULT_BACKUP_CREATED, null, now - TimeUnit.DAYS.toMillis(31)));

        // timestamps are in milliseconds: before, the 30-day window compared them with seconds
        assertEquals(Arrays.asList(EventType.VAULT_UNLOCKED, EventType.VAULT_EXPORTED), types(getValue(_dao.getAll())));
    }

    @Test
    public void testDeletesEventsOlderThan30Days() {
        long now = System.currentTimeMillis();
        _dao.insert(new AuditLogEntry(0, EventType.VAULT_UNLOCKED, null, now));
        _dao.insert(new AuditLogEntry(0, EventType.VAULT_BACKUP_CREATED, null, now - TimeUnit.DAYS.toMillis(31)));
        _dao.insert(new AuditLogEntry(0, EventType.ENTRY_SHARED, "ref", now - TimeUnit.DAYS.toMillis(400)));

        assertEquals(2, _dao.deleteOlderThan(now - AuditLogRepository.RETENTION_MILLIS));
        assertEquals(0, _dao.deleteOlderThan(now - AuditLogRepository.RETENTION_MILLIS));
        assertEquals(Arrays.asList(EventType.VAULT_UNLOCKED), types(getValue(_dao.getAll())));
    }

    private static List<EventType> types(List<AuditLogEntry> entries) {
        return entries.stream().map(AuditLogEntry::getEventType).collect(Collectors.toList());
    }

    private static <T> T getValue(LiveData<T> liveData) {
        AtomicReference<T> value = new AtomicReference<>();
        Observer<T> observer = value::set;
        liveData.observeForever(observer);
        shadowOf(Looper.getMainLooper()).idle();
        liveData.removeObserver(observer);
        return value.get();
    }
}

package io.cueapp.android.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AuditLogDao {
    @Insert
    void insert(AuditLogEntry log);

    // timestamp is in milliseconds (System.currentTimeMillis()), strftime('%s') in seconds
    @Query("SELECT * FROM audit_logs WHERE timestamp >= strftime('%s', 'now', '-30 days') * 1000 ORDER BY timestamp DESC")
    LiveData<List<AuditLogEntry>> getAll();

    @Query("DELETE FROM audit_logs WHERE timestamp < :timestamp")
    int deleteOlderThan(long timestamp);
}

package com.capslock.raft.core.storage;

import com.capslock.raft.core.model.LogEntry;

import java.util.List;

/**
 * Created by alvin.
 */
public interface LogStorage {
    long getFirstAvailableIndex();

    long getLastLogIndex();

    long getStartIndex();

    LogEntry getLastLogEntry();

    long append(LogEntry logEntry);

    void writeAt(long index, LogEntry logEntry);

    List<LogEntry> getLogEntries(long start, long end);

    LogEntry getLogEntryAt(long index);
}

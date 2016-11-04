package com.capslock.raft.core;

import java.util.List;

/**
 * Created by alvin.
 */
public interface LogStorage {
    long getFirstAvailableIndex();

    long getStartIndex();

    LogEntry getLastLogEntry();

    long append(LogEntry logEntry);

    void writeAt(long index, LogEntry logEntry);

    List<LogEntry> getLogEntries(long start, long end);

    LogEntry getLogEntryAt(long index);
}

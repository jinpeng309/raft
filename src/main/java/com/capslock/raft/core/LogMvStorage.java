package com.capslock.raft.core;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by alvin.
 */
public class LogMvStorage implements LogStorage {
    private MVMap<Long, LogEntry> logEntryMVMap;

    @PostConstruct
    public void init() {
        final MVStore mvStore = MVStore.open("log");
        logEntryMVMap = mvStore.openMap("logMap");
    }

    @Override
    public long getFirstAvailableIndex() {
        return logEntryMVMap.lastKey() + 1;
    }

    @Override
    public long getStartIndex() {
        return 1;
    }

    @Override
    public LogEntry getLastLogEntry() {
        return logEntryMVMap.get(logEntryMVMap.lastKey());
    }

    @Override
    public synchronized long append(final LogEntry logEntry) {
        logEntryMVMap.put(getFirstAvailableIndex(), logEntry);
        return 0;
    }

    @Override
    public void writeAt(final long index, final LogEntry logEntry) {
        logEntryMVMap.put(index, logEntry);
    }

    @Override
    public List<LogEntry> getLogEntries(final long start, final long end) {
        final List<LogEntry> logEntries = new ArrayList<>();
        for (long i = start; i < end; i++) {
            final LogEntry logEntry = logEntryMVMap.get(i);
            if (logEntry == null) {
                System.err.println("log is crash");
                System.exit(1);
            } else {
                logEntries.add(logEntryMVMap.get(i));
            }
        }
        return logEntries;
    }

    @Override
    public LogEntry getLogEntryAt(final long index) {
        return logEntryMVMap.get(index);
    }
}

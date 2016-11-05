package com.capslock.raft.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Created by alvin.
 */
@Data
@AllArgsConstructor
public class LogEntry {
    private long term;
    private byte[] value;
    private LogType type;

    public LogEntry() {
    }

    public enum LogType {
        LOG, CONFIG
    }
}

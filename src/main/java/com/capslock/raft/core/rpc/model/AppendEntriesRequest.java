package com.capslock.raft.core.rpc.model;

import com.capslock.raft.core.model.LogEntry;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Created by alvin.
 */
@Data
@AllArgsConstructor
public class AppendEntriesRequest {
    private final long term;
    private List<LogEntry> logEntries;
}

package com.capslock.raft.core.rpc.model;

import com.capslock.raft.core.model.Endpoint;
import com.capslock.raft.core.model.LogEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Created by alvin.
 */
@Data
@AllArgsConstructor
@Builder
public class AppendEntriesRequest {
    private Endpoint source;
    private long term;
    private long lastLogTerm;
    private long lastLogIndex;
    private List<LogEntry> logEntries;
    private long leaderCommittedLogIndex;

    public AppendEntriesRequest() {
    }
}

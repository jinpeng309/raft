package com.capslock.raft.core.rpc.model;

import com.capslock.raft.core.model.LogEntryId;
import lombok.Data;

/**
 * Created by alvin.
 */
@Data
public class AppendEntriesResponse {
    private LogEntryId lastLogEntryId;
    private boolean accepted;
}

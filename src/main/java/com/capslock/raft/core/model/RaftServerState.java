package com.capslock.raft.core.model;

import lombok.Data;

/**
 * Created by alvin.
 */
@Data
public class RaftServerState {
    private long term;
    private long committedLogIndex;
    private Endpoint voteFor;

    public void increaseTerm() {
        term += 1;
    }
}

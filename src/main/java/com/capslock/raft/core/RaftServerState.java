package com.capslock.raft.core;

import lombok.Data;

/**
 * Created by alvin.
 */
@Data
public class RaftServerState {
    private long term;
    private long committedLogIndex;
    private Endpoint voteFor;
}

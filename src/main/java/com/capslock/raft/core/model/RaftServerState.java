package com.capslock.raft.core.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Created by alvin.
 */
@Data
public class RaftServerState implements Serializable{
    private long term;
    private long committedLogIndex;
    private Endpoint voteFor;

    public void increaseTerm() {
        term += 1;
    }
}

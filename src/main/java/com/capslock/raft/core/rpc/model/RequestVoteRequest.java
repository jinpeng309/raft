package com.capslock.raft.core.rpc.model;

import com.capslock.raft.core.model.Endpoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Created by alvin.
 */
@AllArgsConstructor
@Data
@Builder
public class RequestVoteRequest {
    private long term;
    private long lastLogIndex;
    private long lastLogTerm;
    private Endpoint source;
    private Endpoint destination;

    public RequestVoteRequest() {

    }
}

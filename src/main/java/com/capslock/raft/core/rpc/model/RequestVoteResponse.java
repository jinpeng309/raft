package com.capslock.raft.core.rpc.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Created by alvin.
 */
@AllArgsConstructor
@Data
public class RequestVoteResponse {
    private boolean accepted;

    public RequestVoteResponse() {
    }
}

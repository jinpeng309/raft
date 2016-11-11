package com.capslock.raft.core.rpc.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Created by alvin.
 */
@Data
@AllArgsConstructor
public class CommitRequest {
    private long logIndex;

    public CommitRequest() {
    }
}

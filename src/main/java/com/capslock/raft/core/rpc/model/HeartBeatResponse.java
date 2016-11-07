package com.capslock.raft.core.rpc.model;

import lombok.Data;

/**
 * Created by alvin.
 */
@Data
public class HeartBeatResponse {
    private boolean accepted = true;

}

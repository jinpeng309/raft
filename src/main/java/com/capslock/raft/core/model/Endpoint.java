package com.capslock.raft.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Created by alvin.
 */
@Data
@AllArgsConstructor
public class Endpoint {
    private String host;
    private int port;

    public Endpoint() {
    }
}

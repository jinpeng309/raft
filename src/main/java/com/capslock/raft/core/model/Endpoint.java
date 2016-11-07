package com.capslock.raft.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * Created by alvin.
 */
@Data
@AllArgsConstructor
public class Endpoint implements Serializable {
    private String host;
    private int port;

    public Endpoint() {
    }
}

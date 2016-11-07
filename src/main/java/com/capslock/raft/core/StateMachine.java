package com.capslock.raft.core;

/**
 * Created by alvin.
 */
public interface StateMachine {
    void commit(byte[] data);
}

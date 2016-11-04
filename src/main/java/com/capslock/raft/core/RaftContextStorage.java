package com.capslock.raft.core;

/**
 * Created by alvin.
 */
public interface RaftContextStorage {
    void saveState(final RaftServerState state);

    RaftServerState getState();
}

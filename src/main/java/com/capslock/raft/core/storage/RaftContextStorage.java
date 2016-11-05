package com.capslock.raft.core.storage;

import com.capslock.raft.core.model.RaftServerState;

/**
 * Created by alvin.
 */
public interface RaftContextStorage {
    void saveState(final RaftServerState state);

    RaftServerState getState();
}

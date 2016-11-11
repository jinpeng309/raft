package com.capslock.raft.core;

import java.util.List;

/**
 * Created by alvin.
 */
public interface StateMachine {
    void commit(List<byte[]> dataList);
}

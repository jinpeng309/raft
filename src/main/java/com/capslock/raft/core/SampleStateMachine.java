package com.capslock.raft.core;

import org.springframework.stereotype.Component;

/**
 * Created by alvin.
 */
@Component
public class SampleStateMachine implements StateMachine {
    @Override
    public void commit(final byte[] data) {

    }
}

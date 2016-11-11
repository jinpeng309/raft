package com.capslock.raft.core;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Created by alvin.
 */
@Component
public class SampleStateMachine implements StateMachine {
    @Override
    public void commit(final List<byte[]> dataList) {
        System.out.println("commit to stateMachine");
        dataList.stream().map(String::new).forEach(System.out::println);
    }
}

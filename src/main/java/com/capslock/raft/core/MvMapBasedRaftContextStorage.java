package com.capslock.raft.core;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Created by alvin.
 */
@Component
public class MvMapBasedRaftContextStorage implements RaftContextStorage {
    private static final String STATE_KEY = "STATE";
    private MVMap<String, Object> contextMap;
    private MVStore mvStore;

    @PostConstruct
    public void init() {
        mvStore = MVStore.open("raft");
        contextMap = mvStore.openMap("context");
    }

    @PreDestroy
    public void destroy() {
        mvStore.commit();
        mvStore.close();
    }

    @Override
    public void saveState(final RaftServerState state) {
        contextMap.put(STATE_KEY, state);
    }

    @Override
    public RaftServerState getState() {
        return (RaftServerState) contextMap.get(STATE_KEY);
    }
}

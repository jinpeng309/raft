package com.capslock.raft.core;

import com.capslock.raft.core.rpc.model.AppendEntriesRequest;
import com.capslock.raft.core.rpc.model.RequestVoteRequest;
import com.capslock.raft.core.rpc.model.RequestVoteResponse;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by alvin.
 */
@Component
public class RaftService {
    private static final int ELECTION_TIME_OUT = 1000;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    private Role role = Role.FOLLOWER;

    public RequestVoteResponse handleRequestVote(final RequestVoteRequest request) {
        return new RequestVoteResponse(true);
    }

    public void handleAppendEntries(final AppendEntriesRequest request) {
    }
}

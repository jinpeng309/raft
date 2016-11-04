package com.capslock.raft.core;

import com.capslock.raft.core.rpc.RpcClient;
import com.capslock.raft.core.rpc.model.AppendEntriesRequest;
import com.capslock.raft.core.rpc.model.AppendEntriesResponse;
import com.capslock.raft.core.rpc.model.RequestVoteRequest;
import com.capslock.raft.core.rpc.model.RequestVoteResponse;
import io.reactivex.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by alvin.
 */
@Component
public class RaftService {
    private static final int ELECTION_TIME_OUT = 1000;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> electionTask;
    private Role role = Role.FOLLOWER;
    private ConcurrentHashMap<Endpoint, RpcClient> rpcClientMap = new ConcurrentHashMap<>();
    private Endpoint localEndpoint;
    private RaftServerState raftServerState;
    private AtomicInteger votesGranted = new AtomicInteger(0);
    private AtomicInteger votesResponsed = new AtomicInteger(0);
    private volatile boolean electCompleted = false;

    @Autowired
    private RaftContextStorage raftContextStorage;
    @Autowired
    private LogStorage logStorage;

    @PostConstruct
    public void init() throws UnknownHostException {
        final String localHost = Inet4Address.getLocalHost().getHostAddress();
        localEndpoint = new Endpoint(localHost, 8081);
        raftServerState = raftContextStorage.getState();
        if (raftServerState == null) {
            raftServerState = new RaftServerState();
            saveState();
        }
        startElectionTimer();
    }

    private void saveState() {
        raftContextStorage.saveState(raftServerState);
    }

    private void startElectionTimer() {
        electionTask = scheduledExecutorService.schedule(this::startElect, ELECTION_TIME_OUT, TimeUnit.MILLISECONDS);
    }

    public void startElect() {
        role = Role.CANDIDATE;
        electCompleted = false;
        raftServerState.setVoteFor(localEndpoint);
        votesGranted.addAndGet(1);
        votesResponsed.addAndGet(1);

        rpcClientMap.forEachEntry(rpcClientMap.size(), entry -> {
            requestVote(entry.getKey(), entry.getValue());
        });
    }

    public void requestVote(final Endpoint destination, final RpcClient rpcClient) {
        final long lastLogIndex = logStorage.getFirstAvailableIndex() - 1;
        final long lastLogTerm = logStorage.getLogEntryAt(lastLogIndex).getTerm();

        final RequestVoteRequest request = RequestVoteRequest
                .builder()
                .term(raftServerState.getTerm())
                .lastLogTerm(lastLogTerm)
                .lastLogIndex(lastLogIndex)
                .source(localEndpoint)
                .destination(destination)
                .build();

        rpcClient.requestVote(request)
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.computation())
                .subscribe(response -> {
                    this.votesResponsed.addAndGet(1);
                    if (electCompleted) {
                        return;
                    }
                    if (response.isAccepted()) {
                        processVoteRequestGranted();
                    } else {
                        processVoteRequestRejected(response);
                    }
                });
    }

    private void processVoteRequestGranted() {
        final int granted = this.votesGranted.addAndGet(1);
        if (granted > (rpcClientMap.size() + 1) / 2) {
            this.electCompleted = true;
            becomeLeader();
        }
    }

    private void processVoteRequestRejected(final RequestVoteResponse response) {

    }

    private void becomeLeader() {
        role = Role.LEADER;
    }

    public synchronized RequestVoteResponse handleRequestVote(final RequestVoteRequest request) {
        resetElectionTask();
        updateTerm(request.getTerm());
        //paper 5.4.2
        final long localLastLogIndex = logStorage.getLastLogIndex();
        final long localLastLogTerm = logStorage.getLogEntryAt(localLastLogIndex).getTerm();
        final boolean logOkay = request.getLastLogTerm() > localLastLogTerm
                || (localLastLogTerm == request.getLastLogTerm() && localLastLogIndex <= request.getLastLogIndex());
        final boolean grant = raftServerState.getTerm() == request.getTerm()
                && logOkay
                && (raftServerState.getVoteFor() == null || Objects.equals(raftServerState.getVoteFor(), request.getSource()));

        final RequestVoteResponse response = new RequestVoteResponse(grant);
        if (grant) {
            raftServerState.setVoteFor(request.getSource());
            saveState();
        }
        return response;
    }

    public void resetElectionTask() {
        electionTask.cancel(false);
        startElectionTimer();
    }

    private void updateTerm(final long term) {
        if (term > raftServerState.getTerm()) {
            raftServerState.setTerm(term);
            raftServerState.setCommittedLogIndex(0);
            raftServerState.setVoteFor(null);
            role = Role.FOLLOWER;
            saveState();
        }
    }

    public AppendEntriesResponse handleAppendEntries(final AppendEntriesRequest request) {
        resetElectionTask();
        updateTerm(request.getTerm());
        return new AppendEntriesResponse();
    }
}

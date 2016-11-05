package com.capslock.raft.core;

import com.capslock.raft.core.model.Endpoint;
import com.capslock.raft.core.model.LogEntry;
import com.capslock.raft.core.model.RaftServerState;
import com.capslock.raft.core.model.Role;
import com.capslock.raft.core.rpc.RpcClient;
import com.capslock.raft.core.rpc.RpcClientFactory;
import com.capslock.raft.core.rpc.model.AppendEntriesRequest;
import com.capslock.raft.core.rpc.model.AppendEntriesResponse;
import com.capslock.raft.core.rpc.model.RequestVoteRequest;
import com.capslock.raft.core.rpc.model.RequestVoteResponse;
import com.capslock.raft.core.storage.LogStorage;
import com.capslock.raft.core.storage.RaftContextStorage;
import com.google.common.net.HostAndPort;
import io.reactivex.schedulers.Schedulers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by alvin.
 */
@Component
public class RaftService {
    private static final int ELECTION_TIME_OUT = 3000;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> electionTask;
    private Role role = Role.FOLLOWER;
    private ConcurrentHashMap<Endpoint, RpcClient> rpcClientMap = new ConcurrentHashMap<>();
    private Endpoint localEndpoint;
    private RaftServerState raftServerState;
    private AtomicInteger voteGranted = new AtomicInteger(0);
    private AtomicInteger voteResponsed = new AtomicInteger(0);
    private volatile boolean electCompleted = false;
    @Value("#{'${cluster.nodes}'.split(',')}")
    private List<String> rawClusterNodeList;

    @Autowired
    private RaftContextStorage raftContextStorage;
    @Autowired
    private LogStorage logStorage;
    @Autowired
    private RpcClientFactory rpcClientFactory;

    @PostConstruct
    public void init() throws UnknownHostException {
        final String localHost = Inet4Address.getLocalHost().getHostAddress();
        localEndpoint = new Endpoint(localHost, 8081);
        raftServerState = raftContextStorage.getState();
        if (raftServerState == null) {
            raftServerState = new RaftServerState();
            saveState();
        }
        rawClusterNodeList
                .stream()
                .map(address -> {
                    final HostAndPort hostAndPort = HostAndPort.fromString(address);
                    return new Endpoint(hostAndPort.getHostText(), hostAndPort.getPort());
                })
                .filter(otherEndPoint -> !otherEndPoint.equals(localEndpoint))
                .forEach(endpoint -> rpcClientMap.put(endpoint, rpcClientFactory.createRpcClient(endpoint)));

        startElectionTimer();
    }

    private void saveState() {
        raftContextStorage.saveState(raftServerState);
    }

    private void startElectionTimer() {
        electionTask = scheduledExecutorService.schedule(this::startElect, new Random().nextInt(ELECTION_TIME_OUT), TimeUnit.MILLISECONDS);
    }

    public void startElect() {
        becomeCandidate();

        raftServerState.increaseTerm();
        raftServerState.setVoteFor(localEndpoint);
        voteGranted.set(1);
        voteResponsed.set(1);
        electCompleted = false;

        rpcClientMap.forEachEntry(rpcClientMap.size(), entry -> {
            requestVote(entry.getKey(), entry.getValue());
        });
    }

    public void requestVote(final Endpoint destination, final RpcClient rpcClient) {
        final long lastLogIndex = logStorage.getFirstAvailableIndex() - 1;
        final LogEntry lastLogEntry = logStorage.getLogEntryAt(lastLogIndex);
        final long lastLogTerm = lastLogEntry == null ? 0 : lastLogEntry.getTerm();

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
                .doOnError(throwable -> {
                    if (voteResponsed.addAndGet(1) == clusterSize()) {
                        electCompleted = true;
                    }
                })
                .doOnNext(response -> {
                    this.voteResponsed.addAndGet(1);
                    if (electCompleted) {
                        return;
                    }
                    if (response.isAccepted()) {
                        processVoteRequestGranted();
                    } else {
                        processVoteRequestRejected(response);
                    }
                })
                .subscribe();
    }

    private int clusterSize() {
        return rpcClientMap.size() + 1;
    }

    private void processVoteRequestGranted() {
        final int granted = this.voteGranted.addAndGet(1);
        if (granted > majorSize()) {
            this.electCompleted = true;
            becomeLeader();
        }
    }

    private int majorSize() {
        return (rpcClientMap.size() + 2) / 2;
    }

    private void processVoteRequestRejected(final RequestVoteResponse response) {
        if (response.getTerm() > raftServerState.getTerm()) {

        }
    }

    private void becomeLeader() {
        role = Role.LEADER;
    }

    private void becomeCandidate() {
        role = Role.CANDIDATE;
    }

    private void becomeFollower() {
        role = Role.FOLLOWER;
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

        final RequestVoteResponse response = new RequestVoteResponse(grant, raftServerState.getTerm());
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
            becomeFollower();
            saveState();
        }
    }

    public AppendEntriesResponse handleAppendEntries(final AppendEntriesRequest request) {
        resetElectionTask();
        updateTerm(request.getTerm());
        return new AppendEntriesResponse();
    }
}

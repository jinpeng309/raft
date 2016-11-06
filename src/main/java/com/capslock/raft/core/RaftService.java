package com.capslock.raft.core;

import com.capslock.raft.core.model.*;
import com.capslock.raft.core.rpc.RpcClientFactory;
import com.capslock.raft.core.rpc.model.AppendEntriesRequest;
import com.capslock.raft.core.rpc.model.AppendEntriesResponse;
import com.capslock.raft.core.rpc.model.RequestVoteRequest;
import com.capslock.raft.core.rpc.model.RequestVoteResponse;
import com.capslock.raft.core.storage.LogStorage;
import com.capslock.raft.core.storage.RaftContextStorage;
import com.google.common.net.HostAndPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by alvin.
 */
@Component
public class RaftService {
    private static final int ELECTION_TIME_OUT = 3000;
    private static final int MAX_APPEND_SIZE = 10;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> electionTask;
    private Role role = Role.FOLLOWER;
    private Endpoint leader;
    private ConcurrentHashMap<Endpoint, RaftClusterNode> nodeMap = new ConcurrentHashMap<>();
    private Endpoint localEndpoint;
    private RaftServerState raftServerState;
    private AtomicInteger voteGranted = new AtomicInteger(0);
    private AtomicInteger voteResponsed = new AtomicInteger(0);
    private AtomicLong committedLogIndex = new AtomicLong(0);
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
                .forEach(endpoint -> {
                    final RaftClusterNode node = new RaftClusterNode(endpoint,
                            rpcClientFactory.createRpcClient(endpoint));
                    nodeMap.put(endpoint, node);
                });

        if (clusterSize() % 2 != 1) {
            System.err.println("node size must be odd");
            System.exit(1);
        }

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

        nodeMap.forEachEntry(nodeMap.size(), entry -> requestVote(entry.getValue()));
    }

    public void requestVote(final RaftClusterNode clusterNode) {
        final Endpoint destination = clusterNode.getEndpoint();

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

        clusterNode.requestVote(request)
                .doOnError(throwable -> {
                    if (voteResponsed.addAndGet(1) == clusterSize()) {
                        electCompleted = true;
                        if (role != Role.LEADER) {
                            startElectionTimer();
                        }
                    }
                })
                .doOnNext(response -> {
                    if (voteResponsed.addAndGet(1) == clusterSize()) {
                        if (role != Role.LEADER) {
                            startElectionTimer();
                        }
                    }
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
        return nodeMap.size() + 1;
    }

    private int majorSize() {
        return (clusterSize() + 1) / 2;
    }

    private void processVoteRequestGranted() {
        final int granted = this.voteGranted.addAndGet(1);
        if (granted > majorSize()) {
            this.electCompleted = true;
            becomeLeader();
        }
    }


    private void processVoteRequestRejected(final RequestVoteResponse response) {
        if (response.getTerm() > raftServerState.getTerm()) {

        }
    }

    private void becomeLeader() {
        role = Role.LEADER;
        cancelElectionTask();
        leader = localEndpoint;
        appendLogEntriesToFollowers();
    }

    private long getTermForLogIndex(final long logIndex) {
        final LogEntry logEntry = logStorage.getLogEntryAt(logIndex);
        return logEntry == null ? 0 : logEntry.getTerm();
    }

    private void appendLogEntriesToFollowers() {
        nodeMap.forEachValue(nodeMap.size(), this::appendLogEntriesToFollow);
    }

    private void appendLogEntriesToFollow(final RaftClusterNode clusterNode) {
        if (clusterNode.isAppending()) {
            return;
        }

        final long currentNextLogIndex = logStorage.getFirstAvailableIndex();
        if (clusterNode.getNextLogIndex() == 0) {
            clusterNode.setNextLogIndex(currentNextLogIndex);
        }
        final long lastLogIndex = clusterNode.getNextLogIndex() - 1;
        final long lastLogTerm = getTermForLogIndex(lastLogIndex);
        final long endLogIndex = Math.min(currentNextLogIndex, lastLogIndex + 1 + MAX_APPEND_SIZE);
        final List<LogEntry> logEntries = lastLogIndex + 1 > endLogIndex ?
                Collections.EMPTY_LIST : logStorage.getLogEntries(lastLogIndex, endLogIndex);

        if (!logEntries.isEmpty()) {
            final AppendEntriesRequest request = AppendEntriesRequest
                    .builder()
                    .source(localEndpoint)
                    .lastLogTerm(lastLogTerm)
                    .lastLogIndex(lastLogIndex)
                    .logEntries(logEntries)
                    .leaderCommittedLogIndex(committedLogIndex.get())
                    .build();

            clusterNode
                    .appendLogEntries(request)
                    .doOnNext(System.out::println)
                    .doOnComplete(() -> appendLogEntriesToFollow(clusterNode))
                    .subscribe();
        }
    }

    private void becomeCandidate() {
        role = Role.CANDIDATE;
    }

    private void becomeFollower() {
        role = Role.FOLLOWER;
    }

    public synchronized RequestVoteResponse processRequestVote(final RequestVoteRequest request) {
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

    public AppendEntriesResponse processAppendEntries(final AppendEntriesRequest request) {
        resetElectionTask();
        updateTerm(request.getTerm());
        return new AppendEntriesResponse();
    }

    public void resetElectionTask() {
        electionTask.cancel(false);
        startElectionTimer();
    }

    public void cancelElectionTask() {
        electionTask.cancel(false);
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
}

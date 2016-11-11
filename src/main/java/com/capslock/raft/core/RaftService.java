package com.capslock.raft.core;

import com.capslock.raft.core.model.Endpoint;
import com.capslock.raft.core.model.LogEntry;
import com.capslock.raft.core.model.LogEntryId;
import com.capslock.raft.core.model.RaftClusterNode;
import com.capslock.raft.core.model.RaftServerState;
import com.capslock.raft.core.model.Role;
import com.capslock.raft.core.rpc.RpcClientFactory;
import com.capslock.raft.core.rpc.model.AppendEntriesRequest;
import com.capslock.raft.core.rpc.model.AppendEntriesResponse;
import com.capslock.raft.core.rpc.model.CommitRequest;
import com.capslock.raft.core.rpc.model.CommitResponse;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Created by alvin.
 */
@Component
public class RaftService {
    private static final int ELECTION_TIME_OUT = 5000;
    private static final int MAX_APPEND_SIZE = 10;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledExecutorService heartBeatScheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
    private List<ScheduledFuture<?>> heartBeatTasks = new CopyOnWriteArrayList<>();
    private ScheduledFuture<?> electionTask;
    private Role role = Role.FOLLOWER;
    private Endpoint leader;
    private ConcurrentHashMap<Endpoint, RaftClusterNode> clusterNodeMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<LogEntryId, Integer> logEntryAppendCountMap = new ConcurrentHashMap<>();
    private Endpoint localEndpoint;
    private RaftServerState raftServerState;
    private AtomicInteger voteGranted = new AtomicInteger(0);
    private AtomicInteger voteResponsed = new AtomicInteger(0);
    private AtomicLong committedLogIndex = new AtomicLong(0);
    private volatile boolean electCompleted = false;
    @Value("#{'${cluster.nodes}'.split(',')}")
    private List<String> rawClusterNodeList;
    private Logger logger = Logger.getLogger("service");
    @Autowired
    private RaftContextStorage raftContextStorage;
    @Autowired
    private LogStorage logStorage;
    @Autowired
    private RpcClientFactory rpcClientFactory;
    @Value("${server.port}")
    private int servicePort;
    @Autowired
    private StateMachine stateMachine;

    @PostConstruct
    public void init() throws UnknownHostException {
        final String localHost = Inet4Address.getLocalHost().getHostAddress();
        localEndpoint = new Endpoint(localHost, servicePort);
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
                    clusterNodeMap.put(endpoint, node);
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
        final int timeout = 20000 + new Random(System.currentTimeMillis()).nextInt(10000);
        electionTask = scheduledExecutorService.schedule(this::startElect, timeout, TimeUnit.MILLISECONDS);
    }

    public void startElect() {
        if (clusterSize() == 1) {
            becomeLeader();
        }
        logger.info("start elect");
        becomeCandidate();

        raftServerState.increaseTerm();
        raftServerState.setVoteFor(localEndpoint);
        voteGranted.set(1);
        voteResponsed.set(1);
        electCompleted = false;

        clusterNodeMap.forEachEntry(clusterNodeMap.size(), entry -> requestVote(entry.getValue()));
    }

    public void processClientRequest(final byte[] data) {
        if (role == Role.LEADER) {
            final LogEntry logEntry = new LogEntry(raftServerState.getTerm(), data, LogEntry.LogType.LOG);
            logStorage.append(logEntry);
            appendLogEntriesToFollowers();
        }
    }

    public synchronized void requestVote(final RaftClusterNode clusterNode) {
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
                        if (electCompleted) {
                            return;
                        }
                        electCompleted = true;
                        if (role != Role.LEADER) {
                            resetElectionTask();
                        }
                    }
                })
                .doOnNext(response -> {
                    if (voteResponsed.addAndGet(1) == clusterSize()) {
                        if (role != Role.LEADER) {
                            resetElectionTask();
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
        return clusterNodeMap.size() + 1;
    }

    private int majorSize() {
        return (clusterSize() + 1) / 2;
    }

    private synchronized void processVoteRequestGranted() {
        final int granted = this.voteGranted.addAndGet(1);
        if (granted >= majorSize()) {
            this.electCompleted = true;
            becomeLeader();
        }
    }

    private void processVoteRequestRejected(final RequestVoteResponse response) {
        if (response.getTerm() > raftServerState.getTerm()) {

        }
    }

    private void becomeLeader() {
        logger.info("become leader");
        role = Role.LEADER;
        cancelElectionTask();
        leader = localEndpoint;
        for (int i = 0; i < 3; i++) {
            processClientRequest(("data-" + i).getBytes());
        }

        appendLogEntriesToFollowers();
        startHeartBeat();
    }

    private synchronized void startHeartBeat() {
        cancelHeartBeat();
        clusterNodeMap.forEachValue(clusterNodeMap.size(), clusterNode -> {
            ScheduledFuture<?> heartBeatTask = heartBeatScheduler.scheduleAtFixedRate(clusterNode::heartBeat, 0, 5, TimeUnit.SECONDS);
            heartBeatTasks.add(heartBeatTask);
        });
    }

    private synchronized void cancelHeartBeat() {
        heartBeatTasks.forEach(task -> task.cancel(false));
        heartBeatTasks.clear();
    }

    private long getTermForLogIndex(final long logIndex) {
        final LogEntry logEntry = logStorage.getLogEntryAt(logIndex);
        return logEntry == null ? 0 : logEntry.getTerm();
    }

    private synchronized void appendLogEntriesToFollowers() {
        clusterNodeMap.forEachValue(clusterNodeMap.size(), this::appendLogEntriesToFollow);
    }

    private synchronized void appendLogEntriesToFollow(final RaftClusterNode clusterNode) {
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
                    .doOnNext(appendResponse -> processAppendEntriesResponse(clusterNode, appendResponse))
                    .doOnComplete(() -> appendLogEntriesToFollow(clusterNode))
                    .subscribe();
        }
    }

    private void processAppendEntriesResponse(final RaftClusterNode clusterNode, final AppendEntriesResponse response) {
        logger.info("rcv append response " + response);

        if (response.isAccepted()) {
            final LogEntryId lastLogEntryId = response.getLastLogEntryId();
            final long lastLogEntryTerm = lastLogEntryId.getTerm();
            final long lastLogEntryIndex = lastLogEntryId.getLogIndex();
            clusterNode.setMatchedLogIndex(lastLogEntryIndex);
            clusterNode.setNextLogIndex(lastLogEntryIndex + 1);
            int appendNodeSize = 0;
            for (final RaftClusterNode raftClusterNode : clusterNodeMap.values()) {
                if (raftClusterNode.getNextLogIndex() > lastLogEntryIndex) {
                    appendNodeSize++;
                }
            }
            if (appendNodeSize >= majorSize() && lastLogEntryTerm == raftServerState.getTerm()) {
                this.committedLogIndex.set(lastLogEntryIndex);
                broadcastCommit();
            }
        }
    }

    private void broadcastCommit() {
        clusterNodeMap.forEachValue(clusterNodeMap.size(), clusterNode -> {
            clusterNode.commitLog(new CommitRequest(committedLogIndex.get()));
        });
    }

    private void becomeCandidate() {
        cancelHeartBeat();
        role = Role.CANDIDATE;
    }

    private void becomeFollower() {
        cancelHeartBeat();
        role = Role.FOLLOWER;
        voteGranted.set(0);
        voteResponsed.set(0);
        raftServerState.setVoteFor(null);
        raftContextStorage.saveState(raftServerState);
    }

    public synchronized RequestVoteResponse processRequestVote(final RequestVoteRequest request) {
        logger.info(request.toString());
        resetElectionTask();
        updateTerm(request.getTerm());
        //paper 5.4.2
        final long localLastLogIndex = logStorage.getLastLogIndex();
        final long localLastLogTerm = getTermForLogIndex(localLastLogIndex);
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
        logger.info("rcv append request " + request);
        resetElectionTask();
        updateTerm(request.getTerm());
        final AppendEntriesResponse response = new AppendEntriesResponse();
        response.setAccepted(true);
        request.getLogEntries().forEach(logStorage::append);
        LogEntry logEntry = logStorage.getLastLogEntry();
        response.setLastLogEntryId(new LogEntryId(logEntry.getTerm(), logStorage.getLastLogIndex()));
        logger.info("return response " + response);
        return response;
    }

    public void resetElectionTask() {
        electionTask.cancel(false);
        if (role != Role.LEADER) {
            startElectionTimer();
        }
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

    public void processHeartBeat() {
        logger.info("heart beat");
        if (role == Role.CANDIDATE) {
            becomeFollower();
        }
        resetElectionTask();
    }

    public CommitResponse commitLog(final CommitRequest request) {
        this.committedLogIndex.set(request.getLongIndex());
        return new CommitResponse(true);
    }
}

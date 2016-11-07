package com.capslock.raft.core.model;

import com.capslock.raft.core.rpc.RpcClient;
import com.capslock.raft.core.rpc.model.AppendEntriesRequest;
import com.capslock.raft.core.rpc.model.AppendEntriesResponse;
import com.capslock.raft.core.rpc.model.RequestVoteRequest;
import com.capslock.raft.core.rpc.model.RequestVoteResponse;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import lombok.Data;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by capslock1874.
 */
@Data
public class RaftClusterNode {
    private final Endpoint endpoint;
    private final RpcClient rpcClient;
    private long nextLogIndex = 0;
    private long matchedLogIndex = 0;
    private AtomicBoolean isAppending = new AtomicBoolean(false);

    public void setIsAppending(final boolean running) {
        isAppending.set(running);
    }

    public boolean isAppending() {
        return isAppending.get();
    }

    public synchronized Observable<AppendEntriesResponse> appendLogEntries(final AppendEntriesRequest request) {
        if (isAppending()) {
            return Observable.empty();
        }

        setIsAppending(true);
        return getRpcClient()
                .appendEntries(request)
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.computation())
                .retry()
                .doOnComplete(() -> setIsAppending(false));

    }

    public Observable<RequestVoteResponse> requestVote(final RequestVoteRequest request) {
        return getRpcClient()
                .requestVote(request)
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.computation());
    }

    public void heartBeat() {
        getRpcClient().heartBeat().observeOn(Schedulers.io()).subscribe();
    }
}

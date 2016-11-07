package com.capslock.raft.core.rpc;

import com.capslock.raft.core.rpc.model.AppendEntriesRequest;
import com.capslock.raft.core.rpc.model.AppendEntriesResponse;
import com.capslock.raft.core.rpc.model.HeartBeatResponse;
import com.capslock.raft.core.rpc.model.RequestVoteRequest;
import com.capslock.raft.core.rpc.model.RequestVoteResponse;
import io.reactivex.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * Created by alvin.
 */
public interface RpcClient {
    @POST("vote")
    Observable<RequestVoteResponse> requestVote(@Body final RequestVoteRequest requestVoteRequest);

    @POST("append-entries")
    Observable<AppendEntriesResponse> appendEntries(@Body final AppendEntriesRequest appendEntriesRequest);

    @GET("heart-beat")
    Observable<HeartBeatResponse> heartBeat();
}

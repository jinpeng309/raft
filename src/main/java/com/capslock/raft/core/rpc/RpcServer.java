package com.capslock.raft.core.rpc;

import com.capslock.raft.core.RaftService;
import com.capslock.raft.core.rpc.model.RequestVoteRequest;
import com.capslock.raft.core.rpc.model.RequestVoteResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by alvin.
 */
@RestController
public class RpcServer {
    @Autowired
    private RaftService raftService;

    @RequestMapping(value = "vote", method = RequestMethod.POST)
    public RequestVoteResponse requestVote(@RequestBody final RequestVoteRequest request) {
        return raftService.handleRequestVote(request);
    }
}

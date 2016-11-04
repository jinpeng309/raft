package com.capslock.raft.core.rpc.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

/**
 * Created by alvin.
 */
@Getter
@ToString
public class RequestVoteResponse {
    @JsonProperty("accepted")
    private final boolean accepted;

    @JsonCreator
    public RequestVoteResponse(@JsonProperty("accepted") final boolean accepted) {
        this.accepted = accepted;
    }
}

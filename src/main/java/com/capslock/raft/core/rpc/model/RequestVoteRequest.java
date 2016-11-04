package com.capslock.raft.core.rpc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

/**
 * Created by alvin.
 */
@Getter
@ToString
public class RequestVoteRequest {
    @JsonProperty("term")
    private final long term;
    @JsonProperty("lastLogIndex")
    private final long lastLogIndex;
    @JsonProperty("lastLogTerm")
    private final long lastLogTerm;

    @JsonProperty("source")
    private final String source;
    @JsonProperty("destination")
    private final String destination;

    public RequestVoteRequest(@JsonProperty("term") final long term,
            @JsonProperty("lastLogIndex") final long lastLogIndex,
            @JsonProperty("lastLogTerm") final long lastLogTerm,
            @JsonProperty("source") final String source,
            @JsonProperty("destination") final String destination) {
        this.term = term;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
        this.source = source;
        this.destination = destination;
    }
}

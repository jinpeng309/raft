package com.capslock.raft.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * Created by alvin.
 */
@Getter
public class LogEntry {
    @JsonProperty("term")
    private long term;
    @JsonProperty("value")
    private byte[] value;
    @JsonProperty("type")
    private LogType type;

    public LogEntry(@JsonProperty(value = "term", required = true) final long term,
            @JsonProperty(value = "value", required = true) final byte[] value,
            @JsonProperty(value = "type", required = true) final LogType type) {
        this.term = term;
        this.value = value;
        this.type = type;
    }

    public enum LogType {
        LOG, CONFIG
    }
}

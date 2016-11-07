package com.capslock.raft.core.model;

import com.google.common.collect.ComparisonChain;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Created by alvin.
 */
@Data
@AllArgsConstructor
public class LogEntryId implements Comparable<LogEntryId>{
    private long term;
    private long logIndex;

    public LogEntryId() {
    }

    @Override
    public int compareTo(final LogEntryId o) {
        return ComparisonChain.start()
                .compare(term, o.term)
                .compare(logIndex, o.getLogIndex())
                .result();
    }
}

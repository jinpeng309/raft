package com.capslock.raft.core.model;

import com.google.common.collect.ComparisonChain;
import lombok.Data;

/**
 * Created by alvin.
 */
@Data
public class LogEntryId implements Comparable<LogEntryId>{
    private final long term;
    private final long logIndex;

    @Override
    public int compareTo(final LogEntryId o) {
        return ComparisonChain.start()
                .compare(term, o.term)
                .compare(logIndex, o.getLogIndex())
                .result();
    }
}

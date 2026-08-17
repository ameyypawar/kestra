package io.kestra.core.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks when each job currently running under one set of metric tags started, so that a gauge can
 * report the longest-running one.
 *
 * <p>Only the maximum is reported rather than one series per job: identifying a job would mean
 * tagging by task run id, which multiplies the label cardinality of the metric by the number of
 * task runs.
 *
 * <p>Jobs are keyed by a token handed back at registration rather than by their start time, as two
 * jobs running under the same tags can start on the same tick.
 */
public class RunningDurations {
    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private final Map<Object, Long> startedAtNanos = new ConcurrentHashMap<>();

    /**
     * Record a job as started.
     *
     * @param nanos the reading of {@link System#nanoTime()} at which the job started
     * @return the token that must later be passed to {@link #finished(Object)}
     */
    public Object started(final long nanos) {
        Object token = new Object();
        this.startedAtNanos.put(token, nanos);
        return token;
    }

    /**
     * Record the job identified by this token as no longer running.
     *
     * <p>This must run even when the job fails: a token that is never returned leaves the gauge
     * reporting an ever-growing duration for a job that has already ended.
     */
    public void finished(final Object token) {
        this.startedAtNanos.remove(token);
    }

    /**
     * The duration in seconds of the longest job still running, or zero when none is.
     *
     * @param nowNanos the current reading of {@link System#nanoTime()}
     */
    public double maxDurationSeconds(final long nowNanos) {
        long oldest = Long.MAX_VALUE;
        for (long startedAt : this.startedAtNanos.values()) {
            oldest = Math.min(oldest, startedAt);
        }

        return oldest == Long.MAX_VALUE ? 0d : (nowNanos - oldest) / NANOS_PER_SECOND;
    }

    /**
     * The number of jobs currently tracked as running. Exposed for tests, to assert that tokens are
     * returned rather than leaked.
     */
    public int runningCount() {
        return this.startedAtNanos.size();
    }
}

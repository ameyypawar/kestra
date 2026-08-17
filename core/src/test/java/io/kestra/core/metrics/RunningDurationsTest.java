package io.kestra.core.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The clock is passed in rather than read, so every case here is exact rather than timed.
 */
class RunningDurationsTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void shouldReportZeroWhenNothingIsRunning() {
        RunningDurations durations = new RunningDurations();

        assertThat(durations.maxDurationSeconds(10 * SECOND)).isZero();
        assertThat(durations.runningCount()).isZero();
    }

    @Test
    void shouldReportTheElapsedTimeOfASingleJob() {
        RunningDurations durations = new RunningDurations();
        durations.started(4 * SECOND);

        assertThat(durations.maxDurationSeconds(10 * SECOND)).isEqualTo(6d);
    }

    @Test
    void shouldReportTheLongestRunningJobRatherThanTheLatest() {
        RunningDurations durations = new RunningDurations();
        durations.started(2 * SECOND);
        durations.started(9 * SECOND);

        assertThat(durations.maxDurationSeconds(10 * SECOND)).isEqualTo(8d);
        assertThat(durations.runningCount()).isEqualTo(2);
    }

    @Test
    void shouldFallBackToTheNextOldestWhenTheLongestEnds() {
        RunningDurations durations = new RunningDurations();
        Object oldest = durations.started(2 * SECOND);
        durations.started(9 * SECOND);

        durations.finished(oldest);

        assertThat(durations.maxDurationSeconds(10 * SECOND)).isEqualTo(1d);
    }

    @Test
    void shouldReturnToZeroOnceEveryJobHasEnded() {
        RunningDurations durations = new RunningDurations();
        Object first = durations.started(2 * SECOND);
        Object second = durations.started(9 * SECOND);

        durations.finished(first);
        durations.finished(second);

        assertThat(durations.maxDurationSeconds(10 * SECOND)).isZero();
        assertThat(durations.runningCount()).isZero();
    }

    /**
     * Jobs are keyed by token rather than by start time: two tasks dispatched together can read the
     * same tick, and keying by the instant would let one of them end both.
     */
    @Test
    void shouldTrackTwoJobsStartedOnTheSameTickSeparately() {
        RunningDurations durations = new RunningDurations();
        Object first = durations.started(3 * SECOND);
        durations.started(3 * SECOND);

        durations.finished(first);

        assertThat(durations.runningCount()).isEqualTo(1);
        assertThat(durations.maxDurationSeconds(10 * SECOND)).isEqualTo(7d);
    }

    @Test
    void shouldIgnoreATokenReturnedTwice() {
        RunningDurations durations = new RunningDurations();
        Object first = durations.started(3 * SECOND);
        durations.started(5 * SECOND);

        durations.finished(first);
        durations.finished(first);

        assertThat(durations.runningCount()).isEqualTo(1);
        assertThat(durations.maxDurationSeconds(10 * SECOND)).isEqualTo(5d);
    }
}

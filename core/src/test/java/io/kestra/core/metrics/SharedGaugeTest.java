package io.kestra.core.metrics;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
class SharedGaugeTest {
    @Inject
    private MetricRegistry metricRegistry;

    /**
     * The behaviour the shared accessors exist for. Micrometer keeps the supplier handed to the
     * first registration of a name and tag set and discards later ones, so a component that is
     * re-created per job — as the worker's task processor is — cannot register its own holder each
     * time.
     */
    @Test
    void registeringAFreshHolderEachTimeLeavesTheGaugeStale() {
        String name = "test.stale.gauge";
        String[] tags = {"probe", "stale"};

        AtomicInteger first = metricRegistry.gauge(name, "", new AtomicInteger(0), tags);
        AtomicInteger second = metricRegistry.gauge(name, "", new AtomicInteger(0), tags);

        assertThat(second).isNotSameAs(first);

        second.incrementAndGet();

        assertThat(second.get()).isEqualTo(1);
        assertThat(metricRegistry.find(name).tags(tags).gauge().value())
            .as("the second registration's holder is discarded, so the gauge still reads the first")
            .isZero();
    }

    @Test
    void sharedCountGaugeKeepsTheRegisteredHolderAndTheUpdatedOneTheSame() {
        String name = "test.shared.count";
        String[] tags = {"probe", "shared"};

        AtomicInteger first = metricRegistry.sharedCountGauge(name, "", tags);
        AtomicInteger second = metricRegistry.sharedCountGauge(name, "", tags);

        assertThat(second).isSameAs(first);

        second.incrementAndGet();

        assertThat(metricRegistry.find(name).tags(tags).gauge().value()).isEqualTo(1d);

        second.decrementAndGet();

        assertThat(metricRegistry.find(name).tags(tags).gauge().value()).isZero();
    }

    @Test
    void sharedCountGaugeSeparatesDistinctTagSets() {
        String name = "test.shared.count.tags";

        AtomicInteger one = metricRegistry.sharedCountGauge(name, "", "flow_id", "a");
        AtomicInteger other = metricRegistry.sharedCountGauge(name, "", "flow_id", "b");

        assertThat(other).isNotSameAs(one);

        one.incrementAndGet();

        assertThat(metricRegistry.find(name).tags("flow_id", "a").gauge().value()).isEqualTo(1d);
        assertThat(metricRegistry.find(name).tags("flow_id", "b").gauge().value()).isZero();
    }

    /**
     * A joined key would let {@code ["a b", "c"]} and {@code ["a", "b c"]} collide.
     */
    @Test
    void sharedCountGaugeSeparatesTagsThatWouldCollideIfJoined() {
        String name = "test.shared.count.collide";

        AtomicInteger one = metricRegistry.sharedCountGauge(name, "", "k", "a b", "c", "d");
        AtomicInteger other = metricRegistry.sharedCountGauge(name, "", "k", "a", "b c", "d");

        assertThat(other).isNotSameAs(one);
    }

    @Test
    void sharedDurationGaugeReportsTheLongestRunningJob() {
        String name = "test.shared.duration";
        String[] tags = {"probe", "duration"};

        RunningDurations durations = metricRegistry.sharedDurationGauge(name, "", tags);

        assertThat(metricRegistry.sharedDurationGauge(name, "", tags)).isSameAs(durations);
        assertThat(metricRegistry.find(name).tags(tags).gauge().value())
            .as("nothing running yet")
            .isZero();

        Object token = durations.started(System.nanoTime() - 5_000_000_000L);

        assertThat(metricRegistry.find(name).tags(tags).gauge().value())
            .as("a job started five seconds ago")
            .isGreaterThanOrEqualTo(5d);

        durations.finished(token);

        assertThat(metricRegistry.find(name).tags(tags).gauge().value())
            .as("back to zero once the job ends")
            .isZero();
    }
}

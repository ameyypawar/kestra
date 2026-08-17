package io.kestra.core.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.runners.FollowLogEvent;
import io.kestra.core.utils.IdUtils;

import io.micronaut.http.sse.Event;
import jakarta.inject.Inject;
import lombok.Builder;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class LogStreamingServiceTest {

    private static final String EXECUTION_ID = "exec-streaming-test";

    @Inject
    BroadcastQueueInterface<FollowLogEvent> queue;

    @Inject
    LogStreamingService service;

    @ParameterizedTest
    @FieldSource("filtersTestCases")
    void shouldDispatchOnlyMatchingEvents(FiltersTestCase testCase) {
        // Given
        String subscriberId = IdUtils.create();
        List<FollowLogEvent> received = new CopyOnWriteArrayList<>();

        Flux.<Event<FollowLogEvent>> create(
            sink -> service.registerSubscriber(EXECUTION_ID, subscriberId, sink, testCase.filters())
        )
            .doFinally(sig -> service.unregisterSubscriber(EXECUTION_ID, subscriberId))
            .subscribe(event -> received.add(event.getData()));

        // When
        testCase.events().forEach(event ->
        {
            try {
                queue.emit(event);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Then
        try {
            Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> received.size() >= testCase.expectedEvents().size());
        } finally {
            service.unregisterSubscriber(EXECUTION_ID, subscriberId);
        }

        assertThat(received)
            .usingRecursiveFieldByFieldElementComparatorOnFields("executionId", "level", "taskId", "taskRunId", "attemptNumber", "message")
            .containsExactlyInAnyOrderElementsOf(testCase.expectedEvents());
    }

    @Test
    void shouldNotLoseAnEventPublishedWhileTheHistoryIsBeingReplayed() {
        // Given a subscriber registered in buffering mode, standing in for the follow endpoint
        // between subscribing and finishing its repository read
        String subscriberId = IdUtils.create();
        List<FollowLogEvent> received = new CopyOnWriteArrayList<>();

        // A plain subscriber on the same execution: the fanout reaching it proves the queue dispatched,
        // so "nothing received yet" below cannot just mean the poll had not happened.
        CountDownLatch dispatched = new CountDownLatch(1);
        String probeId = IdUtils.create();
        Flux.<Event<FollowLogEvent>> create(
            sink -> service.registerSubscriber(EXECUTION_ID, probeId, sink, List.of())
        ).subscribe(event -> dispatched.countDown());


        Flux.<Event<FollowLogEvent>> create(
            sink -> service.registerBufferedSubscriber(EXECUTION_ID, subscriberId, sink, List.of())
        )
            .subscribe(event -> received.add(event.getData()));

        try {
            // When an event is published during that window
            FollowLogEvent duringReplay = event(Level.INFO, "load-data", "task-run-1", 0, "logged mid-replay");
            queue.emit(duringReplay);

            assertThat(dispatched.await(30, TimeUnit.SECONDS)).as("event must reach the fanout").isTrue();

            // Then it is held rather than dropped or emitted early
            Awaitility.await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                .until(() -> received.isEmpty());

            // And it is delivered once the replay finishes
            service.streamBufferedSubscriber(EXECUTION_ID, subscriberId, Set.of());

            Awaitility.await().atMost(Duration.ofSeconds(5)).pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> !received.isEmpty());
            assertThat(received)
                .as("an event published between subscribing and the end of the replay must still arrive (#10521)")
                .extracting(FollowLogEvent::message)
                .containsExactly("logged mid-replay");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            service.unregisterSubscriber(EXECUTION_ID, probeId);
            service.unregisterSubscriber(EXECUTION_ID, subscriberId);
        }
    }

    @Test
    void shouldNotRepeatAnEventTheReplayAlreadyEmitted() {
        // Given the same window, but the buffered event is one the caller's own read also returned
        String subscriberId = IdUtils.create();
        List<FollowLogEvent> received = new CopyOnWriteArrayList<>();

        // A plain subscriber on the same execution: the fanout reaching it proves the queue dispatched,
        // so "nothing received yet" below cannot just mean the poll had not happened.
        CountDownLatch dispatched = new CountDownLatch(1);
        String probeId = IdUtils.create();
        Flux.<Event<FollowLogEvent>> create(
            sink -> service.registerSubscriber(EXECUTION_ID, probeId, sink, List.of())
        ).subscribe(event -> dispatched.countDown());


        Flux.<Event<FollowLogEvent>> create(
            sink -> service.registerBufferedSubscriber(EXECUTION_ID, subscriberId, sink, List.of())
        )
            .subscribe(event -> received.add(event.getData()));

        try {
            FollowLogEvent alsoPersisted = event(Level.INFO, "load-data", "task-run-1", 0, "already replayed");
            queue.emit(alsoPersisted);

            assertThat(dispatched.await(30, TimeUnit.SECONDS)).as("event must reach the fanout").isTrue();

            Awaitility.await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                .until(() -> received.isEmpty());

            // When the replay reports having emitted it itself
            service.streamBufferedSubscriber(EXECUTION_ID, subscriberId, Set.of(alsoPersisted));

            // Then it is not sent a second time
            Awaitility.await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
                .until(() -> received.isEmpty());
            assertThat(received)
                .as("an entry the replay already emitted must not be duplicated by the buffer")
                .isEmpty();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            service.unregisterSubscriber(EXECUTION_ID, probeId);
            service.unregisterSubscriber(EXECUTION_ID, subscriberId);
        }
    }

    private static final FollowLogEvent traceEvent = event(Level.TRACE, "load-data", "task-run-1", 0, "trace line");
    private static final FollowLogEvent debugEvent = event(Level.DEBUG, "load-data", "task-run-1", 0, "debug line");
    private static final FollowLogEvent infoEvent = event(Level.INFO, "load-data", "task-run-1", 0, "info line");
    private static final FollowLogEvent warnEvent = event(Level.WARN, "transform", "task-run-2", 0, "warn line");
    private static final FollowLogEvent errorEvent = event(Level.ERROR, "transform", "task-run-2", 1, "error line");
    private static final List<FollowLogEvent> allEvents = List.of(traceEvent, debugEvent, infoEvent, warnEvent, errorEvent);

    private static final List<FiltersTestCase> filtersTestCases = List.of(
        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(allEvents)
            .filters(List.of())
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of(infoEvent, warnEvent, errorEvent))
            .filters(
                List.of(
                    QueryFilter.builder()
                        .field(QueryFilter.Field.LEVEL)
                        .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                        .value(Level.INFO)
                        .build()
                )
            )
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of(traceEvent, debugEvent, infoEvent))
            .filters(
                List.of(
                    QueryFilter.builder()
                        .field(QueryFilter.Field.LEVEL)
                        .operation(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO)
                        .value(Level.INFO)
                        .build()
                )
            )
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of(warnEvent, errorEvent))
            .filters(
                List.of(
                    QueryFilter.builder()
                        .field(QueryFilter.Field.TASK_ID)
                        .operation(QueryFilter.Op.EQUALS)
                        .value("transform")
                        .build()
                )
            )
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of(traceEvent, debugEvent, infoEvent))
            .filters(
                List.of(
                    QueryFilter.builder()
                        .field(QueryFilter.Field.TASK_RUN_ID)
                        .operation(QueryFilter.Op.EQUALS)
                        .value("task-run-1")
                        .build()
                )
            )
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of(errorEvent))
            .filters(
                List.of(
                    QueryFilter.builder()
                        .field(QueryFilter.Field.ATTEMPT_NUMBER)
                        .operation(QueryFilter.Op.EQUALS)
                        .value(1)
                        .build()
                )
            )
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of(infoEvent))
            .filters(
                List.of(
                    QueryFilter.builder()
                        .field(QueryFilter.Field.LEVEL)
                        .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                        .value(Level.INFO)
                        .build(),
                    QueryFilter.builder()
                        .field(QueryFilter.Field.TASK_ID)
                        .operation(QueryFilter.Op.EQUALS)
                        .value("load-data")
                        .build()
                )
            )
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of())
            .filters(
                List.of(
                    QueryFilter.builder()
                        .field(QueryFilter.Field.EXECUTION_ID)
                        .operation(QueryFilter.Op.EQUALS)
                        .value("some-other-execution")
                        .build()
                )
            )
            .build()
    );

    private static FollowLogEvent event(Level level, String taskId, String taskRunId, Integer attempt, String message) {
        return new FollowLogEvent(
            null,
            "io.kestra.demo",
            "demo-flow",
            taskId,
            EXECUTION_ID,
            taskRunId,
            attempt,
            null,
            Instant.now(),
            level,
            "main",
            message,
            null,
            null
        );
    }

    @Builder
    private record FiltersTestCase(
        List<FollowLogEvent> events,
        List<FollowLogEvent> expectedEvents,
        List<QueryFilter> filters) {
    }
}

package io.kestra.webserver.controllers.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.runners.FollowLogEvent;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.services.LogStreamingService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.tenants.TenantValidationFilter;

import io.micronaut.http.sse.Event;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The follow endpoint replays already-persisted logs and streams live ones. If it reads the repository
 * before subscribing, anything published in between belongs to neither and is never sent
 * (<a href="https://github.com/kestra-io/kestra/issues/10521">#10521</a>).
 * <p>
 * The repository here publishes a log while its own read is still streaming, which is exactly that window.
 */
@KestraTest
class LogFollowBackfillRaceTest {

    private static final String EXECUTION_ID = "exec-backfill-race";
    private static final String HISTORICAL = "historical line";
    private static final String DURING_READ = "published while the read was streaming";

    @Inject
    private LogController logController;

    @Inject
    private BroadcastQueueInterface<FollowLogEvent> logQueue;

    @Inject
    private LogStreamingService logStreamingService;

    @Inject
    private TenantService tenantService;

    @MockBean(TenantService.class)
    TenantService tenantService() {
        return mock(TenantService.class);
    }

    @MockBean(TenantValidationFilter.class)
    TenantValidationFilter tenantValidationFilter() {
        return mock(TenantValidationFilter.class);
    }

    /**
     * A repository whose read emits a log onto the queue midway through streaming its own results,
     * and gives the dispatch time to land before the read completes.
     */
    @MockBean(LogDataStoreInterface.class)
    LogDataStoreInterface logRepository() {
        LogDataStoreInterface repository = mock(LogDataStoreInterface.class);
        when(repository.findAsync(any(), any())).thenAnswer(invocation ->
            Flux.just(logEntry(HISTORICAL))
                .doOnNext(ignored -> {
                    try {
                        logQueue.emit(followEvent(DURING_READ));
                        // let the broadcast dispatch land while the read is still in flight
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
        );
        return repository;
    }

    @Test
    void shouldNotLoseALogPublishedWhileTheHistoryIsBeingRead() {
        when(tenantService.resolveTenant()).thenReturn("main");

        // A second follower on another execution keeps the queue consumer polling. Without it the
        // consumer is paused for the whole read and the backlog is simply replayed on resume, so the
        // gap never opens and the test cannot tell the orderings apart.
        String keepAliveId = IdUtils.create();
        Flux.<Event<FollowLogEvent>> create(
            sink -> logStreamingService.registerSubscriber("keep-consumer-running", keepAliveId, sink, List.of())
        ).subscribe(event -> { });

        try {

        List<Event<FollowLogEvent>> received = logController
            .followLogsFromExecution(EXECUTION_ID, List.of())
            .take(Duration.ofSeconds(10))
            .collectList()
            .block(Duration.ofSeconds(15));

        assertThat(received).isNotNull();
        List<String> messages = received.stream()
            .filter(event -> "progress".equals(event.getId()))
            .map(event -> event.getData().message())
            .toList();

        assertThat(messages)
            .as("a log published between subscribing and the end of the history read must still reach the client (#10521)")
            .contains(HISTORICAL, DURING_READ);
        } finally {
            logStreamingService.unregisterSubscriber("keep-consumer-running", keepAliveId);
        }
    }

    private static LogEntry logEntry(String message) {
        return LogEntry.builder()
            .tenantId("main")
            .namespace("io.kestra.unittest")
            .flowId("follow-race")
            .executionId(EXECUTION_ID)
            .taskId("task")
            .taskRunId(IdUtils.create())
            .attemptNumber(0)
            .timestamp(Instant.now())
            .level(Level.INFO)
            .thread("test")
            .message(message)
            .build();
    }

    private static FollowLogEvent followEvent(String message) {
        return FollowLogEvent.from(logEntry(message));
    }
}

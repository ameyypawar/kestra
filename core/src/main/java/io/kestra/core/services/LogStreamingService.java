package io.kestra.core.services;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.runners.FollowLogEvent;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.MapUtils;

import io.micronaut.http.sse.Event;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;

/**
 * This service offers a fanout mechanism so a single consumer of the log queue can dispatch log messages to multiple consumers.
 * It is designed to be used for 'follow' endpoints that using SSE to follow a flow logs.
 * <p>
 * Consumers need first to register themselves via {@link #registerSubscriber(String, String, FluxSink, List)},
 * then unregister (ideally in a finally block to avoid any memory leak) via {@link #unregisterSubscriber(String, String)}.
 * <p>
 * Each subscriber is registered with a list of {@link QueryFilter}s. Incoming events are matched
 * against those filters via the injected {@link Searchable}; only events that match every filter
 * are forwarded to that subscriber.
 */
@Slf4j
@Singleton
public class LogStreamingService {
    private final Map<String, Map<String, Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final Object subscriberLock = new Object();

    @Inject
    protected BroadcastQueueInterface<FollowLogEvent> logQueue;

    @Inject
    protected FollowLogEventMatcher followLogEventMatcher;

    private QueueSubscriber<FollowLogEvent> queueSubscriber;

    @PostConstruct
    void startQueueConsumer() {
        this.queueSubscriber = logQueue.subscriber();
        this.queueSubscriber.pause();
        this.queueSubscriber.subscribe(this::dispatch);
    }

    /**
     * Dispatch a log event to all the subscribers of its execution.
     * This method never throws: the queue subscriber is shared by all subscribers and treats any escaping
     * exception as fatal, so a delivery failure to a single SSE stream would shut down the whole server.
     */
    private void dispatch(Either<FollowLogEvent, DeserializationException> either) {
        try {
            if (either.isRight()) {
                log.error("Unable to deserialize log: {}", either.getRight().getMessage());
                return;
            }

            if (subscribers.isEmpty()) {
                return;
            }

            FollowLogEvent current = either.getLeft();
            if (current.executionId() == null) {
                // some logs are not about any execution, we skip them
                return;
            }

            // Get all subscribers for this execution
            Map<String, Subscriber> executionSubscribers = subscribers.get(current.executionId());

            if (executionSubscribers != null && !executionSubscribers.isEmpty()) {
                executionSubscribers.forEach((subscriberId, subscriber) -> {
                    // A subscriber still replaying history buffers instead of emitting, so live events
                    // neither overtake the backfill nor get lost while it runs.
                    Queue<FollowLogEvent> buffer = subscriber.buffer;
                    if (buffer != null) {
                        buffer.add(current);
                        return;
                    }
                    deliver(current, subscriberId, subscriber.sink, subscriber.filters);
                });
            }
        } catch (Exception e) {
            log.error("Unable to dispatch the log event to its subscribers", e);
        }
    }

    /**
     * Deliver a log event to a single subscriber.
     * This method never throws so a stale or broken SSE stream cannot prevent delivery to the other subscribers.
     */
    private void deliver(FollowLogEvent current, String subscriberId, FluxSink<Event<FollowLogEvent>> sink, List<QueryFilter> filters) {
        if (sink.isCancelled()) {
            // the SSE stream is already closed: drop the stale subscriber instead of writing to it
            unregisterSubscriber(current.executionId(), subscriberId);
            return;
        }

        try {
            if (followLogEventMatcher.matches(current, filters)) {
                sink.next(Event.of(current).id("progress"));
            }
        } catch (Exception e) {
            log.error("Error sending log update to the subscriber '{}'", subscriberId, e);
            failSilently(sink, e);
            unregisterSubscriber(current.executionId(), subscriberId);
        }
    }

    /**
     * Fail the sink, ignoring any error raised by an already terminated one.
     */
    private void failSilently(FluxSink<Event<FollowLogEvent>> sink, Exception cause) {
        try {
            sink.error(cause);
        } catch (Exception e) {
            log.debug("Unable to fail an already terminated sink", e);
        }
    }

    /**
     * Register a subscriber to an execution logs. The provided {@code filters} are applied to
     * every event before it is forwarded; an empty or {@code null} list forwards everything.
     * All subscribers must ensure to call {@link #unregisterSubscriber(String, String)} to avoid any memory leak.
     */
    public void registerSubscriber(String executionId, String subscriberId, FluxSink<Event<FollowLogEvent>> sink, List<QueryFilter> filters) {
        register(executionId, subscriberId, sink, filters, false);
    }

    /**
     * Register a subscriber that holds every incoming event in a buffer instead of emitting it, so a caller
     * can safely read the already-persisted logs afterwards. Nothing published while that read runs is lost.
     * The caller must then call {@link #streamBufferedSubscriber(String, String, Set)} to release the buffer
     * and switch to live delivery, otherwise the subscriber never emits anything.
     */
    public void registerBufferedSubscriber(String executionId, String subscriberId, FluxSink<Event<FollowLogEvent>> sink, List<QueryFilter> filters) {
        register(executionId, subscriberId, sink, filters, true);
    }

    private void register(String executionId, String subscriberId, FluxSink<Event<FollowLogEvent>> sink, List<QueryFilter> filters, boolean buffered) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            // Register the subscriber BEFORE resuming the queue to avoid a race where the polling
            // thread delivers an event between resume() and put(), causing events to be dropped.
            subscribers.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>())
                .put(subscriberId, new Subscriber(sink, filters, buffered ? new ConcurrentLinkedQueue<>() : null));

            if (this.queueSubscriber.isPaused()) {
                this.queueSubscriber.resume();
            }
        }
    }

    /**
     * Release a buffered subscriber: emit everything held while it was buffering, then switch it to live
     * delivery. Events in {@code alreadyDelivered} are skipped, as the caller has emitted them itself from
     * its own read — the buffering window overlaps that read, so the same event can appear in both.
     */
    public void streamBufferedSubscriber(String executionId, String subscriberId, Set<FollowLogEvent> alreadyDelivered) {
        Subscriber subscriber;
        synchronized (subscriberLock) {
            subscriber = subscribers.getOrDefault(executionId, Map.of()).get(subscriberId);
            if (subscriber == null || subscriber.buffer == null) {
                return;
            }
        }

        // Drain before clearing the buffer reference so an event arriving mid-drain is still captured;
        // the last poll after the switch covers the small window between the two.
        Queue<FollowLogEvent> buffer = subscriber.buffer;
        drain(buffer, subscriber, subscriberId, executionId, alreadyDelivered);
        subscriber.buffer = null;
        drain(buffer, subscriber, subscriberId, executionId, alreadyDelivered);
    }

    private void drain(Queue<FollowLogEvent> buffer, Subscriber subscriber, String subscriberId, String executionId, Set<FollowLogEvent> alreadyDelivered) {
        FollowLogEvent buffered;
        while ((buffered = buffer.poll()) != null) {
            if (alreadyDelivered != null && alreadyDelivered.contains(buffered)) {
                continue;
            }
            deliver(buffered, subscriberId, subscriber.sink, subscriber.filters);
        }
    }

    /** A registered SSE subscriber; {@code buffer} is non-null only while it is replaying history. */
    private static final class Subscriber {
        private final FluxSink<Event<FollowLogEvent>> sink;
        private final List<QueryFilter> filters;
        private volatile Queue<FollowLogEvent> buffer;

        private Subscriber(FluxSink<Event<FollowLogEvent>> sink, List<QueryFilter> filters, Queue<FollowLogEvent> buffer) {
            this.sink = sink;
            this.filters = filters;
            this.buffer = buffer;
        }
    }

    /**
     * Unregister a subscribers.
     * This is advised to do it in a finally block to be sure to free resources.
     */
    public void unregisterSubscriber(String executionId, String subscriberId) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            Map<String, Subscriber> executionSubscribers = subscribers.get(executionId);
            if (executionSubscribers != null) {
                executionSubscribers.remove(subscriberId);
                if (executionSubscribers.isEmpty()) {
                    subscribers.remove(executionId);
                }
            }

            // pause the subscription if no one is listening anymore
            if (MapUtils.isEmpty(subscribers) && !this.queueSubscriber.isPaused()) {
                this.queueSubscriber.pause();
            }
        }
    }

    @PreDestroy
    void shutdown() {
        if (queueSubscriber != null) {
            queueSubscriber.close();
        }
    }
}

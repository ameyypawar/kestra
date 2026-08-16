package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.runners.KVMetadataStateStore;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.*;
import io.kestra.core.utils.TestsUtils;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sorting the KV table used to fail for any field whose column is named differently from the
 * field the API exposes: the property is camel-cased straight into a column name, so the query
 * referenced a column that does not exist and the whole request failed rather than just the
 * ordering, leaving the table empty. See #17314.
 */
@KestraTest(resolveParameters = false)
class KVControllerSortTest {

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private KvMetadataRepositoryInterface kvMetadataRepository;

    @Inject
    private KVMetadataStateStore kvMetadataStateStore;

    @BeforeEach
    public void init() {
        List<PersistedKvMetadata> persisted = kvMetadataRepository.find(Pageable.UNPAGED, MAIN_TENANT, Collections.emptyList(), true, true);
        kvMetadataRepository.purge(persisted);
    }

    private KVStore seedTwoEntries() throws IOException {
        String namespace = TestsUtils.randomNamespace();
        KVStore kvStore = new InternalKVStore(MAIN_TENANT, namespace, storageInterface, kvMetadataStateStore);

        Instant expiration = Instant.now().plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
        kvStore.put("alpha-key", new KVValueAndMetadata(new KVMetadata("alpha description", expiration), "alpha-value"));
        kvStore.put("beta-key", new KVValueAndMetadata(new KVMetadata("beta description", expiration), "beta-value"));

        return kvStore;
    }

    private PagedResults<KVEntry> list(String sort) {
        return client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/kv?size=10&page=1&sort=" + sort),
            Argument.of(PagedResults.class, KVEntry.class)
        );
    }

    /**
     * Every column the KV table offers as sortable. A field with no column behind it fails the
     * request outright, so reaching the assertion at all is most of the point.
     */
    @ParameterizedTest
    @ValueSource(strings = {"key", "namespace", "description", "updateDate", "creationDate", "revision", "expirationDate"})
    void shouldSortByEveryOfferedField(String field) throws IOException {
        seedTwoEntries();

        for (String direction : List.of("asc", "desc")) {
            PagedResults<KVEntry> results = list(field + ":" + direction);

            assertThat(results.getTotal()).isEqualTo(2);
            assertThat(results.getResults()).hasSize(2);
        }
    }

    @Test
    void shouldOrderByLastModified() throws IOException {
        String namespace = TestsUtils.randomNamespace();
        KVStore kvStore = new InternalKVStore(MAIN_TENANT, namespace, storageInterface, kvMetadataStateStore);

        // Written oldest-first, and deliberately not in key order, so ordering by the update
        // date cannot be satisfied by falling back to the key.
        kvStore.put("z-oldest", new KVValueAndMetadata(new KVMetadata(null, (Duration) null), "one"));
        kvStore.put("a-newest", new KVValueAndMetadata(new KVMetadata(null, (Duration) null), "two"));

        PagedResults<KVEntry> descending = list("updateDate:desc");
        assertThat(descending.getResults()).hasSize(2);
        assertThat(descending.getResults().getFirst().key()).isEqualTo("a-newest");

        PagedResults<KVEntry> ascending = list("updateDate:asc");
        assertThat(ascending.getResults()).hasSize(2);
        assertThat(ascending.getResults().getFirst().key()).isEqualTo("z-oldest");
    }
}

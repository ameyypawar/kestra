package io.kestra.webserver.controllers.api;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.serializers.JacksonMapper;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class WebhookSecretInputTest {
    private static final String TESTS_FLOW_NS = "io.kestra.tests";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private TestRunnerUtils runnerUtils;

    /**
     * A webhook trigger mapping a request field onto a {@code SECRET} input used to fail the call
     * outright: the execution was built with the trigger's raw, unrendered inputs, so building the
     * run context tried to decrypt a value that was still the plain {@code "{{ trigger.body.* }}"}
     * template and threw {@code String cannot be cast to Map}.
     */
    @Test
    @LoadFlows("flows/valids/webhook-secret-input.yaml")
    void shouldEncryptASecretInputPassedByAWebhook() {
        var response = client.toBlocking().exchange(
            POST(
                "/api/v1/%s/executions/webhook/io.kestra.tests/webhook-secret-input/secret-input-key".formatted(MAIN_TENANT),
                Map.of("plaintext", "visible", "ciphertext", "s3cr3t")
            ),
            String.class
        );

        assertThat((Object) response.getStatus()).isEqualTo(HttpStatus.OK);

        var execution = runnerUtils.awaitFlowExecution(
            e -> e.getTrigger() != null && e.getTrigger().getId().equals("webhook"),
            MAIN_TENANT, TESTS_FLOW_NS, "webhook-secret-input"
        );

        // The non-secret input is rendered from the request body, as it always was
        assertThat(execution.getInputs()).containsEntry("plaintext", "visible");

        // ...and the SECRET one reaches the execution encrypted, the same shape a UI-triggered run produces
        Map<String, Object> ciphertext = JacksonMapper.toMap(execution.getInputs().get("ciphertext"));
        assertThat(ciphertext).containsEntry("type", EncryptedString.TYPE);
        assertThat(ciphertext.get("value")).isNotNull().isNotEqualTo("s3cr3t");
    }
}

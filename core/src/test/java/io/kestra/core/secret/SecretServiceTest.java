package io.kestra.core.secret;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The environment is passed in rather than read from the process, so both encodings can be exercised
 * without touching {@link System#getenv()}.
 */
class SecretServiceTest {
    /** Valid Base64 for "hello", and also a plausible credential in its own right. */
    private static final String LOOKS_LIKE_BASE64 = "aGVsbG8=";

    @SuppressWarnings("rawtypes")
    private static SecretService serviceWith(Map<String, Object> properties, Map<String, String> environment) {
        ApplicationContext context = ApplicationContext.run(properties);
        SecretService service = context.getBean(SecretService.class);
        service.decode(environment);

        return service;
    }

    @SuppressWarnings("rawtypes")
    private static SecretService base64(Map<String, String> environment) {
        return serviceWith(Map.of(), environment);
    }

    @SuppressWarnings("rawtypes")
    private static SecretService raw(Map<String, String> environment) {
        return serviceWith(Map.of("kestra.secret.encoding", "raw"), environment);
    }

    private static String find(Object service, String key) throws Exception {
        return ((SecretService<?>) service).findSecret(null, null, key);
    }

    private static java.util.Set<String> keys(Object service) throws Exception {
        return ((SecretService<?>) service).inheritedSecrets(null, "ns").get("ns");
    }

    @Test
    void shouldDecodeBase64ByDefault() throws Exception {
        var service = base64(Map.of("SECRET_MY_KEY", LOOKS_LIKE_BASE64));

        assertThat(find(service, "my_key")).isEqualTo("hello");
    }

    @Test
    void shouldReadTheValueUntouchedWhenEncodingIsRaw() throws Exception {
        var service = raw(Map.of("SECRET_MY_KEY", "hello"));

        assertThat(find(service, "my_key")).isEqualTo("hello");
    }

    /**
     * The case the report calls out as the worst one: a credential that happens to be valid Base64
     * is decoded into unrelated bytes and fails later, against a third party, rather than at startup.
     */
    @Test
    void shouldNotDecodeACredentialThatMerelyLooksLikeBase64WhenRaw() throws Exception {
        var asRaw = raw(Map.of("SECRET_TOKEN", LOOKS_LIKE_BASE64));
        var asBase64 = base64(Map.of("SECRET_TOKEN", LOOKS_LIKE_BASE64));

        assertThat(find(asRaw, "token"))
            .as("raw hands back the credential as issued")
            .isEqualTo(LOOKS_LIKE_BASE64);

        assertThat(find(asBase64, "token"))
            .as("the control: the same value is silently rewritten under the default")
            .isEqualTo("hello");
    }

    @Test
    void shouldKeepLineBreaksOfAMultiLineValueWhenRaw() throws Exception {
        String certificate = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----";
        var service = raw(Map.of("SECRET_CERT", certificate));

        assertThat(find(service, "cert")).isEqualTo(certificate);
    }

    @Test
    void shouldStripLineBreaksBeforeDecodingBase64() throws Exception {
        var service = base64(Map.of("SECRET_WRAPPED", "aGVs\nbG8="));

        assertThat(find(service, "wrapped")).isEqualTo("hello");
    }

    @Test
    void shouldOmitAValueThatIsNotBase64WhenDecodingIsExpected() {
        var service = base64(Map.of("SECRET_PLAINTEXT", "not base64 at all!!"));

        assertThatThrownBy(() -> find(service, "plaintext"))
            .isInstanceOf(SecretNotFoundException.class);
    }

    /**
     * The same value is readable under raw, which is the whole point of the option.
     */
    @Test
    void shouldReadAPlaintextValueThatBase64WouldHaveRejected() throws Exception {
        String plaintext = "not base64 at all!!";
        var service = raw(Map.of("SECRET_PLAINTEXT", plaintext));

        assertThat(find(service, "plaintext")).isEqualTo(plaintext);
    }

    @Test
    void shouldStripThePrefixAndUpperCaseTheKeyInBothEncodings() throws Exception {
        assertThat(keys(raw(Map.of("SECRET_my_key", "v")))).containsExactly("MY_KEY");
        assertThat(keys(base64(Map.of("SECRET_my_key", "dg==")))).containsExactly("MY_KEY");
    }

    @Test
    void shouldIgnoreEnvironmentEntriesWithoutThePrefix() throws Exception {
        var service = raw(Map.of("NOT_A_SECRET", "v", "SECRET_REAL", "v"));

        assertThat(keys(service)).containsExactly("REAL");
    }
}

package io.kestra.core.secret;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("kestra.secret")
public class SecretConfig {
    /**
     * How the value of a {@code SECRET_*} environment variable is encoded.
     */
    Encoding encoding = Encoding.BASE64;

    public enum Encoding {
        /**
         * The value is Base64-encoded and is decoded before use. The default, and what multi-line
         * values such as certificates and private keys rely on.
         */
        BASE64,

        /**
         * The value is used exactly as it appears in the environment.
         *
         * <p>This is what a Kubernetes Secret already delivers: the object stores its values
         * Base64-encoded, but the kubelet injects them decoded, so every other consumer of that
         * Secret receives the plaintext credential.
         */
        RAW
    }
}

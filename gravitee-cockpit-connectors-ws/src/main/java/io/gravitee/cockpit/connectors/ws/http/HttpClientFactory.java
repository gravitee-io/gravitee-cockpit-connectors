/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.cockpit.connectors.ws.http;

import io.gravitee.cockpit.connectors.ws.endpoints.WebSocketEndpoint;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class HttpClientFactory {

    private static final String HTTPS_SCHEME = "https";
    private static final String KEYSTORE_FORMAT_JKS = "JKS";
    private static final String KEYSTORE_FORMAT_PEM = "PEM";
    private static final String KEYSTORE_FORMAT_PKCS12 = "PKCS12";

    private final AtomicInteger counter = new AtomicInteger(0);
    private final Vertx vertx;

    @Getter
    private final HttpClientConfiguration configuration;

    public HttpClientFactory(Vertx vertx, HttpClientConfiguration configuration) {
        this.vertx = vertx;
        this.configuration = configuration;
    }

    public WebSocketEndpoint nextEndpoint() {
        List<WebSocketEndpoint> endpoints = configuration.getEndpoints();

        if (endpoints.isEmpty()) {
            return null;
        }

        WebSocketEndpoint endpoint = endpoints.get(Math.abs(counter.getAndIncrement() % endpoints.size()));

        int tryConnect = endpoint.incrementRetryCount();
        if (tryConnect > 5 && endpoint.isRemovable()) {
            log.info("Cockpit connector tries to connect to instance at {} 5 times. Removing instance...", endpoint.getUrl());
            configuration.getEndpoints().remove(endpoint);
            return nextEndpoint();
        }

        return endpoint;
    }

    public HttpClient getHttpClient(WebSocketEndpoint webSocketEndpoint) {
        URI target = URI.create(webSocketEndpoint.getUrl());
        HttpClientOptions options = new HttpClientOptions();

        if (!HTTPS_SCHEME.equalsIgnoreCase(target.getScheme())) {
            throw new IllegalArgumentException("Https is mandatory");
        }

        // Configure SSL
        options.setSsl(true);
        options.setTrustAll(configuration.isTrustAll());
        options.setVerifyHost(configuration.isHostnameVerifier());

        if (configuration.getKeystoreType() != null) {
            if (configuration.getKeystoreType().equalsIgnoreCase(KEYSTORE_FORMAT_JKS)) {
                options.setKeyStoreOptions(
                    new JksOptions().setPath(configuration.getKeystorePath()).setPassword(configuration.getKeystorePassword())
                );
            } else if (configuration.getKeystoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PKCS12)) {
                options.setPfxKeyCertOptions(
                    new PfxOptions().setPath(configuration.getKeystorePath()).setPassword(configuration.getKeystorePassword())
                );
            }
        }

        if (configuration.getTruststoreType() != null) {
            if (configuration.getTruststoreType().equalsIgnoreCase(KEYSTORE_FORMAT_JKS)) {
                options.setTrustStoreOptions(
                    new JksOptions().setPath(configuration.getTruststorePath()).setPassword(configuration.getTruststorePassword())
                );
            } else if (configuration.getTruststoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PKCS12)) {
                options.setPfxTrustOptions(
                    new PfxOptions().setPath(configuration.getTruststorePath()).setPassword(configuration.getTruststorePassword())
                );
            } else if (configuration.getTruststoreType().equalsIgnoreCase(KEYSTORE_FORMAT_PEM)) {
                options.setPemTrustOptions(new PemTrustOptions().addCertPath(configuration.getTruststorePath()));
            }
        }

        return vertx.createHttpClient(options);
    }
}

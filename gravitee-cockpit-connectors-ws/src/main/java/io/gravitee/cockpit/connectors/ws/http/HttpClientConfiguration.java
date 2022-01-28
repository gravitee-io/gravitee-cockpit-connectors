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
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
public class HttpClientConfiguration {

    private final Environment environment;

    private List<WebSocketEndpoint> endpoints;

    /**
     * Cockpit ssl keystore type. (jks, pkcs12)
     */
    @Value("${cockpit.keystore.type:#{null}}")
    private String keystoreType;

    /**
     * Cockpit ssl keystore path.
     */
    @Value("${cockpit.keystore.path:#{null}}")
    private String keystorePath;

    /**
     * Cockpit ssl keystore password.
     */
    @Value("${cockpit.keystore.password:#{null}}")
    private String keystorePassword;

    /**
     * Cockpit ssl truststore trustall.
     */
    @Value("${cockpit.ssl.trustall:false}")
    private boolean trustAll;

    /**
     * Cockpit ssl truststore hostname verifier.
     */
    @Value("${cockpit.ssl.verifyHostname:true}")
    private boolean hostnameVerifier;

    /**
     * Cockpit ssl truststore type.
     */
    @Value("${cockpit.truststore.type:#{null}}")
    private String truststoreType;

    /**
     * Cockpit ssl truststore path.
     */
    @Value("${cockpit.truststore.path:#{null}}")
    private String truststorePath;

    /**
     * Cockpit ssl truststore password.
     */
    @Value("${cockpit.truststore.password:#{null}}")
    private String truststorePassword;

    public List<WebSocketEndpoint> getEndpoints() {
        if (endpoints == null) {
            endpoints = initializeEndpoints();
        }

        return endpoints;
    }

    public void setEndpoints(List<WebSocketEndpoint> endpoints) {
        this.endpoints = endpoints;
    }

    private List<WebSocketEndpoint> initializeEndpoints() {
        String key = String.format("cockpit.ws.endpoints[%s]", 0);
        List<WebSocketEndpoint> endpoints = new ArrayList<>();

        while (environment.containsProperty(key)) {
            String url = environment.getProperty(key);
            endpoints.add(new WebSocketEndpoint(url));

            key = String.format("cockpit.ws.endpoints[%s]", endpoints.size());
        }

        return endpoints;
    }
}

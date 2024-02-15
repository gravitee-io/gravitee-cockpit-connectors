/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.cockpit.connectors.ws.spring;

import static io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration.ENDPOINTS_KEY;
import static io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration.KEYSTORE_PASSWORD_KEY;
import static io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration.KEYSTORE_PATH_KEY;
import static io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration.KEYSTORE_TYPE_KEY;
import static io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration.MAX_WEB_SOCKET_FRAME_SIZE_KEY;
import static io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration.MAX_WEB_SOCKET_MESSAGE_SIZE_KEY;
import static io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration.TRUSTSTORE_PASSWORD_KEY;
import static io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration.TRUSTSTORE_PATH_KEY;
import static io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration.TRUSTSTORE_TYPE_KEY;
import static io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration.TRUST_ALL_KEY;
import static io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration.VERIFY_HOST_KEY;

import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CockpitClientConfiguration {

    static final String TRUST_ALL_FALLBACK_KEY = "cockpit.ssl.trustall";
    static final String VERIFY_HOST_FALLBACK_KEY = "cockpit.ssl.verifyHostname";
    static final String KEYSTORE_TYPE_FALLBACK_KEY = "cockpit.keystore.type";
    static final String KEYSTORE_PATH_FALLBACK_KEY = "cockpit.keystore.path";
    static final String KEYSTORE_PASSWORD_FALLBACK_KEY = "cockpit.keystore.password";
    static final String TRUSTSTORE_TYPE_FALLBACK_KEY = "cockpit.truststore.type";
    static final String TRUSTSTORE_PATH_FALLBACK_KEY = "cockpit.truststore.path";
    static final String TRUSTSTORE_PASSWORD_FALLBACK_KEY = "cockpit.truststore.password";
    static final String MAX_WEB_SOCKET_FRAME_SIZE_FALLBACK_KEY = "cockpit.ws.maxWebSocketFrameSize";
    static final String MAX_WEB_SOCKET_MESSAGE_SIZE_FALLBACK_KEY = "cockpit.ws.maxWebSocketMessageSize";
    static final String ENDPOINTS_FALLBACK_KEY = "cockpit.ws.endpoints";

    static final Map<String, String> FALLBACK_KEYS = new HashMap<>();

    static {
        FALLBACK_KEYS.put(TRUST_ALL_KEY, TRUST_ALL_FALLBACK_KEY);
        FALLBACK_KEYS.put(VERIFY_HOST_KEY, VERIFY_HOST_FALLBACK_KEY);
        FALLBACK_KEYS.put(KEYSTORE_TYPE_KEY, KEYSTORE_TYPE_FALLBACK_KEY);
        FALLBACK_KEYS.put(KEYSTORE_PATH_KEY, KEYSTORE_PATH_FALLBACK_KEY);
        FALLBACK_KEYS.put(KEYSTORE_PASSWORD_KEY, KEYSTORE_PASSWORD_FALLBACK_KEY);
        FALLBACK_KEYS.put(TRUSTSTORE_TYPE_KEY, TRUSTSTORE_TYPE_FALLBACK_KEY);
        FALLBACK_KEYS.put(TRUSTSTORE_PATH_KEY, TRUSTSTORE_PATH_FALLBACK_KEY);
        FALLBACK_KEYS.put(TRUSTSTORE_PASSWORD_KEY, TRUSTSTORE_PASSWORD_FALLBACK_KEY);
        FALLBACK_KEYS.put(MAX_WEB_SOCKET_FRAME_SIZE_KEY, MAX_WEB_SOCKET_FRAME_SIZE_FALLBACK_KEY);
        FALLBACK_KEYS.put(MAX_WEB_SOCKET_MESSAGE_SIZE_KEY, MAX_WEB_SOCKET_MESSAGE_SIZE_FALLBACK_KEY);
        FALLBACK_KEYS.put(ENDPOINTS_KEY, ENDPOINTS_FALLBACK_KEY);
    }
}

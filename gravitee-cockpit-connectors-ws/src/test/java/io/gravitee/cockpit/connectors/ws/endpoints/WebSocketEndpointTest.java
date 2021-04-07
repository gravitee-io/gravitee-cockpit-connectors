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
package io.gravitee.cockpit.connectors.ws.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author Lorie Pisicchio (lorie.pisicchio at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebSocketEndpointTest {

    private static final String PATH = "/ws/connectors";

    @ParameterizedTest
    @ValueSource(strings = { "http://localhost:8062", "http://localhost:8062/" })
    public void resolvePath_should_return_a_path_resolved(String baseUrl) {
        WebSocketEndpoint endpoint = new WebSocketEndpoint(baseUrl);
        assertThat(endpoint.resolvePath(PATH)).isEqualTo(PATH);
    }
}

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
package io.gravitee.cockpit.connectors.ws.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.cockpit.connectors.core.spring.CommandHandlersConfiguration;
import io.gravitee.cockpit.connectors.core.spring.MonitoringCollectorConfiguration;
import io.gravitee.cockpit.connectors.ws.http.HttpClientConfiguration;
import io.gravitee.cockpit.connectors.ws.http.HttpClientFactory;
import io.gravitee.plugin.core.api.PluginManifest;
import io.gravitee.plugin.core.api.PluginRegistry;
import io.vertx.core.Vertx;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import({ CommandHandlersConfiguration.class, MonitoringCollectorConfiguration.class })
public class WebSocketConnectorConfiguration {

    public static final String COCKPIT_CONNECTORS_WS_PLUGIN_ID = "cockpit-connectors-ws";

    @Bean
    public HttpClientConfiguration httpClientConfiguration(Environment environment) {
        return new HttpClientConfiguration(environment);
    }

    @Bean
    public HttpClientFactory httpClientFactory(Vertx vertx, HttpClientConfiguration configuration) {
        return new HttpClientFactory(vertx, configuration);
    }

    @Bean("pluginManifest")
    PluginManifest pluginInfos(PluginRegistry pluginRegistry) {
        return pluginRegistry
            .plugins()
            .stream()
            .filter(plugin -> plugin.id().equals(COCKPIT_CONNECTORS_WS_PLUGIN_ID))
            .findFirst()
            .get()
            .manifest();
    }

    @Bean("cockpitObjectMapper")
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

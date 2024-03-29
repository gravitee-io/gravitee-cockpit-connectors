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

import static io.gravitee.cockpit.connectors.ws.spring.CockpitClientConfiguration.FALLBACK_KEYS;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.gravitee.cockpit.api.command.websocket.CockpitExchangeSerDe;
import io.gravitee.cockpit.connectors.core.spring.MonitoringCollectorConfiguration;
import io.gravitee.exchange.api.configuration.IdentifyConfiguration;
import io.gravitee.exchange.connector.websocket.client.WebSocketClientConfiguration;
import io.gravitee.exchange.connector.websocket.client.WebSocketConnectorClientFactory;
import io.gravitee.exchange.connector.websocket.spring.ConnectorWebSocketConfiguration;
import io.vertx.rxjava3.core.Vertx;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * @author Guillaume LAMIRAND (guillaume.lamirand at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import({ ConnectorWebSocketConfiguration.class, MonitoringCollectorConfiguration.class })
public class CockpitConnectorConfiguration {

    @Bean("cockpitIdentifyConfiguration")
    public IdentifyConfiguration cockpitIdentifyConfiguration(final Environment environment) {
        return new IdentifyConfiguration(environment, "cockpit", FALLBACK_KEYS);
    }

    @Bean("cockpitWebsocketConnectorClientFactory")
    public WebSocketConnectorClientFactory cockpitWebsocketConnectorClientFactory(
        Vertx vertx,
        @Qualifier("cockpitIdentifyConfiguration") IdentifyConfiguration cockpitIdentifyConfiguration
    ) {
        return new WebSocketConnectorClientFactory(vertx, new WebSocketClientConfiguration(cockpitIdentifyConfiguration));
    }

    @Bean("cockpitExchangeSerDe")
    public CockpitExchangeSerDe cockpitExchangeSerDe(@Qualifier("cockpitObjectMapper") ObjectMapper cockpitObjectMapper) {
        return new CockpitExchangeSerDe(cockpitObjectMapper);
    }

    @Bean("cockpitObjectMapper")
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}

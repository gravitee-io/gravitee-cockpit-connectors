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
package io.gravitee.cockpit.connectors.ws;

import io.gravitee.cockpit.api.CockpitConnector;
import io.gravitee.cockpit.connectors.core.services.MonitoringCollectorService;
import io.gravitee.cockpit.connectors.ws.command.CockpitConnectorCommandContext;
import io.gravitee.common.service.AbstractService;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.command.CommandAdapter;
import io.gravitee.exchange.api.command.CommandHandler;
import io.gravitee.exchange.api.command.Reply;
import io.gravitee.exchange.api.command.ReplyAdapter;
import io.gravitee.exchange.api.connector.ConnectorCommandHandlersFactory;
import io.gravitee.exchange.api.connector.ExchangeConnector;
import io.gravitee.exchange.api.connector.ExchangeConnectorManager;
import io.gravitee.exchange.api.websocket.command.ExchangeSerDe;
import io.gravitee.exchange.api.websocket.protocol.ProtocolVersion;
import io.gravitee.exchange.connector.websocket.WebSocketExchangeConnector;
import io.gravitee.exchange.connector.websocket.client.WebSocketConnectorClientFactory;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
// This class is instanciated as a Spring Component by Gravitee Node
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Slf4j
public class WebSocketCockpitConnector extends AbstractService<CockpitConnector> implements CockpitConnector {

    public static final ProtocolVersion PROTOCOL_VERSION = ProtocolVersion.V1;

    @Autowired
    private Vertx vertx;

    @Autowired
    private ExchangeConnectorManager exchangeConnectorManager;

    @Autowired
    @Lazy
    @Qualifier("cockpitConnectorCommandHandlersFactory")
    private ConnectorCommandHandlersFactory cockpitConnectorCommandHandlersFactory;

    @Autowired
    @Qualifier("cockpitWebsocketConnectorClientFactory")
    private WebSocketConnectorClientFactory cockpitWebsocketConnectorClientFactory;

    @Autowired
    @Qualifier("cockpitExchangeSerDe")
    private ExchangeSerDe cockpitExchangeSerDe;

    @Autowired
    private MonitoringCollectorService monitoringCollectorService;

    @Value("${cockpit.enabled:${cloud.enabled:false}}")
    private boolean enabled;

    private WebSocketExchangeConnector websocketExchangeConnector;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (enabled) {
            log.info("Cockpit connector is enabled. Starting connector...");
            CockpitConnectorCommandContext integrationConnectorCommandContext = new CockpitConnectorCommandContext();

            List<CommandHandler<? extends Command<?>, ? extends Reply<?>>> connectorCommandHandlers = cockpitConnectorCommandHandlersFactory.buildCommandHandlers(
                integrationConnectorCommandContext
            );
            List<CommandAdapter<? extends Command<?>, ? extends Command<?>, ? extends Reply<?>>> connectorCommandAdapters = cockpitConnectorCommandHandlersFactory.buildCommandAdapters(
                integrationConnectorCommandContext,
                PROTOCOL_VERSION
            );
            List<ReplyAdapter<? extends Reply<?>, ? extends Reply<?>>> connectorReplyAdapters = cockpitConnectorCommandHandlersFactory.buildReplyAdapters(
                integrationConnectorCommandContext,
                PROTOCOL_VERSION
            );
            websocketExchangeConnector =
                new WebSocketExchangeConnector(
                    PROTOCOL_VERSION,
                    connectorCommandHandlers,
                    connectorCommandAdapters,
                    connectorReplyAdapters,
                    vertx,
                    cockpitWebsocketConnectorClientFactory,
                    cockpitExchangeSerDe
                );

            exchangeConnectorManager
                .register(websocketExchangeConnector)
                .andThen(
                    Completable
                        .fromRunnable(() -> monitoringCollectorService.start(websocketExchangeConnector))
                        .doOnError(throwable -> log.warn("Unable to start monitoring collector for cockpit connector"))
                        .onErrorComplete()
                )
                .blockingAwait();
            log.info("Cockpit connector started successfully.");
            // Register shutdown hook
            Thread shutdownHook = new ContainerShutdownHook(websocketExchangeConnector);

            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } else {
            log.info("Cockpit connector is disabled.");
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        this.monitoringCollectorService.stop();
    }

    @Override
    public Single<Reply<?>> sendCommand(final Command<?> command) {
        return Single
            .fromCallable(() -> this.websocketExchangeConnector.isActive())
            .flatMap(isActive -> {
                if (isActive) {
                    return this.websocketExchangeConnector.sendCommand(command);
                } else {
                    return Single.error(new IllegalStateException("CockpitConnector is not ready yet."));
                }
            });
    }

    private class ContainerShutdownHook extends Thread {

        private final ExchangeConnector connector;

        private ContainerShutdownHook(ExchangeConnector connector) {
            super("graviteeio-cockpit-connector-finalizer");
            this.connector = connector;
        }

        @Override
        public void run() {
            try {
                connector.close().blockingAwait();
            } catch (Exception ex) {
                LoggerFactory.getLogger(this.getClass()).error("Unexpected error while stopping {}", name(), ex);
            }
        }
    }
}

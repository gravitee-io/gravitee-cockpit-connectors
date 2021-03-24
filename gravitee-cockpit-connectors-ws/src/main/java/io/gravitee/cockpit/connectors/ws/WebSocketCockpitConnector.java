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
package io.gravitee.cockpit.connectors.ws;

import static io.gravitee.cockpit.api.command.Command.PING_PONG_PREFIX;

import io.gravitee.cockpit.api.CockpitConnector;
import io.gravitee.cockpit.api.command.*;
import io.gravitee.cockpit.connectors.ws.channel.ClientChannel;
import io.gravitee.cockpit.connectors.ws.endpoints.WebSocketEndpoint;
import io.gravitee.cockpit.connectors.ws.http.HttpClientFactory;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.Node;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.subjects.CompletableSubject;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.WebSocket;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class WebSocketCockpitConnector extends AbstractService<CockpitConnector> implements CockpitConnector {

    private static final long PING_HANDLER_DELAY = 5000;

    @Value("${cockpit.enabled:false}")
    private boolean enabled;

    @Value("${cockpit.ws.discovery:true}")
    private boolean discovery;

    @Autowired
    private HttpClientFactory httpClientFactory;

    @Autowired
    private Vertx vertx;

    @Autowired
    private Node node;

    @Autowired
    @Qualifier("cockpitCommandHandlers")
    private Map<Command.Type, CommandHandler<Command<?>, Reply>> commandHandlers;

    @Autowired(required = false)
    @Qualifier("cockpitHelloCommandProducer")
    private CommandProducer helloCommandProducer;

    private boolean closedByCockpit = false;

    private long pongHandlerId;

    private CircuitBreaker circuitBreaker;

    private final String path;

    private HttpClient httpClient;

    private ClientChannel clientChannel;

    private final CompletableSubject webSocketConnectionReady = CompletableSubject.create();

    public WebSocketCockpitConnector() {
        this.path = "/ws/controller";
    }

    @Override
    protected void doStart() {
        if (enabled) {
            log.info("Cockpit connector is enabled. Starting connector.");
            circuitBreaker =
                CircuitBreaker.create(
                    "cockpit-connector",
                    vertx,
                    new CircuitBreakerOptions().setMaxRetries(Integer.MAX_VALUE).setNotificationAddress(null)
                );

            // Back-off retry
            circuitBreaker.retryPolicy(integer -> 5000L);

            connect();
        } else {
            log.info("Cockpit connector is disabled.");
        }
    }

    private void connect() {
        circuitBreaker
            .execute(this::doConnect)
            .setHandler(
                event -> {
                    // The connection has been established.
                    if (event.succeeded()) {
                        final WebSocket webSocket = event.result();
                        clientChannel = new ClientChannel(webSocket, node, helloCommandProducer, commandHandlers);
                        clientChannel.onClose(
                            () -> {
                                closedByCockpit = true;
                                webSocket.close();
                            }
                        );
                        clientChannel.init().subscribe(webSocketConnectionReady::onComplete);

                        // Initialize ping-pong
                        // See RFC 6455 Section <a href="https://tools.ietf.org/html/rfc6455#section-5.5.2"
                        pongHandlerId =
                            vertx.setPeriodic(
                                PING_HANDLER_DELAY,
                                pong -> {
                                    if (!webSocket.isClosed()) {
                                        webSocket.writePing(Buffer.buffer(PING_PONG_PREFIX + node.id() + " - " + node.hostname()));
                                    }
                                }
                            );

                        if (discovery) {
                            log.info("Discovery mode is enabled, listening for cockpit instances...");
                        }

                        webSocket.exceptionHandler(throwable -> log.error("An error occurs on the websocket connection", throwable));

                        webSocket.pongHandler(data -> log.debug("Got a pong from Cockpit Controller"));

                        webSocket.closeHandler(
                            closeEvent -> {
                                log.debug("Connection to Cockpit Controller has been closed.");

                                if (pongHandlerId != 0L) {
                                    vertx.cancelTimer(pongHandlerId);
                                }

                                // Cleanup channel.
                                clientChannel.cleanup();

                                if (!closedByCockpit) {
                                    // How to force to reconnect ?
                                    connect();
                                }
                            }
                        );
                    } else {
                        // Retry the connection
                        connect();
                    }
                }
            );
    }

    private void doConnect(Promise<WebSocket> promise) {
        try {
            WebSocketEndpoint webSocketEndpoint = httpClientFactory.nextEndpoint();

            if (webSocketEndpoint == null) {
                log.warn("No Cockpit endpoint is defined. Please check that 'cockpit.ws.endpoints' property has been properly defined.");
                promise.fail("No Cockpit endpoint is defined.");
                return;
            }

            log.debug("Trying to connect to Cockpit Controller WebSocket (endpoint [{}])." + webSocketEndpoint.getUrl());

            httpClient = httpClientFactory.getHttpClient(webSocketEndpoint);

            httpClient.webSocket(
                webSocketEndpoint.getPort(),
                webSocketEndpoint.getHost(),
                webSocketEndpoint.getPath() + path,
                result -> {
                    if (result.succeeded()) {
                        // Re-init endpoint counter.
                        webSocketEndpoint.reinitRetryCount();

                        log.info("Channel is ready to send data to Cockpit Controller through websocket from {}", webSocketEndpoint + path);
                        promise.complete(result.result());
                    } else {
                        Throwable throwable = result.cause();
                        log.error(
                            "An error occurs while trying to connect to the Cockpit Controller: {} [{} times]",
                            throwable.getMessage(),
                            webSocketEndpoint.getRetryCount(),
                            throwable
                        );
                        promise.fail(throwable);

                        // Force the HTTP client to close after a defect.
                        httpClient.close();
                    }
                }
            );
        } catch (Exception e) {
            log.error("An error occurred when trying to connect to Cockpit Controller.", e);
            promise.fail(e);
        }
    }

    @Override
    public Maybe<Reply> sendCommand(Command<? extends Payload> command) {
        return this.webSocketConnectionReady.toSingle(() -> clientChannel).toMaybe().flatMap(clientChannel -> clientChannel.send(command));
    }

    @Override
    protected void doStop() throws Exception {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IllegalStateException ise) {
                log.warn(ise.getMessage());
            }
        }

        if (pongHandlerId != 0L) {
            vertx.cancelTimer(pongHandlerId);
        }
    }

    public Completable whenReady() {
        return this.webSocketConnectionReady;
    }
}

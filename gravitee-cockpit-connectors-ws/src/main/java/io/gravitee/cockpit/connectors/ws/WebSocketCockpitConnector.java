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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.cockpit.api.CockpitConnector;
import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.CommandProducer;
import io.gravitee.cockpit.api.command.Payload;
import io.gravitee.cockpit.api.command.Reply;
import io.gravitee.cockpit.api.command.hello.HelloReply;
import io.gravitee.cockpit.api.command.installation.InstallationPayload;
import io.gravitee.cockpit.api.command.installation.InstallationReply;
import io.gravitee.cockpit.connectors.core.internal.CommandHandlerWrapper;
import io.gravitee.cockpit.connectors.ws.channel.ClientChannel;
import io.gravitee.cockpit.connectors.ws.endpoints.WebSocketEndpoint;
import io.gravitee.cockpit.connectors.ws.http.HttpClientFactory;
import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.Node;
import io.gravitee.plugin.core.api.PluginManifest;
import io.reactivex.Single;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.WebSocket;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.Getter;
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
    private static final String COCKPIT_ACCEPTED_STATUS = "ACCEPTED";

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
    private Map<Command.Type, CommandHandlerWrapper<Command<?>, Reply>> commandHandlers;

    @Autowired(required = false)
    @Qualifier("cockpitHelloCommandProducer")
    private CommandProducer helloCommandProducer;

    @Autowired
    private PluginManifest pluginManifest;

    @Autowired
    @Qualifier("cockpitObjectMapper")
    private ObjectMapper objectMapper;

    @Getter
    private boolean isPrimary = false;

    private boolean closedByCockpit = false;

    private long pongHandlerId;

    private CircuitBreaker circuitBreaker;

    private final String path;

    private HttpClient httpClient;

    private ClientChannel clientChannel;

    private final Collection<Runnable> onConnectListeners;
    private final Collection<Runnable> onDisconnectListeners;
    private final Collection<Runnable> onReadyListeners;
    private final Collection<Runnable> onPrimaryListeners;
    private final Collection<Runnable> onReplicaListeners;

    public WebSocketCockpitConnector() {
        this.path = "/ws/controller";
        onConnectListeners = new ConcurrentLinkedQueue<>();
        onDisconnectListeners = new ConcurrentLinkedQueue<>();
        onReadyListeners = new ConcurrentLinkedQueue<>();
        onPrimaryListeners = new ConcurrentLinkedQueue<>();
        onReplicaListeners = new ConcurrentLinkedQueue<>();
    }

    @Override
    protected void doStart() {
        if (enabled) {
            log.info("Cockpit connector is enabled. Starting connector.");

            commandHandlers.values().forEach(commandHandler -> commandHandler.setCallback(this::handleOnReadyNotification));

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

    @Override
    public void registerOnConnectListener(Runnable runnable) {
        onConnectListeners.add(runnable);
    }

    @Override
    public void registerOnDisconnectListener(Runnable runnable) {
        onDisconnectListeners.add(runnable);
    }

    @Override
    public void registerOnReadyListener(Runnable runnable) {
        onReadyListeners.add(runnable);
    }

    @Override
    public void registerOnPrimary(Runnable runnable) {
        onPrimaryListeners.add(runnable);
    }

    @Override
    public void registerOnReplica(Runnable runnable) {
        onReplicaListeners.add(runnable);
    }

    private void connect() {
        circuitBreaker
            .execute(this::doConnect)
            .setHandler(
                event -> {
                    // The connection has been established.
                    if (event.succeeded()) {
                        final WebSocket webSocket = event.result();
                        clientChannel =
                            new ClientChannel(webSocket, node, helloCommandProducer, ((Map) commandHandlers), pluginManifest, objectMapper);

                        this.notifyOnConnectListeners();

                        clientChannel.onClose(
                            () -> {
                                closedByCockpit = true;
                                webSocket.close();
                            }
                        );

                        clientChannel.onPrimary(
                            () -> {
                                this.isPrimary = true;
                                notifyOnPrimaryListeners();
                            }
                        );
                        clientChannel.onReplica(
                            () -> {
                                this.isPrimary = false;
                                notifyOnReplicaListeners();
                            }
                        );

                        clientChannel
                            .init()
                            .doOnError(throwable -> log.error("An error occurred when initializing the web socket channel.", throwable))
                            .subscribe(helloReply -> handleOnReadyNotification(null, helloReply));

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

                                notifyOnDisconnectListeners();

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

    private void notifyOnConnectListeners() {
        log.debug("Notifying all OnConnect listeners.");
        onConnectListeners.forEach(Runnable::run);
    }

    private void notifyOnDisconnectListeners() {
        log.debug("Notifying all OnDisconnect listeners.");
        onDisconnectListeners.forEach(Runnable::run);
    }

    private void notifyOnReadyListeners() {
        log.debug("Notifying all OnReady listeners.");
        onReadyListeners.forEach(Runnable::run);
    }

    private void notifyOnPrimaryListeners() {
        log.debug("Notifying all OnPrimary listeners.");
        onPrimaryListeners.forEach(Runnable::run);
    }

    private void notifyOnReplicaListeners() {
        log.debug("Notifying all OnReplica listeners.");
        onReplicaListeners.forEach(Runnable::run);
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
                webSocketEndpoint.resolvePath(path),
                result -> {
                    if (result.succeeded()) {
                        // Re-init endpoint counter.
                        webSocketEndpoint.reinitRetryCount();

                        log.info("Channel is now connected to Cockpit Controller through websocket from {}", webSocketEndpoint + path);
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
    public Single<Reply> sendCommand(Command<? extends Payload> command) {
        return clientChannel.send(command);
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

    private void handleOnReadyNotification(Command<?> command, Reply reply) {
        String installationStatus = null;

        if (reply instanceof HelloReply) {
            installationStatus = ((HelloReply) reply).getInstallationStatus();
        } else if (reply instanceof InstallationReply) {
            installationStatus = ((InstallationPayload) command.getPayload()).getStatus();
        }

        if (COCKPIT_ACCEPTED_STATUS.equals(installationStatus)) {
            notifyOnReadyListeners();
        }
    }
}

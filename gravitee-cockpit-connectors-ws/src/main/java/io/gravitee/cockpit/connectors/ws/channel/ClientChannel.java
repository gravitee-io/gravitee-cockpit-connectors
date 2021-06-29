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
package io.gravitee.cockpit.connectors.ws.channel;

import static io.gravitee.cockpit.api.command.Command.*;
import static io.gravitee.cockpit.api.command.CommandStatus.ERROR;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.cockpit.api.command.*;
import io.gravitee.cockpit.api.command.hello.HelloCommand;
import io.gravitee.cockpit.api.command.hello.HelloPayload;
import io.gravitee.cockpit.api.command.hello.HelloReply;
import io.gravitee.cockpit.api.command.ignored.IgnoredReply;
import io.gravitee.cockpit.connectors.ws.exceptions.ChannelClosedException;
import io.gravitee.common.util.Version;
import io.gravitee.node.api.Node;
import io.gravitee.plugin.core.api.PluginManifest;
import io.reactivex.*;
import io.reactivex.subjects.SingleSubject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class ClientChannel {

    private final WebSocket webSocket;
    private final Map<String, SingleEmitter<Reply>> resultEmitters;
    private final Map<Command.Type, CommandHandler<Command<?>, Reply>> commandHandlers;
    private final CommandProducer<HelloCommand, HelloReply> helloCommandProducer;

    private final Node node;
    private final PluginManifest pluginManifest;
    private final ObjectMapper objectMapper;
    private boolean goodbyeCommandReceived = false;
    private ClientChannelEventHandler closeHandler = () -> {};
    private ClientChannelEventHandler onPrimaryHandler = () -> {};
    private ClientChannelEventHandler onReplicaHandler = () -> {};

    public ClientChannel(
        WebSocket webSocket,
        Node node,
        CommandProducer<HelloCommand, HelloReply> helloCommandProducer,
        Map<Type, CommandHandler<Command<?>, Reply>> commandHandlers,
        PluginManifest pluginManifest,
        ObjectMapper objectMapper
    ) {
        this.webSocket = webSocket;
        this.node = node;
        this.pluginManifest = pluginManifest;
        this.objectMapper = objectMapper;
        this.resultEmitters = new ConcurrentHashMap<>();
        this.helloCommandProducer = helloCommandProducer;
        this.commandHandlers = commandHandlers;
    }

    public Single<HelloReply> init() {
        // Start listening.
        listen();

        // Hello command handler is only used once after connection occurred.
        return handleHello(node);
    }

    public void cleanup() {
        resultEmitters.forEach((type, emitter) -> emitter.onError(new ChannelClosedException()));
        resultEmitters.clear();
    }

    Single<HelloReply> handleHello(Node node) {
        HelloPayload payload = new HelloPayload();
        io.gravitee.cockpit.api.command.Node payloadNode = new io.gravitee.cockpit.api.command.Node();
        payloadNode.setApplication(node.application());
        payloadNode.setVersion(Version.RUNTIME_VERSION.MAJOR_VERSION);
        payloadNode.setConnectorVersion(pluginManifest.version());
        payloadNode.setHostname(node.hostname());
        payload.setNode(payloadNode);
        HelloCommand helloCommand = new HelloCommand(payload);

        Single<HelloCommand> helloCommandObs = Single.just(helloCommand);

        SingleSubject<HelloReply> helloHandshakeDone = SingleSubject.create();

        helloCommandObs
            .flatMap(
                command -> {
                    if (helloCommandProducer != null) {
                        // Give to opportunity to complete the HelloCommand with custom information.
                        return helloCommandProducer.prepare(helloCommand);
                    }
                    return Single.just(command);
                }
            )
            .flatMap(this::send)
            .flatMap(
                reply -> {
                    if (helloCommandProducer != null) {
                        return helloCommandProducer.handleReply((HelloReply) reply);
                    }
                    return Single.just((HelloReply) reply);
                }
            )
            .subscribe(
                reply -> {
                    helloHandshakeDone.onSuccess(reply);
                    log.info(
                        "HelloCommand replied with status [{}]. Installation status is [{}]",
                        reply.getCommandStatus(),
                        reply.getInstallationStatus()
                    );

                    if (shouldCloseConnection(reply)) {
                        closeHandler.handle();
                    }
                },
                t -> {
                    helloHandshakeDone.onError(t);
                    log.error("Unable to send HelloCommand", t);
                }
            );

        return helloHandshakeDone;
    }

    void handleGoodbye() {
        goodbyeCommandReceived = true;
    }

    void listen() {
        webSocket.handler(
            buffer -> {
                String incoming = buffer.toString();

                if (incoming.startsWith(PING_PONG_PREFIX)) {
                    return;
                }

                if (incoming.startsWith(PRIMARY_MESSAGE)) {
                    log.warn("I am the PRIMARY");
                    this.onPrimaryHandler.handle();
                    return;
                }

                if (incoming.startsWith(REPLICA_MESSAGE)) {
                    log.warn("I am a replica");
                    this.onReplicaHandler.handle();
                    return;
                }

                try {
                    if (incoming.startsWith(COMMAND_PREFIX)) {
                        Command<?> command = decodeJsonString(incoming.replace(COMMAND_PREFIX, ""), Command.class);
                        CommandHandler<Command<?>, Reply> commandHandler = commandHandlers.get(command.getType());

                        if (command.getType().equals(Type.GOODBYE_COMMAND)) {
                            handleGoodbye();
                        }

                        if (commandHandler != null) {
                            commandHandler
                                .handle(command)
                                .subscribe(
                                    reply -> {
                                        reply(reply);
                                        if (reply.stopOnErrorStatus() && reply.getCommandStatus() == ERROR) {
                                            webSocket.close();
                                        }
                                        if (goodbyeCommandReceived) {
                                            closeHandler.handle();
                                        }
                                    },
                                    e -> webSocket.close((short) 500, "Unexpected error")
                                );
                        } else {
                            log.info("No handler found for command type {}. Ignoring.", command.getType());
                            reply(new IgnoredReply(command.getId()));
                        }
                    } else if (incoming.startsWith(REPLY_PREFIX)) {
                        Reply reply = decodeJsonString(incoming.replace(REPLY_PREFIX, ""), Reply.class);
                        SingleEmitter<Reply> emitter = resultEmitters.remove(reply.getCommandId());

                        if (emitter != null) {
                            emitter.onSuccess(reply);
                        }
                    } else {
                        webSocket.close((short) 400, "Bad incoming content");
                    }
                } catch (Exception e) {
                    log.info(String.format("An error occurred when trying to decode incoming content [%s]. Ignore message.", incoming), e);
                }
            }
        );
    }

    public Single<Reply> send(Command<? extends Payload> command) {
        return Single
            .<Reply>create(
                emitter -> {
                    resultEmitters.put(command.getId(), emitter);
                    write(command).doOnError(emitter::onError).subscribe();
                }
            )
            // Cleanup result emitters list if cancelled by the upstream.
            .doOnDispose(() -> resultEmitters.remove(command.getId()));
    }

    public void onClose(ClientChannelEventHandler closeHandler) {
        this.closeHandler = closeHandler;
    }

    public void onPrimary(ClientChannelEventHandler onPrimaryHandler) {
        this.onPrimaryHandler = onPrimaryHandler;
    }

    public void onReplica(ClientChannelEventHandler onReplicaHandler) {
        this.onReplicaHandler = onReplicaHandler;
    }

    void reply(Reply reply) throws JsonProcessingException {
        this.webSocket.write(
                Buffer.buffer(REPLY_PREFIX + toJsonString(reply)),
                avoid -> {
                    if (avoid.failed()) {
                        log.error("An error occurred when trying to reply [{}]. Closing socket.", reply);
                        webSocket.close();
                    } else {
                        log.debug("Write to socket succeeded");
                    }
                }
            );
    }

    private Completable write(Command<? extends Payload> command) {
        return Completable.create(
            emitter ->
                this.webSocket.write(
                        Buffer.buffer(COMMAND_PREFIX + toJsonString(command)),
                        avoid -> {
                            if (avoid.failed()) {
                                log.error("An error occurred when trying to send command [{}]", command);
                                emitter.onError(new Exception("Write to socket failed"));
                            } else {
                                log.debug("Write to socket succeeded: [{}]", command);
                                emitter.onComplete();
                            }
                        }
                    )
        );
    }

    private boolean shouldCloseConnection(HelloReply reply) {
        String installationStatus = reply.getInstallationStatus();
        return installationStatus.equals("DELETED") || installationStatus.equals("INCOMPATIBLE");
    }

    private String toJsonString(Object object) throws JsonProcessingException {
        return objectMapper.writeValueAsString(object);
    }

    private <T> T decodeJsonString(String content, Class<T> clazz) throws JsonProcessingException {
        return objectMapper.readValue(content, clazz);
    }
}

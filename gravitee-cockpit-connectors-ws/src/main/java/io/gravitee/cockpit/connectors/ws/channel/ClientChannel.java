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

import io.gravitee.cockpit.api.command.*;
import io.gravitee.cockpit.api.command.hello.HelloCommand;
import io.gravitee.cockpit.api.command.hello.HelloPayload;
import io.gravitee.cockpit.api.command.hello.HelloReply;
import io.gravitee.cockpit.api.command.ignored.IgnoredReply;
import io.gravitee.cockpit.connectors.ws.exceptions.ChannelClosedException;
import io.gravitee.node.api.Node;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.MaybeEmitter;
import io.reactivex.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.Json;
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
    private final Map<String, MaybeEmitter<Reply>> resultEmitters;
    private final Map<Command.Type, CommandHandler<Command<?>, Reply>> commandHandlers;
    private final CommandProducer<HelloCommand, HelloReply> helloCommandProducer;

    public ClientChannel(
        WebSocket webSocket,
        Node node,
        CommandProducer<HelloCommand, HelloReply> helloCommandProducer,
        Map<Command.Type, CommandHandler<Command<?>, Reply>> commandHandlers
    ) {
        this.webSocket = webSocket;
        this.resultEmitters = new ConcurrentHashMap<>();
        this.helloCommandProducer = helloCommandProducer;
        this.commandHandlers = commandHandlers;

        // Start listening.
        listen();

        // Hello command handler is only used once after connection occurred.
        handleHello(node);
    }

    public void cleanup() {
        resultEmitters.forEach((type, emitter) -> emitter.onError(new ChannelClosedException()));
        resultEmitters.clear();
    }

    void handleHello(Node node) {
        HelloPayload payload = new HelloPayload();
        io.gravitee.cockpit.api.command.Node payloadNode = new io.gravitee.cockpit.api.command.Node();
        payloadNode.setApplication(node.application());
        payload.setNode(payloadNode);
        HelloCommand helloCommand = new HelloCommand(payload);

        Single<HelloCommand> helloCommandObs = Single.just(helloCommand);

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
            .flatMapMaybe(this::send)
            .flatMapSingle(
                reply -> {
                    if (helloCommandProducer != null) {
                        return helloCommandProducer.handleReply((HelloReply) reply);
                    }
                    return Single.just((HelloReply) reply);
                }
            )
            .subscribe(
                reply ->
                    log.info(
                        "HelloCommand replied with status [{}]. Installation status is [{}]",
                        reply.getCommandStatus(),
                        reply.getInstallationStatus()
                    ),
                t -> log.error("Unable to send HelloCommand", t)
            );
    }

    void listen() {
        webSocket.handler(
            buffer -> {
                String incoming = buffer.toString();

                if (incoming.startsWith(PING_PONG_PREFIX)) {
                    return;
                }

                try {
                    if (incoming.startsWith(COMMAND_PREFIX)) {
                        Command<?> command = Json.decodeValue(incoming.replace(COMMAND_PREFIX, ""), Command.class);
                        CommandHandler<Command<?>, Reply> commandHandler = commandHandlers.get(command.getType());

                        if (commandHandler != null) {
                            commandHandler
                                .handle(command)
                                .subscribe(
                                    reply -> {
                                        reply(reply);
                                        if (reply.stopOnErrorStatus() && reply.getCommandStatus() == ERROR) {
                                            webSocket.close();
                                        }
                                    },
                                    e -> webSocket.close((short) 500, "Unexpected error")
                                );
                        } else {
                            log.info("No handler found for command type {}. Ignoring.", command.getType());
                            reply(new IgnoredReply(command.getId()));
                        }
                    } else if (incoming.startsWith(REPLY_PREFIX)) {
                        Reply reply = Json.decodeValue(incoming.replace(REPLY_PREFIX, ""), Reply.class);
                        MaybeEmitter<Reply> emitter = resultEmitters.remove(reply.getCommandId());

                        if (emitter != null) {
                            emitter.onSuccess(reply);
                        }
                    } else {
                        webSocket.close((short) 400, "Bad incoming content");
                    }
                } catch (Exception e) {
                    log.warn("An error occurred when trying to decode incoming content [{}]. Closing Socket.", incoming);
                    webSocket.close((short) 500, "Unexpected error");
                }
            }
        );
    }

    Maybe<Reply> send(Command<? extends Payload> command) {
        return Maybe
            .<Reply>create(
                emitter -> {
                    resultEmitters.put(command.getId(), emitter);
                    write(command).doOnError(emitter::onError).subscribe();
                }
            )
            // Cleanup result emitters list if cancelled by the upstream.
            .doOnDispose(() -> resultEmitters.remove(command.getId()));
    }

    void reply(Reply reply) {
        this.webSocket.write(
                Buffer.buffer(REPLY_PREFIX + Json.encode(reply)),
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
                        Buffer.buffer(COMMAND_PREFIX + Json.encode(command)),
                        avoid -> {
                            if (avoid.failed()) {
                                log.error("An error occurred when trying to send command [{}]", command);
                                emitter.onError(new Exception("Write to socket failed"));
                            } else {
                                log.debug("Write to socket succeeded");
                                emitter.onComplete();
                            }
                        }
                    )
        );
    }
}

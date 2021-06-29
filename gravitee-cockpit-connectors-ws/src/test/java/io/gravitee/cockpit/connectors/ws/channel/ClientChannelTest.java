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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.cockpit.api.command.*;
import io.gravitee.cockpit.api.command.goodbye.GoodbyeCommand;
import io.gravitee.cockpit.api.command.hello.HelloCommand;
import io.gravitee.cockpit.api.command.hello.HelloReply;
import io.gravitee.cockpit.api.command.organization.OrganizationCommand;
import io.gravitee.cockpit.api.command.organization.OrganizationPayload;
import io.gravitee.cockpit.connectors.ws.exceptions.ChannelClosedException;
import io.gravitee.node.api.Node;
import io.gravitee.plugin.core.api.PluginManifest;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.Json;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class ClientChannelTest {

    private static final String APPLICATION_TEST = "gio-test";

    @Mock
    private WebSocket webSocket;

    @Mock
    private ClientChannelEventHandler closeHandler;

    @Mock
    private ClientChannelEventHandler onPrimaryHandler;

    @Mock
    private ClientChannelEventHandler onReplicaHandler;

    @Mock
    private Node node;

    @Mock
    private PluginManifest pluginManifest;

    @Captor
    ArgumentCaptor<Handler<Buffer>> listenCaptor;

    HashMap<Command.Type, CommandHandler<Command<?>, Reply>> commandHandlers;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ClientChannel cut;

    @BeforeEach
    public void init() {
        commandHandlers = new HashMap<>();
        when(webSocket.handler(listenCaptor.capture())).thenReturn(null);
        cut = new ClientChannel(webSocket, node, null, commandHandlers, pluginManifest, objectMapper);
        cut.onClose(closeHandler);
        cut.onPrimary(onPrimaryHandler);
        cut.onReplica(onReplicaHandler);
        cut.init();
        verify(webSocket).write(any(Buffer.class), any(Handler.class));
    }

    @Test
    public void listenPingPong() {
        listenCaptor.getValue().handle(Buffer.buffer("ping_pong: "));

        verifyNoMoreInteractions(webSocket);
    }

    @Test
    public void listenPrimary() {
        listenCaptor.getValue().handle(Buffer.buffer(Command.PRIMARY_MESSAGE));

        verify(onPrimaryHandler).handle();
        verifyNoMoreInteractions(webSocket);
    }

    @Test
    public void listenReplica() {
        listenCaptor.getValue().handle(Buffer.buffer(Command.REPLICA_MESSAGE));

        verify(onReplicaHandler).handle();
        verifyNoMoreInteractions(webSocket);
    }

    @Test
    public void listenInvalid() {
        listenCaptor.getValue().handle(Buffer.buffer("invalid: "));

        verify(webSocket).close(eq((short) 400), eq("Bad incoming content"));
    }

    @Test
    public void listenDecodeException() {
        listenCaptor.getValue().handle(Buffer.buffer("reply: invalid"));

        verifyNoMoreInteractions(webSocket);
    }

    @Test
    public void listenCommandNoHandler() {
        HelloCommand helloCommand = new HelloCommand();
        listenCaptor.getValue().handle(Buffer.buffer("command: " + Json.encode(helloCommand)));

        verify(webSocket)
            .write(
                argThat(
                    buffer ->
                        buffer.toString().matches("reply: \\{\"commandId\":\".*\",\"type\":\"IGNORED_REPLY\",\"commandStatus\":\"ERROR\"}")
                ),
                any(Handler.class)
            );
    }

    @Test
    public void listenCommand() {
        setCommandHandler(Command.Type.ORGANIZATION_COMMAND, Reply.Type.ORGANIZATION_REPLY, CommandStatus.SUCCEEDED, false);

        OrganizationPayload organizationPayload = new OrganizationPayload();
        OrganizationCommand organizationCommand = new OrganizationCommand(organizationPayload);
        listenCaptor.getValue().handle(Buffer.buffer("command: " + Json.encode(organizationCommand)));

        // Reply should be sent.
        verify(webSocket).write(argThat(buffer -> buffer.toString().startsWith("reply: ")), any(Handler.class));
    }

    @Test
    public void listenCommandError() {
        setCommandHandler(Command.Type.ORGANIZATION_COMMAND, Reply.Type.ORGANIZATION_REPLY, CommandStatus.ERROR, false);

        OrganizationPayload organizationPayload = new OrganizationPayload();
        OrganizationCommand organizationCommand = new OrganizationCommand(organizationPayload);
        listenCaptor.getValue().handle(Buffer.buffer("command: " + Json.encode(organizationCommand)));

        // Reply should be sent.
        verify(webSocket).write(argThat(buffer -> buffer.toString().startsWith("reply: ")), any(Handler.class));
        verifyNoMoreInteractions(webSocket);
    }

    @Test
    public void listenCommandStopOnError() {
        setCommandHandler(Command.Type.ORGANIZATION_COMMAND, Reply.Type.ORGANIZATION_REPLY, CommandStatus.ERROR, true);

        OrganizationPayload organizationPayload = new OrganizationPayload();
        OrganizationCommand organizationCommand = new OrganizationCommand(organizationPayload);
        listenCaptor.getValue().handle(Buffer.buffer("command: " + Json.encode(organizationCommand)));

        // Reply should be sent.
        verify(webSocket).write(argThat(buffer -> buffer.toString().startsWith("reply: ")), any(Handler.class));
        verify(webSocket).close();
    }

    @Test
    public void listenGoodbyeCommand() {
        setCommandHandler(Command.Type.GOODBYE_COMMAND, Reply.Type.GOODBYE_REPLY, CommandStatus.SUCCEEDED, true);

        GoodbyeCommand command = new GoodbyeCommand();
        listenCaptor.getValue().handle(Buffer.buffer("command: " + Json.encode(command)));

        // Reply should be sent.
        verify(webSocket).write(argThat(buffer -> buffer.toString().startsWith("reply: ")), any(Handler.class));
        verifyNoMoreInteractions(webSocket);

        // Close handle should be call
        verify(closeHandler).handle();
    }

    @Test
    public void handleHelloWithHelloCommandProducer() {
        final CommandProducer<HelloCommand, HelloReply> helloCommandCommandProducer = mock(CommandProducer.class);
        when(helloCommandCommandProducer.prepare(any(HelloCommand.class))).thenAnswer(i -> Single.just(i.getArgument(0)));

        cut = new ClientChannel(webSocket, node, helloCommandCommandProducer, commandHandlers, pluginManifest, objectMapper);
        cut.init();

        setCommandHandler(Command.Type.ORGANIZATION_COMMAND, Reply.Type.ORGANIZATION_REPLY, CommandStatus.SUCCEEDED, false);

        OrganizationPayload organizationPayload = new OrganizationPayload();
        OrganizationCommand organizationCommand = new OrganizationCommand(organizationPayload);
        listenCaptor.getValue().handle(Buffer.buffer("command: " + Json.encode(organizationCommand)));

        // Reply should be sent.
        verify(webSocket).write(argThat(buffer -> buffer.toString().startsWith("reply: ")), any(Handler.class));

        verify(helloCommandCommandProducer, times(1)).prepare(any(HelloCommand.class));
    }

    @Test
    public void cleanup() {
        OrganizationPayload organizationPayload = new OrganizationPayload();
        OrganizationCommand organizationCommand = new OrganizationCommand(organizationPayload);

        TestObserver<Reply> obs = cut.send(organizationCommand).test();

        cut.cleanup();

        // Pending command should result in an error.
        obs.awaitTerminalEvent();
        obs.assertError(ChannelClosedException.class);
    }

    private void setCommandHandler(Command.Type commandType, Reply.Type replyType, CommandStatus expectedStatus, boolean stopOnError) {
        commandHandlers.put(
            commandType,
            new CommandHandler<Command<?>, Reply>() {
                @Override
                public Command.Type handleType() {
                    return commandType;
                }

                @Override
                public Single<Reply> handle(Command<?> command) {
                    return Single.just(
                        new Reply(replyType) {
                            @Override
                            public String getCommandId() {
                                return command.getId();
                            }

                            @Override
                            public Type getType() {
                                return replyType;
                            }

                            @Override
                            public CommandStatus getCommandStatus() {
                                return expectedStatus;
                            }

                            @Override
                            public boolean stopOnErrorStatus() {
                                return stopOnError;
                            }
                        }
                    );
                }
            }
        );
    }
}

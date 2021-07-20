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
package io.gravitee.cockpit.connectors.core.handlers.alert;

import static io.gravitee.cockpit.api.command.CommandStatus.ERROR;
import static io.gravitee.cockpit.api.command.CommandStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.gravitee.alert.api.condition.StringCondition;
import io.gravitee.alert.api.trigger.Trigger;
import io.gravitee.alert.api.trigger.TriggerProvider;
import io.gravitee.cockpit.api.command.alert.trigger.create.AlertTriggerCommand;
import io.gravitee.cockpit.api.command.alert.trigger.create.AlertTriggerPayload;
import io.gravitee.cockpit.api.command.alert.trigger.create.AlertTriggerReply;
import io.reactivex.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertTriggerCommandHandlerTest {

    @Mock
    private TriggerProvider triggerProvider;

    private AlertTriggerCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AlertTriggerCommandHandler(triggerProvider);
    }

    @Test
    void handle_registers_a_trigger_using_trigger_provider() {
        AlertTriggerCommand command = anAlertTriggerCommand();

        TestObserver<AlertTriggerReply> obs = handler.handle(command).test();
        obs.awaitTerminalEvent();
        obs.assertComplete();

        verify(triggerProvider).register(command.getPayload().getTrigger());
    }

    @Test
    void handle_returns_succeeded_reply() {
        AlertTriggerCommand command = anAlertTriggerCommand();

        TestObserver<AlertTriggerReply> obs = handler.handle(command).test();
        obs.awaitTerminalEvent();
        obs.assertComplete();

        obs.assertValue(
            reply -> {
                assertThat(reply)
                    .extracting(AlertTriggerReply::getCommandId, AlertTriggerReply::getCommandStatus)
                    .containsOnly(command.getId(), SUCCEEDED);

                return true;
            }
        );
    }

    @Test
    void handle_returns_error_reply_when_something_wrong_happens() {
        doThrow(new RuntimeException()).when(triggerProvider).register(any());

        AlertTriggerCommand command = anAlertTriggerCommand();

        TestObserver<AlertTriggerReply> obs = handler.handle(command).test();
        obs.awaitTerminalEvent();
        obs.assertComplete();

        obs.assertValue(
            reply -> {
                assertThat(reply)
                    .extracting(AlertTriggerReply::getCommandId, AlertTriggerReply::getCommandStatus)
                    .containsOnly(command.getId(), ERROR);

                return true;
            }
        );
    }

    private AlertTriggerCommand anAlertTriggerCommand() {
        var trigger = Trigger
            .on("a source")
            .id("trigger#id")
            .name("a trigger")
            .condition(StringCondition.matches("node.event", "NODE_STOP", false).build())
            .build();

        var payload = new AlertTriggerPayload();
        payload.setTrigger(trigger);

        var command = new AlertTriggerCommand();
        command.setPayload(payload);

        return command;
    }
}

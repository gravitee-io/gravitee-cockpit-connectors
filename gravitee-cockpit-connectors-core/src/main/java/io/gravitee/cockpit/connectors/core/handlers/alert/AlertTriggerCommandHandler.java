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

import io.gravitee.alert.api.trigger.Trigger;
import io.gravitee.alert.api.trigger.TriggerProvider;
import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.CommandHandler;
import io.gravitee.cockpit.api.command.CommandStatus;
import io.gravitee.cockpit.api.command.alert.trigger.create.AlertTriggerCommand;
import io.gravitee.cockpit.api.command.alert.trigger.create.AlertTriggerReply;
import io.reactivex.Single;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AlertTriggerCommandHandler implements CommandHandler<AlertTriggerCommand, AlertTriggerReply> {

    private final TriggerProvider triggerProvider;

    public AlertTriggerCommandHandler(TriggerProvider triggerProvider) {
        this.triggerProvider = triggerProvider;
    }

    @Override
    public Command.Type handleType() {
        return Command.Type.ALERT_TRIGGER_COMMAND;
    }

    @Override
    public Single<AlertTriggerReply> handle(AlertTriggerCommand command) {
        log.debug("Alert trigger command [{}] has been received.", command.getId());

        return registerAETrigger(command.getPayload().getTrigger())
            .map(registered -> new AlertTriggerReply(command.getId(), CommandStatus.SUCCEEDED))
            .onErrorResumeNext(throwable -> Single.just(new AlertTriggerReply(command.getId(), CommandStatus.ERROR)));
    }

    private Single<Trigger> registerAETrigger(Trigger trigger) {
        return Single.defer(
            () -> {
                triggerProvider.register(trigger);
                log.debug("Alert trigger [{}] has been pushed to alert system.", trigger.getId());
                return Single.just(trigger);
            }
        );
    }
}

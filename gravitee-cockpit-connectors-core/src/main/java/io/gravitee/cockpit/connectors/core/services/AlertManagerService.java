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
package io.gravitee.cockpit.connectors.core.services;

import io.gravitee.alert.api.trigger.Trigger;
import io.gravitee.alert.api.trigger.TriggerProvider;
import io.gravitee.cockpit.api.CockpitConnector;
import io.gravitee.cockpit.api.command.CommandStatus;
import io.gravitee.cockpit.api.command.alert.trigger.list.ListAlertTriggersCommand;
import io.gravitee.cockpit.api.command.alert.trigger.list.ListAlertTriggersReply;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

@Slf4j
public class AlertManagerService implements InitializingBean {

    private final CockpitConnector cockpitConnector;
    private final TriggerProvider triggerProvider;

    public AlertManagerService(CockpitConnector cockpitConnector, TriggerProvider triggerProvider) {
        this.cockpitConnector = cockpitConnector;
        this.triggerProvider = triggerProvider;
    }

    @Override
    public void afterPropertiesSet() {
        log.info("Starting alert manager service");
        cockpitConnector.registerOnReadyListener(
            () ->
                triggerProvider.addListener(
                    (TriggerProvider.OnConnectionListener) () -> {
                        log.info("Connected to alerting system. Sync cockpit alert triggers...");
                        registerCockpitTriggers()
                            .subscribe(
                                () -> {
                                    log.info("All cockpit triggers registered");
                                }
                            );
                    }
                )
        );
    }

    private Completable registerCockpitTriggers() {
        ListAlertTriggersCommand command = new ListAlertTriggersCommand();
        return this.cockpitConnector.sendCommand(command)
            .flatMapMaybe(
                reply -> {
                    if (reply.getCommandStatus().equals(CommandStatus.ERROR)) {
                        log.error("Fail to get existing triggers");
                        return Maybe.empty();
                    }

                    List<Trigger> triggers = ((ListAlertTriggersReply) reply).getTriggers();
                    return Maybe.just(triggers);
                }
            )
            .doOnSuccess(triggers -> triggers.forEach(triggerProvider::register))
            .ignoreElement();
    }
}
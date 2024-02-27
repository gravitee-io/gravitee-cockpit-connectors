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
package io.gravitee.cockpit.connectors.core.spring;

import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.CommandHandler;
import io.gravitee.cockpit.api.command.Reply;
import io.gravitee.cockpit.connectors.core.internal.CommandHandlerWrapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@ComponentScan("io.gravitee.cockpit.connectors.core.handlers")
public class CommandHandlersConfiguration {

    @Bean("cockpitCommandHandlers")
    Map<Command.Type, CommandHandlerWrapper<Command<?>, Reply>> allCommandHandlers(ApplicationContext context) {
        Map<Command.Type, CommandHandlerWrapper<Command<?>, Reply>> allHandlers = new HashMap<>();

        ApplicationContext applicationContext = context;

        while (applicationContext != null) {
            applicationContext
                .getBeansOfType(CommandHandler.class)
                .forEach(
                    (s, commandHandler) ->
                        allHandlers.put(commandHandler.handleType(), new CommandHandlerWrapper<Command<?>, Reply>(commandHandler))
                );
            applicationContext = applicationContext.getParent();
        }

        return allHandlers;
    }
}

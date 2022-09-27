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
package io.gravitee.cockpit.connectors.core.internal;

import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.CommandHandler;
import io.gravitee.cockpit.api.command.Reply;
import io.reactivex.rxjava3.core.Single;
import java.util.function.BiConsumer;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CommandHandlerWrapper<C extends Command<?>, R extends Reply> implements CommandHandler<C, R> {

    private final CommandHandler<C, R> delegate;

    private BiConsumer<C, R> callback;

    public CommandHandlerWrapper(CommandHandler<C, R> delegate) {
        this.delegate = delegate;
        this.callback = (c, r) -> {};
    }

    @Override
    public Command.Type handleType() {
        return delegate.handleType();
    }

    @Override
    public Single<R> handle(C command) {
        return delegate.handle(command).doOnSuccess(reply -> callback.accept(command, reply));
    }

    public void setCallback(BiConsumer<C, R> callback) {
        this.callback = callback;
    }
}

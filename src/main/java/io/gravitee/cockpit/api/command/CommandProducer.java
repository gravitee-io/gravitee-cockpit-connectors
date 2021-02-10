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
package io.gravitee.cockpit.api.command;

import io.gravitee.cockpit.api.command.hello.HelloReply;
import io.reactivex.Single;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@FunctionalInterface
public interface CommandProducer<T, U> {
    /**
     * Returns the type of command produced by this command producer.
     * The type is used to determine the right producer to use when a command need to be send.
     * @return the type of command produced.
     */
    Command.Type produceType();

    /**
     * Method invoked before sending the command. This allows to add some information to the command before sending it.
     * Does nothing by default and return the command passed in parameter without alter it.
     * @return the command with all necessary information.
     */
    default Single<T> prepare(T command) {
        return Single.just(command);
    }

    /**
     * Method invoke after a reply has been received. This allows to a command producer to execute some operations after a command has been processed and a reply is received.
     *
     * @param reply the reply.
     * @return the same reply altered if necessary.
     */
    default Single<U> handleReply(U reply) {
        return Single.just(reply);
    }
}

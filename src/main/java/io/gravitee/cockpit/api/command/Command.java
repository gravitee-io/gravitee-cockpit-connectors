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

import com.fasterxml.jackson.annotation.*;
import io.gravitee.cockpit.api.command.environment.EnvironmentCommand;
import io.gravitee.cockpit.api.command.hello.HelloCommand;
import io.gravitee.cockpit.api.command.installation.InstallationCommand;
import io.gravitee.cockpit.api.command.membership.MembershipCommand;
import io.gravitee.cockpit.api.command.organization.OrganizationCommand;
import io.gravitee.cockpit.api.command.user.UserCommand;
import io.gravitee.common.utils.UUID;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = OrganizationCommand.class, name = "ORGANIZATION_COMMAND"),
        @JsonSubTypes.Type(value = EnvironmentCommand.class, name = "ENVIRONMENT_COMMAND"),
        @JsonSubTypes.Type(value = UserCommand.class, name = "USER_COMMAND"),
        @JsonSubTypes.Type(value = MembershipCommand.class, name = "MEMBERSHIP_COMMAND"),
        @JsonSubTypes.Type(value = InstallationCommand.class, name = "INSTALLATION_COMMAND"),
        @JsonSubTypes.Type(value = HelloCommand.class, name = "HELLO_COMMAND"),
    }
)
public abstract class Command<T extends Payload> {

    public static final String COMMAND_PREFIX = "command: ";
    public static final String REPLY_PREFIX = "reply: ";
    public static final String PING_PONG_PREFIX = "ping_pong: ";

    protected String id;

    protected T payload;

    protected Type type;

    public enum Type {
        ORGANIZATION_COMMAND,
        ENVIRONMENT_COMMAND,
        HELLO_COMMAND,
        USER_COMMAND,
        MEMBERSHIP_COMMAND,
        INSTALLATION_COMMAND,
    }

    public Command(Type type) {
        this.id = UUID.random().toString();
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    public Type getType() {
        return type;
    }
}

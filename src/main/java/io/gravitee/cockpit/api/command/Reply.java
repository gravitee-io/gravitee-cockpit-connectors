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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.gravitee.cockpit.api.command.environment.EnvironmentReply;
import io.gravitee.cockpit.api.command.hello.HelloReply;
import io.gravitee.cockpit.api.command.ignored.IgnoredReply;
import io.gravitee.cockpit.api.command.installation.InstallationReply;
import io.gravitee.cockpit.api.command.membership.MembershipReply;
import io.gravitee.cockpit.api.command.organization.OrganizationReply;
import io.gravitee.cockpit.api.command.user.UserReply;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = IgnoredReply.class, name = "IGNORED_REPLY"),
        @JsonSubTypes.Type(value = OrganizationReply.class, name = "ORGANIZATION_REPLY"),
        @JsonSubTypes.Type(value = EnvironmentReply.class, name = "ENVIRONMENT_REPLY"),
        @JsonSubTypes.Type(value = UserReply.class, name = "USER_REPLY"),
        @JsonSubTypes.Type(value = MembershipReply.class, name = "MEMBERSHIP_REPLY"),
        @JsonSubTypes.Type(value = InstallationReply.class, name = "INSTALLATION_REPLY"),
        @JsonSubTypes.Type(value = HelloReply.class, name = "HELLO_REPLY"),
    }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Reply {

    public enum Type {
        IGNORED_REPLY,
        ORGANIZATION_REPLY,
        ENVIRONMENT_REPLY,
        HELLO_REPLY,
        USER_REPLY,
        MEMBERSHIP_REPLY,
        INSTALLATION_REPLY,
    }

    protected String commandId;

    protected Type type;

    protected CommandStatus commandStatus;

    public Reply(Type type) {
        this.type = type;
    }

    public Reply(Type type, String commandId, CommandStatus commandStatus) {
        this.type = type;
        this.commandId = commandId;
        this.commandStatus = commandStatus;
    }

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    public Type getType() {
        return type;
    }

    public CommandStatus getCommandStatus() {
        return commandStatus;
    }

    public void setCommandStatus(CommandStatus commandStatus) {
        this.commandStatus = commandStatus;
    }

    public boolean stopOnErrorStatus() {
        return false;
    }
}

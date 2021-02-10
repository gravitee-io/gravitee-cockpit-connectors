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
package io.gravitee.cockpit.api.command.hello;

import io.gravitee.cockpit.api.command.Node;
import io.gravitee.cockpit.api.command.Payload;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HelloPayload implements Payload {

    /**
     * Contains all necessary information about the node.
     */
    private Node node;

    /**
     * The default organization identifier, <code>null</code> if there is no default organization defined.
     */
    private String defaultOrganizationId;

    /**
     * The default environment identifier, <code>null</code> if there is no default organization defined.
     */
    private String defaultEnvironmentId;

    /**
     * Additional information.
     */
    private Map<String, String> additionalInformation = new HashMap<>();

    public HelloPayload() {}

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public String getDefaultOrganizationId() {
        return defaultOrganizationId;
    }

    public void setDefaultOrganizationId(String defaultOrganizationId) {
        this.defaultOrganizationId = defaultOrganizationId;
    }

    public String getDefaultEnvironmentId() {
        return defaultEnvironmentId;
    }

    public void setDefaultEnvironmentId(String defaultEnvironmentId) {
        this.defaultEnvironmentId = defaultEnvironmentId;
    }

    public Map<String, String> getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(Map<String, String> additionalInformation) {
        this.additionalInformation = additionalInformation;
    }
}

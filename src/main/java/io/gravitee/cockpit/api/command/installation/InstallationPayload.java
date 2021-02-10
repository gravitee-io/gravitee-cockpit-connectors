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
package io.gravitee.cockpit.api.command.installation;

import io.gravitee.cockpit.api.command.Payload;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InstallationPayload implements Payload {

    /**
     * The cockpit installation id.
     */
    private String id;

    /**
     * The identifier of the installation in the target system.
     */
    private String externalId;

    /**
     * The scope of this installation (AM, APIM, ...).
     */
    private String scope;

    /**
     * The status of the installation in a Cockpit perspective (pending, accepted, rejected, ...).
     * The status influences possible actions such as creating an organization or environment on that installation.
     */
    private String status;

    /**
     * The common name of the installation (ex: 'APIM installation for performance tests').
     */
    private String name;

    /**
     * A short description of the installation.
     */
    private String description;

    /**
     * Additional information about this installation.
     */
    private Map<String, String> additionalInformation = new HashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(Map<String, String> additionalInformation) {
        this.additionalInformation = additionalInformation;
    }
}

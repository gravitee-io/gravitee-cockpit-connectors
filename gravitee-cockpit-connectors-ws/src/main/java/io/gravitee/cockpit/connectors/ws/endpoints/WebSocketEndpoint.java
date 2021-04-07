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
package io.gravitee.cockpit.connectors.ws.endpoints;

import java.net.URI;
import lombok.Getter;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebSocketEndpoint {

    private static final String HTTPS_SCHEME = "https";
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    @Getter
    private final String url;

    private final URI uri;

    @Getter
    private int retryCount;

    public WebSocketEndpoint(String url) {
        this.url = url;
        this.uri = URI.create(url);
        this.retryCount = 0;
    }

    public int incrementRetryCount() {
        this.retryCount++;
        return retryCount;
    }

    public void reinitRetryCount() {
        this.retryCount = 0;
    }

    public int getPort() {
        return uri.getPort() != -1 ? uri.getPort() : (HTTPS_SCHEME.equals(uri.getScheme()) ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT);
    }

    public String getHost() {
        return uri.getHost();
    }

    public String resolvePath(String path) {
        return uri.resolve(path).getRawPath();
    }

    public boolean isRemovable() {
        return false;
    }
}

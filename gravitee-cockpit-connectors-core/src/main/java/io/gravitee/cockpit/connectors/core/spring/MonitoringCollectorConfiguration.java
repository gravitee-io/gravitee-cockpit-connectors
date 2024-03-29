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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.cockpit.connectors.core.services.MonitoringCollectorService;
import io.gravitee.node.monitoring.NodeMonitoringService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MonitoringCollectorConfiguration {

    public static final String DEFAULT_CRON_TRIGGER = "*/5 * * * * *";

    @Bean
    MonitoringCollectorService monitoringCollectorService(
        NodeMonitoringService nodeMonitoringService,
        @Qualifier("cockpitObjectMapper") ObjectMapper objectMapper,
        @Value("${cockpit.monitoring.cron:" + DEFAULT_CRON_TRIGGER + "}") String cronTrigger
    ) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setThreadNamePrefix("cockpit-monitoring-collector-");
        taskScheduler.setPoolSize(1);
        taskScheduler.initialize();
        return new MonitoringCollectorService(nodeMonitoringService, taskScheduler, cronTrigger, objectMapper);
    }
}

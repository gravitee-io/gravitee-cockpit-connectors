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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.cockpit.api.CockpitConnector;
import io.gravitee.cockpit.api.command.healthcheck.HealthCheckCommand;
import io.gravitee.cockpit.api.command.healthcheck.HealthCheckPayload;
import io.gravitee.cockpit.api.command.healthcheck.HealthCheckProbe;
import io.gravitee.cockpit.api.command.node.NodeCommand;
import io.gravitee.cockpit.api.command.node.NodePayload;
import io.gravitee.cockpit.api.command.node.NodePlugin;
import io.gravitee.node.api.Monitoring;
import io.gravitee.node.api.healthcheck.HealthCheck;
import io.gravitee.node.api.infos.NodeInfos;
import io.gravitee.node.api.infos.PluginInfos;
import io.gravitee.node.monitoring.NodeMonitoringService;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class MonitoringCollectorService implements InitializingBean {

    @Value("${cockpit.monitoring.cron:*/5 * * * * *}")
    private String cronTrigger;

    private final NodeMonitoringService nodeMonitoringService;
    private final CockpitConnector cockpitConnector;
    private final TaskScheduler taskScheduler;
    private final ObjectMapper objectMapper;

    private long lastRefreshAt;
    private long lastDelay;
    boolean ready;

    public MonitoringCollectorService(
        NodeMonitoringService nodeMonitoringService,
        CockpitConnector cockpitConnector,
        TaskScheduler taskScheduler,
        ObjectMapper objectMapper
    ) {
        this.nodeMonitoringService = nodeMonitoringService;
        this.cockpitConnector = cockpitConnector;
        this.taskScheduler = taskScheduler;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Starting monitoring collector service");

        lastRefreshAt = System.currentTimeMillis() - ChronoUnit.HOURS.getDuration().toMillis();
        lastDelay = 0;

        cockpitConnector.registerOnReadyListener(() -> this.ready = true);
        cockpitConnector.registerOnDisconnectListener(() -> this.ready = false);

        taskScheduler.schedule(this::collectAndSend, new CronTrigger(cronTrigger));
    }

    protected void collectAndSend() {
        if (!ready) {
            log.debug("Cockpit connector is not ready to accept command or installation is not accepted yet. Skip monitoring propagation.");
            return;
        }

        long nextLastRefreshAt = System.currentTimeMillis();

        log.debug("Collecting and sending monitoring data to Cockpit");

        // First send node infos.
        nodeMonitoringService
            .findByTypeAndTimeframe(Monitoring.NODE_INFOS, lastRefreshAt - lastDelay, nextLastRefreshAt)
            .map(this::convertToNodeCommand)
            .flatMapSingle(cockpitConnector::sendCommand)
            .blockingSubscribe();

        // Then send health checks.
        nodeMonitoringService
            .findByTypeAndTimeframe(Monitoring.HEALTH_CHECK, lastRefreshAt - lastDelay, nextLastRefreshAt)
            .map(this::convertToHealthCheckCommand)
            .flatMapSingle(cockpitConnector::sendCommand)
            .blockingSubscribe();

        lastRefreshAt = nextLastRefreshAt;
        lastDelay = System.currentTimeMillis() - nextLastRefreshAt;
    }

    private NodeCommand convertToNodeCommand(Monitoring monitoring) throws JsonProcessingException {
        final NodeCommand nodeCommand = new NodeCommand();
        final NodePayload nodePayload = new NodePayload();

        NodeInfos nodeInfos = objectMapper.readValue(monitoring.getPayload(), NodeInfos.class);
        nodePayload.setNodeId(nodeInfos.getId());
        nodePayload.setName(nodeInfos.getName());
        nodePayload.setApplication(nodeInfos.getApplication());
        nodePayload.setEvaluatedAt(nodeInfos.getEvaluatedAt());
        nodePayload.setStatus(NodePayload.Status.valueOf(nodeInfos.getStatus().name()));
        nodePayload.setVersion(nodeInfos.getVersion());
        nodePayload.setShardingTags(nodeInfos.getTags());
        nodePayload.setTenant(nodeInfos.getTenant());
        nodePayload.setJdkVersion(nodeInfos.getJdkVersion());
        nodePayload.setPlugins(nodeInfos.getPluginInfos().stream().map(this::convertToNodePlugin).collect(Collectors.toList()));

        nodeCommand.setPayload(nodePayload);
        return nodeCommand;
    }

    private NodePlugin convertToNodePlugin(PluginInfos pluginInfos) {
        final NodePlugin nodePlugin = new NodePlugin();
        nodePlugin.setName(pluginInfos.getName());
        nodePlugin.setVersion(pluginInfos.getVersion());

        return nodePlugin;
    }

    private HealthCheckCommand convertToHealthCheckCommand(Monitoring monitoring) throws JsonProcessingException {
        final HealthCheckCommand healthCheckCommand = new HealthCheckCommand();
        final HealthCheckPayload healthcheckPayload = new HealthCheckPayload();
        final HealthCheck healthCheck = objectMapper.readValue(monitoring.getPayload(), HealthCheck.class);
        final List<HealthCheckProbe> probes = healthCheck
            .getResults()
            .entrySet()
            .stream()
            .map(this::convertToHealthCheckProbe)
            .collect(Collectors.toList());

        healthcheckPayload.setNodeId(monitoring.getNodeId());
        healthcheckPayload.setEvaluatedAt(healthCheck.getEvaluatedAt());
        healthcheckPayload.setProbes(probes);
        healthCheckCommand.setPayload(healthcheckPayload);

        return healthCheckCommand;
    }

    private HealthCheckProbe convertToHealthCheckProbe(java.util.Map.Entry<String, io.gravitee.node.api.healthcheck.Result> e) {
        HealthCheckProbe probe = new HealthCheckProbe();
        probe.setKey(e.getKey());
        probe.setStatus(e.getValue().isHealthy() ? HealthCheckProbe.Status.HEALTHY : HealthCheckProbe.Status.UNHEALTHY);
        probe.setStatusMessage(e.getValue().getMessage());
        return probe;
    }
}

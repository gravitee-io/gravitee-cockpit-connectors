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
package io.gravitee.cockpit.connectors.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.cockpit.api.command.v1.node.NodeCommand;
import io.gravitee.cockpit.api.command.v1.node.NodeCommandPayload;
import io.gravitee.cockpit.api.command.v1.node.healthcheck.NodeHealthCheckCommand;
import io.gravitee.cockpit.api.command.v1.node.healthcheck.NodeHealthCheckCommandPayload;
import io.gravitee.exchange.api.connector.ExchangeConnector;
import io.gravitee.node.api.Monitoring;
import io.gravitee.node.api.healthcheck.HealthCheck;
import io.gravitee.node.api.infos.NodeInfos;
import io.gravitee.node.api.infos.PluginInfos;
import io.gravitee.node.monitoring.NodeMonitoringService;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class MonitoringCollectorService {

    private final NodeMonitoringService nodeMonitoringService;
    private final TaskScheduler taskScheduler;
    private final String cronTrigger;
    private final ObjectMapper objectMapper;

    private long lastRefreshAt;
    private long lastDelay;
    private ExchangeConnector exchangeConnector;
    private ScheduledFuture<?> cronTask;

    public void start(final ExchangeConnector exchangeConnector) {
        log.info("Starting cockpit monitoring collector service");
        if (this.exchangeConnector != null) {
            this.stop();
        }
        this.exchangeConnector = exchangeConnector;
        this.lastRefreshAt = System.currentTimeMillis() - ChronoUnit.HOURS.getDuration().toMillis();
        this.lastDelay = 0;
        this.cronTask = taskScheduler.schedule(this::collectAndSend, new CronTrigger(cronTrigger));
    }

    public void stop() {
        log.info("Stopping cockpit monitoring collector service");
        this.exchangeConnector = null;
        if (this.cronTask != null) {
            this.cronTask.cancel(false);
            this.cronTask = null;
        }
    }

    protected void collectAndSend() {
        if (exchangeConnector != null) {
            if (!exchangeConnector.isActive()) {
                log.debug(
                    "Cockpit connector is not ready to accept command or installation is not accepted yet. Skip monitoring propagation."
                );
                return;
            }

            if (!exchangeConnector.isPrimary()) {
                log.debug("Cockpit connector is not primary. Skip monitoring propagation.");
                return;
            }

            long from = lastRefreshAt - lastDelay;
            long nextLastRefreshAt = System.currentTimeMillis();

            log.debug("Collecting and sending monitoring data to Cockpit");

            // First send node infos.
            nodeMonitoringService
                .findByTypeAndTimeframe(Monitoring.NODE_INFOS, from, nextLastRefreshAt)
                .map(this::convertToNodeCommand)
                .flatMapSingle(exchangeConnector::sendCommand)
                .blockingSubscribe();

            // Then send health checks.
            nodeMonitoringService
                .findByTypeAndTimeframe(Monitoring.HEALTH_CHECK, from, nextLastRefreshAt)
                .map(this::convertToHealthCheckCommand)
                .flatMapSingle(exchangeConnector::sendCommand)
                .blockingSubscribe();

            lastRefreshAt = nextLastRefreshAt;
            // Adding one second delay to make sure we don't miss events
            lastDelay = System.currentTimeMillis() - nextLastRefreshAt + ChronoUnit.SECONDS.getDuration().toMillis();
        }
    }

    private NodeCommand convertToNodeCommand(Monitoring monitoring) throws JsonProcessingException {
        NodeInfos nodeInfos = objectMapper.readValue(monitoring.getPayload(), NodeInfos.class);
        return new NodeCommand(
            NodeCommandPayload
                .builder()
                .nodeId(nodeInfos.getId())
                .installationId(exchangeConnector.targetId())
                .name(nodeInfos.getName())
                .application(nodeInfos.getApplication())
                .evaluatedAt(nodeInfos.getEvaluatedAt())
                .status(NodeCommandPayload.Status.valueOf(nodeInfos.getStatus().name()))
                .version(nodeInfos.getVersion())
                .shardingTags(nodeInfos.getTags() == null ? List.of() : nodeInfos.getTags().stream().filter(tag -> !tag.isBlank()).toList())
                .tenant(nodeInfos.getTenant())
                .jdkVersion(nodeInfos.getJdkVersion())
                .plugins(nodeInfos.getPluginInfos().stream().map(this::convertToNodePlugin).toList())
                .build()
        );
    }

    private NodeCommandPayload.NodePlugin convertToNodePlugin(PluginInfos pluginInfos) {
        return NodeCommandPayload.NodePlugin.builder().name(pluginInfos.getName()).version(pluginInfos.getVersion()).build();
    }

    private NodeHealthCheckCommand convertToHealthCheckCommand(Monitoring monitoring) throws JsonProcessingException {
        final HealthCheck healthCheck = objectMapper.readValue(monitoring.getPayload(), HealthCheck.class);
        return new NodeHealthCheckCommand(
            NodeHealthCheckCommandPayload
                .builder()
                .nodeId(monitoring.getNodeId())
                .installationId(exchangeConnector.targetId())
                .evaluatedAt(healthCheck.getEvaluatedAt())
                .isHealthy(healthCheck.isHealthy())
                .probes(healthCheck.getResults().entrySet().stream().map(this::convertToHealthCheckProbe).toList())
                .build()
        );
    }

    private NodeHealthCheckCommandPayload.HealthCheckProbe convertToHealthCheckProbe(
        java.util.Map.Entry<String, io.gravitee.node.api.healthcheck.Result> e
    ) {
        return NodeHealthCheckCommandPayload.HealthCheckProbe
            .builder()
            .key(e.getKey())
            .status(
                e.getValue().isHealthy()
                    ? NodeHealthCheckCommandPayload.HealthCheckProbe.Status.HEALTHY
                    : NodeHealthCheckCommandPayload.HealthCheckProbe.Status.UNHEALTHY
            )
            .statusMessage(e.getValue().getMessage())
            .build();
    }
}

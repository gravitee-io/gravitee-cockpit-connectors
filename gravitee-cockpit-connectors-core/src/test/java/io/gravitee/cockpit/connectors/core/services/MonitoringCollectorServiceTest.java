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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.cockpit.api.CockpitConnector;
import io.gravitee.cockpit.api.command.Command;
import io.gravitee.cockpit.api.command.healthcheck.HealthCheckCommand;
import io.gravitee.cockpit.api.command.ignored.IgnoredReply;
import io.gravitee.cockpit.api.command.monitoring.MonitoringCommand;
import io.gravitee.cockpit.api.command.node.NodeCommand;
import io.gravitee.node.api.Monitoring;
import io.gravitee.node.api.healthcheck.HealthCheck;
import io.gravitee.node.api.healthcheck.Result;
import io.gravitee.node.api.infos.NodeInfos;
import io.gravitee.node.api.infos.NodeStatus;
import io.gravitee.node.api.infos.PluginInfos;
import io.gravitee.node.api.monitor.Monitor;
import io.gravitee.node.monitoring.NodeMonitoringService;
import io.reactivex.Flowable;
import io.reactivex.Single;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class MonitoringCollectorServiceTest {

    @Mock
    private NodeMonitoringService nodeMonitoringService;

    @Mock
    private CockpitConnector cockpitConnector;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ObjectMapper objectMapper;

    private MonitoringCollectorService cut;

    @Captor
    private ArgumentCaptor<Command> commandArgumentCaptor;

    @BeforeEach
    public void initMocks() {
        lenient().when(cockpitConnector.isPrimary()).thenReturn(true);

        cut = new MonitoringCollectorService(nodeMonitoringService, cockpitConnector, taskScheduler, objectMapper);
        cut.ready = true;
    }

    @Test
    public void collectAndSendNotReady() {
        cut.ready = false;
        cut.collectAndSend();
        verifyNoInteractions(objectMapper, nodeMonitoringService, cockpitConnector);
    }

    @Test
    public void collectAndSend_notPrimary() {
        when(cockpitConnector.isPrimary()).thenReturn(false);

        cut.collectAndSend();
        verifyNoInteractions(objectMapper, nodeMonitoringService);
    }

    @Test
    public void collectAndSend() throws JsonProcessingException {
        final NodeInfos nodeInfos = new NodeInfos();
        nodeInfos.setEvaluatedAt(System.currentTimeMillis());
        nodeInfos.setStatus(NodeStatus.STARTED);
        String nodeId = "node#1";
        nodeInfos.setId(nodeId);
        nodeInfos.setApplication("gio-apim-gateway");

        final PluginInfos pluginInfos = new PluginInfos();
        pluginInfos.setId("plugin#1");
        pluginInfos.setName("name");
        pluginInfos.setDescription("description");
        pluginInfos.setVersion("v1");
        pluginInfos.setType("cockpit");
        pluginInfos.setPlugin("plugin");

        nodeInfos.setPluginInfos(Collections.singleton(pluginInfos));
        nodeInfos.setTags(List.of("tag1", "tag2", ""));

        final HealthCheck healthCheck = new HealthCheck();
        final HashMap<String, Result> results = new HashMap<>();
        results.put("test", Result.healthy("OK"));
        healthCheck.setResults(results);

        final Monitor nodeMonitor = new Monitor.Builder().on(nodeId).at(System.currentTimeMillis()).build();

        when(objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)).thenReturn(objectMapper);
        when(objectMapper.readValue("nodeInfosPayload", NodeInfos.class)).thenReturn(nodeInfos);
        when(objectMapper.readValue("healthCheckPayload", HealthCheck.class)).thenReturn(healthCheck);
        when(objectMapper.readValue("monitoringPayload", Monitor.class)).thenReturn(nodeMonitor);
        when(cockpitConnector.sendCommand(any(Command.class))).thenReturn(Single.just(new IgnoredReply()));

        final Monitoring nodeInfosMonitoring = new Monitoring();
        nodeInfosMonitoring.setPayload("nodeInfosPayload");

        when(nodeMonitoringService.findByTypeAndTimeframe(eq(Monitoring.NODE_INFOS), anyLong(), anyLong()))
            .thenReturn(Flowable.just(nodeInfosMonitoring));

        final Monitoring healthCheckMonitoring = new Monitoring();
        healthCheckMonitoring.setPayload("healthCheckPayload");

        when(nodeMonitoringService.findByTypeAndTimeframe(eq(Monitoring.HEALTH_CHECK), anyLong(), anyLong()))
            .thenReturn(Flowable.just(healthCheckMonitoring));

        final Monitoring monitoringData = new Monitoring();
        monitoringData.setPayload("monitoringPayload");

        when(nodeMonitoringService.findByTypeAndTimeframe(eq(Monitoring.MONITOR), anyLong(), anyLong()))
            .thenReturn(Flowable.just(monitoringData));

        cut.collectAndSend();

        verify(cockpitConnector, times(3)).sendCommand(commandArgumentCaptor.capture());

        List<Command> commands = commandArgumentCaptor.getAllValues();

        assertThat(commands.stream().filter(cmd -> cmd.getType() == Command.Type.MONITORING_COMMAND).findAny().isPresent()).isTrue();
        assertThat(commands.stream().filter(cmd -> cmd.getType() == Command.Type.HEALTHCHECK_COMMAND).findAny().isPresent()).isTrue();

        Optional<Command> optionalNodeCommand = commands.stream().filter(cmd -> cmd.getType() == Command.Type.NODE_COMMAND).findAny();
        assertThat(optionalNodeCommand.isPresent()).isTrue();
        NodeCommand nodeCommand = (NodeCommand) optionalNodeCommand.get();
        assertThat(nodeCommand.getPayload().getShardingTags().size()).isEqualTo(2);
        assertThat(nodeCommand.getPayload().getShardingTags()).contains("tag1", "tag2");
    }
}

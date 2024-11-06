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

import static io.gravitee.cockpit.connectors.core.spring.MonitoringCollectorConfiguration.DEFAULT_CRON_TRIGGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.cockpit.api.command.v1.CockpitCommandType;
import io.gravitee.cockpit.api.command.v1.MetadataConstants;
import io.gravitee.cockpit.api.command.v1.node.NodeCommand;
import io.gravitee.exchange.api.command.Command;
import io.gravitee.exchange.api.connector.ExchangeConnector;
import io.gravitee.exchange.api.websocket.protocol.legacy.ignored.IgnoredReply;
import io.gravitee.node.api.Monitoring;
import io.gravitee.node.api.healthcheck.HealthCheck;
import io.gravitee.node.api.healthcheck.Result;
import io.gravitee.node.api.infos.NodeInfos;
import io.gravitee.node.api.infos.NodeStatus;
import io.gravitee.node.api.infos.PluginInfos;
import io.gravitee.node.monitoring.NodeMonitoringService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
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
    private ExchangeConnector cockpitConnector;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ObjectMapper objectMapper;

    private MonitoringCollectorService cut;

    @Captor
    private ArgumentCaptor<Command<?>> commandArgumentCaptor;

    @BeforeEach
    public void initMocks() {
        lenient().when(cockpitConnector.isActive()).thenReturn(true);
        lenient().when(cockpitConnector.isPrimary()).thenReturn(true);

        cut = new MonitoringCollectorService(nodeMonitoringService, taskScheduler, DEFAULT_CRON_TRIGGER, objectMapper);
        cut.start(cockpitConnector);
    }

    @AfterEach
    public void afterEach() {
        cut.stop();
    }

    @Test
    void collectAndSendNotReady() {
        lenient().when(cockpitConnector.isActive()).thenReturn(false);
        cut.collectAndSend();
        verifyNoInteractions(objectMapper, nodeMonitoringService);
        verify(cockpitConnector, never()).sendCommand(any());
    }

    @Test
    void collectAndSend_notPrimary() {
        when(cockpitConnector.isPrimary()).thenReturn(false);

        cut.collectAndSend();
        verifyNoInteractions(objectMapper, nodeMonitoringService);
    }

    @Test
    void collectAndSend() throws JsonProcessingException {
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
        nodeInfos.setMetadata(Map.of(MetadataConstants.GATEWAY_ID, "technicalId#1"));

        final HealthCheck healthCheck = new HealthCheck();
        final HashMap<String, Result> results = new HashMap<>();
        results.put("test", Result.healthy("OK"));
        healthCheck.setResults(results);

        when(objectMapper.readValue("nodeInfosPayload", NodeInfos.class)).thenReturn(nodeInfos);
        when(objectMapper.readValue("healthCheckPayload", HealthCheck.class)).thenReturn(healthCheck);
        when(cockpitConnector.sendCommand(any(Command.class))).thenReturn(Single.just(new IgnoredReply()));

        final Monitoring nodeInfosMonitoring = new Monitoring();
        nodeInfosMonitoring.setPayload("nodeInfosPayload");

        when(nodeMonitoringService.findByTypeAndTimeframe(eq(Monitoring.NODE_INFOS), anyLong(), anyLong()))
            .thenReturn(Flowable.just(nodeInfosMonitoring));

        final Monitoring healthCheckMonitoring = new Monitoring();
        healthCheckMonitoring.setPayload("healthCheckPayload");

        when(nodeMonitoringService.findByTypeAndTimeframe(eq(Monitoring.HEALTH_CHECK), anyLong(), anyLong()))
            .thenReturn(Flowable.just(healthCheckMonitoring));

        cut.collectAndSend();

        verify(cockpitConnector, times(2)).sendCommand(commandArgumentCaptor.capture());

        List<Command<?>> commands = commandArgumentCaptor.getAllValues();

        assertThat(commands.stream().anyMatch(cmd -> cmd.getType().equals(CockpitCommandType.NODE_HEALTHCHECK.name()))).isTrue();

        Optional<Command<?>> optionalNodeCommand = commands
            .stream()
            .filter(cmd -> cmd.getType().equals(CockpitCommandType.NODE.name()))
            .findAny();
        assertThat(optionalNodeCommand).isPresent();
        NodeCommand nodeCommand = (NodeCommand) optionalNodeCommand.get();
        assertThat(nodeCommand.getPayload().shardingTags()).hasSize(2);
        assertThat(nodeCommand.getPayload().shardingTags()).contains("tag1", "tag2");
        assertThat(nodeCommand.getPayload().metadata()).contains(Map.entry(MetadataConstants.GATEWAY_ID, "technicalId#1"));
    }
}

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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.cockpit.api.CockpitConnector;
import io.gravitee.cockpit.api.command.healthcheck.HealthCheckCommand;
import io.gravitee.cockpit.api.command.healthcheck.HealthCheckPayload;
import io.gravitee.cockpit.api.command.healthcheck.HealthCheckProbe;
import io.gravitee.cockpit.api.command.monitoring.*;
import io.gravitee.cockpit.api.command.node.NodeCommand;
import io.gravitee.cockpit.api.command.node.NodePayload;
import io.gravitee.cockpit.api.command.node.NodePlugin;
import io.gravitee.node.api.Monitoring;
import io.gravitee.node.api.healthcheck.HealthCheck;
import io.gravitee.node.api.infos.NodeInfos;
import io.gravitee.node.api.infos.PluginInfos;
import io.gravitee.node.monitoring.NodeMonitoringService;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

        if (!cockpitConnector.isPrimary()) {
            log.debug("Cockpit connector is not primary. Skip monitoring propagation.");
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

        // Then send monitoring data.
        nodeMonitoringService
            .findByTypeAndTimeframe(Monitoring.MONITOR, lastRefreshAt - lastDelay, nextLastRefreshAt)
            .map(this::convertToMonitoringCommand)
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
        nodePayload.setShardingTags(
            nodeInfos.getTags() == null
                ? List.of()
                : nodeInfos.getTags().stream().filter(tag -> !tag.isBlank()).collect(Collectors.toList())
        );
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
        healthcheckPayload.setHealthy(healthCheck.isHealthy());
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

    private MonitoringCommand convertToMonitoringCommand(Monitoring monitoring) throws JsonProcessingException {
        final MonitoringCommand monitoringCommand = new MonitoringCommand();
        final MonitoringPayload monitoringPayload = new MonitoringPayload();
        final io.gravitee.node.api.monitor.Monitor monitor = objectMapper
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(monitoring.getPayload(), io.gravitee.node.api.monitor.Monitor.class);

        monitoringPayload.setNodeId(monitoring.getNodeId());
        monitoringPayload.setEvaluatedAt(monitor.getTimestamp());
        monitoringPayload.setMonitor(convertToMonitor(monitor));
        monitoringCommand.setPayload(monitoringPayload);

        return monitoringCommand;
    }

    private Monitor convertToMonitor(io.gravitee.node.api.monitor.Monitor monitor) {
        Monitor cockpitMonitor = new Monitor();
        cockpitMonitor.setJvmInfo(convertJvmInfo(monitor.getJvm()));
        cockpitMonitor.setOsInfo(convertOsInfo(monitor.getOs()));
        cockpitMonitor.setProcessInfo(convertProcessInfo(monitor.getProcess()));
        return cockpitMonitor;
    }

    private JvmInfo convertJvmInfo(io.gravitee.node.api.monitor.JvmInfo jvm) {
        JvmInfo cockpitJvm = new JvmInfo();
        if (jvm != null) {
            cockpitJvm.timestamp = jvm.timestamp;
            cockpitJvm.uptime = jvm.uptime;
            cockpitJvm.mem = convertMem(jvm.mem);
            cockpitJvm.gc = Arrays.stream(jvm.gc.collectors).map(this::convertGCCollectors).collect(Collectors.toList());
            cockpitJvm.threads = convertThreads(jvm.threads);
        }
        return cockpitJvm;
    }

    private JvmInfo.Threads convertThreads(io.gravitee.node.api.monitor.JvmInfo.Threads threads) {
        JvmInfo.Threads cockpitThreads = new JvmInfo.Threads();
        if (threads != null) {
            cockpitThreads.count = threads.count;
            cockpitThreads.peakCount = threads.peakCount;
        }
        return cockpitThreads;
    }

    private JvmInfo.GarbageCollector convertGCCollectors(io.gravitee.node.api.monitor.JvmInfo.GarbageCollector gc) {
        JvmInfo.GarbageCollector cockpitGc = new JvmInfo.GarbageCollector();
        if (gc != null) {
            cockpitGc.collectionCount = gc.collectionCount;
            cockpitGc.collectionTime = gc.collectionTime;
            cockpitGc.name = gc.name;
        }
        return cockpitGc;
    }

    private JvmInfo.Mem convertMem(io.gravitee.node.api.monitor.JvmInfo.Mem mem) {
        JvmInfo.Mem cockpitMem = new JvmInfo.Mem();
        if (mem != null) {
            cockpitMem.heapCommitted = mem.heapCommitted;
            cockpitMem.heapMax = mem.heapMax;
            cockpitMem.heapUsed = mem.heapUsed;
            cockpitMem.nonHeapCommitted = mem.nonHeapCommitted;
            cockpitMem.nonHeapUsed = mem.nonHeapUsed;
            cockpitMem.pools = Arrays.stream(mem.pools).map(this::convertMemPools).toArray(JvmInfo.MemoryPool[]::new);
        }
        return cockpitMem;
    }

    private JvmInfo.MemoryPool convertMemPools(io.gravitee.node.api.monitor.JvmInfo.MemoryPool memoryPool) {
        return new JvmInfo.MemoryPool(memoryPool.name, memoryPool.used, memoryPool.max, memoryPool.peakUsed, memoryPool.peakMax);
    }

    private OsInfo convertOsInfo(io.gravitee.node.api.monitor.OsInfo os) {
        OsInfo cockpitOs = new OsInfo();
        if (os != null) {
            cockpitOs.timestamp = os.timestamp;
            cockpitOs.cpu = convertCpu(os.cpu);
            cockpitOs.mem = convertOsMem(os.mem);
            cockpitOs.swap = convertSwap(os.swap);
        }
        return cockpitOs;
    }

    private OsInfo.Swap convertSwap(io.gravitee.node.api.monitor.OsInfo.Swap swap) {
        OsInfo.Swap cockpitSwap = new OsInfo.Swap();
        if (swap != null) {
            cockpitSwap.free = swap.free;
            cockpitSwap.total = swap.total;
        }
        return cockpitSwap;
    }

    private OsInfo.Mem convertOsMem(io.gravitee.node.api.monitor.OsInfo.Mem mem) {
        OsInfo.Mem cockpitMem = new OsInfo.Mem();
        if (mem != null) {
            cockpitMem.free = mem.free;
            cockpitMem.total = mem.total;
        }
        return cockpitMem;
    }

    private OsInfo.Cpu convertCpu(io.gravitee.node.api.monitor.OsInfo.Cpu cpu) {
        OsInfo.Cpu cockpitCpu = new OsInfo.Cpu();
        if (cpu != null) {
            cockpitCpu.loadAverage = cpu.loadAverage;
            cockpitCpu.percent = cpu.percent;
        }
        return cockpitCpu;
    }

    private ProcessInfo convertProcessInfo(io.gravitee.node.api.monitor.ProcessInfo process) {
        ProcessInfo cockpitProcessInfo = new ProcessInfo();
        if (process != null) {
            cockpitProcessInfo.timestamp = process.timestamp;
            cockpitProcessInfo.cpu = convertProcessCpu(process.cpu);
            cockpitProcessInfo.mem = convertProcessMem(process.mem);
            cockpitProcessInfo.maxFileDescriptors = process.maxFileDescriptors;
            cockpitProcessInfo.openFileDescriptors = process.openFileDescriptors;
        }
        return cockpitProcessInfo;
    }

    private ProcessInfo.Mem convertProcessMem(io.gravitee.node.api.monitor.ProcessInfo.Mem mem) {
        ProcessInfo.Mem cockpitMem = new ProcessInfo.Mem();
        if (mem != null) {
            cockpitMem.totalVirtual = mem.totalVirtual;
        }
        return cockpitMem;
    }

    private ProcessInfo.Cpu convertProcessCpu(io.gravitee.node.api.monitor.ProcessInfo.Cpu cpu) {
        ProcessInfo.Cpu cockpitCpu = new ProcessInfo.Cpu();
        if (cpu != null) {
            cockpitCpu.percent = cpu.percent;
            cockpitCpu.total = cpu.total;
        }
        return cockpitCpu;
    }
}

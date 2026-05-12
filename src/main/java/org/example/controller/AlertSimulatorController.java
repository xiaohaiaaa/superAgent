package org.example.controller;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import org.example.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 告警模拟控制器
 * 提供各种告警场景的模拟能力，用于测试和演示
 */
@RestController
@RequestMapping("/api/alert/simulate")
@Tag(name = "告警模拟", description = "模拟各种告警场景：内存溢出、线程耗尽、CPU飙升、磁盘爆满等")
public class AlertSimulatorController {

    private static final Logger logger = LoggerFactory.getLogger(AlertSimulatorController.class);

    private final AtomicBoolean oomSimulationRunning = new AtomicBoolean(false);
    private final AtomicBoolean threadLeakRunning = new AtomicBoolean(false);
    private final AtomicBoolean cpuSpiKERunning = new AtomicBoolean(false);

    private final AtomicLong simulatedOomCount = new AtomicLong(0);
    private final AtomicLong simulatedThreadLeakCount = new AtomicLong(0);
    private final AtomicLong simulatedCpuSpikeCount = new AtomicLong(0);

    private final List<byte[]> oomHoldList = new ArrayList<>();
    private final List<Thread> leakedThreads = new ArrayList<>();
    private ExecutorService leakExecutor;

    private final MeterRegistry meterRegistry;

    public AlertSimulatorController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initMetrics() {
        // 注册自定义指标
        Gauge.builder("alert_simulator_oom_running", () -> oomSimulationRunning.get() ? 1.0 : 0.0)
                .description("Whether OOM simulation is running")
                .register(meterRegistry);

        Gauge.builder("alert_simulator_thread_leak_running", () -> threadLeakRunning.get() ? 1.0 : 0.0)
                .description("Whether thread leak simulation is running")
                .register(meterRegistry);

        Gauge.builder("alert_simulator_cpu_spike_running", () -> cpuSpiKERunning.get() ? 1.0 : 0.0)
                .description("Whether CPU spike simulation is running")
                .register(meterRegistry);

        Gauge.builder("alert_simulator_oom_count", simulatedOomCount, AtomicLong::get)
                .description("Total OOM simulation allocations")
                .register(meterRegistry);

        Gauge.builder("alert_simulator_thread_leak_count", simulatedThreadLeakCount, AtomicLong::get)
                .description("Total leaked threads created")
                .register(meterRegistry);

        Gauge.builder("alert_simulator_cpu_spike_count", simulatedCpuSpikeCount, AtomicLong::get)
                .description("Total CPU spike simulation restarts")
                .register(meterRegistry);

        Gauge.builder("alert_simulator_oom_hold_mb", () -> {
            synchronized (oomHoldList) {
                return (double) oomHoldList.size(); // 每块约 1MB
            }
        }).description("Current OOM hold list size in MB")
                .register(meterRegistry);

        Gauge.builder("alert_simulator_leaked_threads", () -> {
            synchronized (leakedThreads) {
                return (double) leakedThreads.size();
            }
        }).description("Current leaked thread count")
                .register(meterRegistry);
    }

    @Operation(summary = "模拟内存溢出", description = "持续创建大对象导致内存溢出 (OOM)")
    @GetMapping("/oom/start")
    public ResponseEntity<ApiResponse<String>> startOomSimulation(
            @RequestParam(defaultValue = "10") int allocateSizeMB,
            @RequestParam(defaultValue = "100") int delayMs) {

        if (oomSimulationRunning.compareAndSet(false, true)) {
            new Thread(() -> {
                logger.warn("=== 内存溢出模拟开始 ===");
                while (oomSimulationRunning.get()) {
                    try {
                        // 分配大内存块并持有
                        byte[][] holder = new byte[allocateSizeMB][];
                        for (int i = 0; i < allocateSizeMB; i++) {
                            holder[i] = new byte[1024 * 1024]; // 1MB
                        }
                        synchronized (oomHoldList) {
                            oomHoldList.add(holder[0]);
                        }
                        simulatedOomCount.incrementAndGet();
                        logger.warn("已分配 {} MB 内存，当前持有 {} 块", allocateSizeMB, oomHoldList.size());

                        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
                        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
                        long usedMB = heapUsage.getUsed() / (1024 * 1024);
                        long maxMB = heapUsage.getMax() / (1024 * 1024);
                        logger.warn("堆内存使用: {}/{} MB", usedMB, maxMB);

                        Thread.sleep(delayMs);
                    } catch (OutOfMemoryError e) {
                        logger.error("=== 触发 OutOfMemoryError ===", e);
                        oomSimulationRunning.set(false);
                        break;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                logger.warn("=== 内存溢出模拟结束 ===");
            }, "OOM-Simulator").start();

            return ResponseEntity.ok(ApiResponse.success("内存溢出模拟已启动，持续分配 " + allocateSizeMB + " MB 对象"));
        } else {
            return ResponseEntity.ok(ApiResponse.error("模拟已在运行中"));
        }
    }

    @Operation(summary = "停止内存溢出模拟", description = "停止持续的内存分配")
    @GetMapping("/oom/stop")
    public ResponseEntity<ApiResponse<String>> stopOomSimulation() {
        oomSimulationRunning.set(false);
        synchronized (oomHoldList) {
            oomHoldList.clear();
        }
        System.gc();
        return ResponseEntity.ok(ApiResponse.success("内存溢出模拟已停止，已触发 GC"));
    }

    @Operation(summary = "获取当前内存状态", description = "查看JVM堆内存使用情况")
    @GetMapping("/memory/status")
    public ResponseEntity<ApiResponse<MemoryStatus>> getMemoryStatus() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        MemoryStatus status = new MemoryStatus();
        status.setHeapUsed(heapUsage.getUsed());
        status.setHeapCommitted(heapUsage.getCommitted());
        status.setHeapMax(heapUsage.getMax());
        status.setNonHeapUsed(nonHeapUsage.getUsed());
        status.setNonHeapCommitted(nonHeapUsage.getCommitted());
        status.setOomSimulationRunning(oomSimulationRunning.get());
        status.setOomHoldCount(oomHoldList.size());

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @Operation(summary = "模拟线程耗尽", description = "不断创建新线程直到达到系统限制")
    @GetMapping("/thread-leak/start")
    public ResponseEntity<ApiResponse<String>> startThreadLeakSimulation(
            @RequestParam(defaultValue = "100") int intervalMs) {

        if (threadLeakRunning.compareAndSet(false, true)) {
            leakExecutor = Executors.newCachedThreadPool();

            new Thread(() -> {
                logger.warn("=== 线程耗尽模拟开始 ===");
                int count = 0;
                while (threadLeakRunning.get()) {
                    try {
                        count++;
                        // 创建会阻塞的线程，持续占用线程资源
                        Future<?> future = leakExecutor.submit(() -> {
                            try {
                                // 线程持续运行，不退出
                                Thread.sleep(Long.MAX_VALUE);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });

                        synchronized (leakedThreads) {
                            leakedThreads.add(Thread.currentThread());
                        }
                        simulatedThreadLeakCount.incrementAndGet();

                        Thread.sleep(intervalMs);

                        if (count % 100 == 0) {
                            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                            logger.warn("已创建 {} 个阻塞线程，当前活跃线程数: {}", count, threadMXBean.getThreadCount());
                        }
                    } catch (RejectedExecutionException e) {
                        logger.error("=== 线程创建失败，线程池已拒绝 ===");
                        threadLeakRunning.set(false);
                        break;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                logger.warn("=== 线程耗尽模拟结束 ===");
            }, "Thread-Leak-Simulator").start();

            return ResponseEntity.ok(ApiResponse.success("线程耗尽模拟已启动，每 " + intervalMs + " ms 创建一个阻塞线程"));
        } else {
            return ResponseEntity.ok(ApiResponse.error("模拟已在运行中"));
        }
    }

    @Operation(summary = "停止线程耗尽模拟", description = "停止创建新线程")
    @GetMapping("/thread-leak/stop")
    public ResponseEntity<ApiResponse<String>> stopThreadLeakSimulation() {
        threadLeakRunning.set(false);
        if (leakExecutor != null) {
            leakExecutor.shutdownNow();
            leakExecutor = null;
        }
        synchronized (leakedThreads) {
            leakedThreads.clear();
        }
        return ResponseEntity.ok(ApiResponse.success("线程耗尽模拟已停止"));
    }

    @Operation(summary = "获取当前线程状态", description = "查看JVM线程使用情况")
    @GetMapping("/thread/status")
    public ResponseEntity<ApiResponse<ThreadStatus>> getThreadStatus() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        ThreadStatus status = new ThreadStatus();
        status.setThreadCount(threadMXBean.getThreadCount());
        status.setPeakThreadCount(threadMXBean.getPeakThreadCount());
        status.setDaemonThreadCount(threadMXBean.getDaemonThreadCount());
        status.setLeakedThreadCount(leakedThreads.size());
        status.setThreadLeakSimulationRunning(threadLeakRunning.get());

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @Operation(summary = "模拟CPU飙升", description = "创建计算密集型任务导致CPU使用率飙升")
    @GetMapping("/cpu-spike/start")
    public ResponseEntity<ApiResponse<String>> startCpuSpikeSimulation(
            @RequestParam(defaultValue = "4") int threadCount) {

        if (cpuSpiKERunning.compareAndSet(false, true)) {
            new Thread(() -> {
                logger.warn("=== CPU飙升模拟开始 ===");
                ExecutorService cpuExecutor = Executors.newFixedThreadPool(threadCount);

                for (int i = 0; i < threadCount; i++) {
                    cpuExecutor.submit(() -> {
                        // 纯计算任务，消耗CPU
                        long start = System.nanoTime();
                        while (cpuSpiKERunning.get()) {
                            //做一些计算
                            double d = Math.random() * Math.random();
                            d = Math.sqrt(d) * Math.log(d + 1);
                            // 避免过度紧凑，添加微小延迟
                            if (System.nanoTime() - start > 1_000_000_000L) {
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                                start = System.nanoTime();
                            }
                        }
                    });
                }

                logger.warn("CPU飙升模拟已启动，创建了 {} 个计算线程", threadCount);
                simulatedCpuSpikeCount.incrementAndGet();
            }, "CPU-Spike-Simulator").start();

            return ResponseEntity.ok(ApiResponse.success("CPU飙升模拟已启动，创建 " + threadCount + " 个计算密集线程"));
        } else {
            return ResponseEntity.ok(ApiResponse.error("模拟已在运行中"));
        }
    }

    @Operation(summary = "停止CPU飙升模拟", description = "停止CPU密集计算")
    @GetMapping("/cpu-spike/stop")
    public ResponseEntity<ApiResponse<String>> stopCpuSpikeSimulation() {
        cpuSpiKERunning.set(false);
        return ResponseEntity.ok(ApiResponse.success("CPU飙升模拟已停止"));
    }

    @Operation(summary = "获取CPU状态", description = "查看当前CPU使用情况")
    @GetMapping("/cpu/status")
    public ResponseEntity<ApiResponse<CpuStatus>> getCpuStatus() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        CpuStatus status = new CpuStatus();
        status.setAvailableProcessors(availableProcessors);
        status.setThreadCount(threadMXBean.getThreadCount());
        status.setCpuSpikeSimulationRunning(cpuSpiKERunning.get());

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @Operation(summary = "获取所有模拟状态", description = "查看所有告警模拟的运行状态")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SimulationStatus>> getAllStatus() {
        SimulationStatus status = new SimulationStatus();
        status.setOomRunning(oomSimulationRunning.get());
        status.setThreadLeakRunning(threadLeakRunning.get());
        status.setCpuSpikeRunning(cpuSpiKERunning.get());

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();

        status.setThreadCount(threadMXBean.getThreadCount());
        status.setHeapUsedMB(heapUsage.getUsed() / (1024 * 1024));
        status.setHeapMaxMB(heapUsage.getMax() / (1024 * 1024));

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    // ==================== 内部类定义 ====================

    public static class MemoryStatus {
        private long heapUsed;
        private long heapCommitted;
        private long heapMax;
        private long nonHeapUsed;
        private long nonHeapCommitted;
        private boolean oomSimulationRunning;
        private int oomHoldCount;

        // getters and setters
        public long getHeapUsed() { return heapUsed; }
        public void setHeapUsed(long heapUsed) { this.heapUsed = heapUsed; }
        public long getHeapCommitted() { return heapCommitted; }
        public void setHeapCommitted(long heapCommitted) { this.heapCommitted = heapCommitted; }
        public long getHeapMax() { return heapMax; }
        public void setHeapMax(long heapMax) { this.heapMax = heapMax; }
        public long getNonHeapUsed() { return nonHeapUsed; }
        public void setNonHeapUsed(long nonHeapUsed) { this.nonHeapUsed = nonHeapUsed; }
        public long getNonHeapCommitted() { return nonHeapCommitted; }
        public void setNonHeapCommitted(long nonHeapCommitted) { this.nonHeapCommitted = nonHeapCommitted; }
        public boolean isOomSimulationRunning() { return oomSimulationRunning; }
        public void setOomSimulationRunning(boolean oomSimulationRunning) { this.oomSimulationRunning = oomSimulationRunning; }
        public int getOomHoldCount() { return oomHoldCount; }
        public void setOomHoldCount(int oomHoldCount) { this.oomHoldCount = oomHoldCount; }
    }

    public static class ThreadStatus {
        private int threadCount;
        private int peakThreadCount;
        private int daemonThreadCount;
        private int leakedThreadCount;
        private boolean threadLeakSimulationRunning;

        // getters and setters
        public int getThreadCount() { return threadCount; }
        public void setThreadCount(int threadCount) { this.threadCount = threadCount; }
        public int getPeakThreadCount() { return peakThreadCount; }
        public void setPeakThreadCount(int peakThreadCount) { this.peakThreadCount = peakThreadCount; }
        public int getDaemonThreadCount() { return daemonThreadCount; }
        public void setDaemonThreadCount(int daemonThreadCount) { this.daemonThreadCount = daemonThreadCount; }
        public int getLeakedThreadCount() { return leakedThreadCount; }
        public void setLeakedThreadCount(int leakedThreadCount) { this.leakedThreadCount = leakedThreadCount; }
        public boolean isThreadLeakSimulationRunning() { return threadLeakSimulationRunning; }
        public void setThreadLeakSimulationRunning(boolean threadLeakSimulationRunning) { this.threadLeakSimulationRunning = threadLeakSimulationRunning; }
    }

    public static class CpuStatus {
        private int availableProcessors;
        private int threadCount;
        private boolean cpuSpikeSimulationRunning;

        // getters and setters
        public int getAvailableProcessors() { return availableProcessors; }
        public void setAvailableProcessors(int availableProcessors) { this.availableProcessors = availableProcessors; }
        public int getThreadCount() { return threadCount; }
        public void setThreadCount(int threadCount) { this.threadCount = threadCount; }
        public boolean isCpuSpikeSimulationRunning() { return cpuSpikeSimulationRunning; }
        public void setCpuSpikeSimulationRunning(boolean cpuSpikeSimulationRunning) { this.cpuSpikeSimulationRunning = cpuSpikeSimulationRunning; }
    }

    public static class SimulationStatus {
        private boolean oomRunning;
        private boolean threadLeakRunning;
        private boolean cpuSpikeRunning;
        private int threadCount;
        private long heapUsedMB;
        private long heapMaxMB;

        // getters and setters
        public boolean isOomRunning() { return oomRunning; }
        public void setOomRunning(boolean oomRunning) { this.oomRunning = oomRunning; }
        public boolean isThreadLeakRunning() { return threadLeakRunning; }
        public void setThreadLeakRunning(boolean threadLeakRunning) { this.threadLeakRunning = threadLeakRunning; }
        public boolean isCpuSpikeRunning() { return cpuSpikeRunning; }
        public void setCpuSpikeRunning(boolean cpuSpikeRunning) { this.cpuSpikeRunning = cpuSpikeRunning; }
        public int getThreadCount() { return threadCount; }
        public void setThreadCount(int threadCount) { this.threadCount = threadCount; }
        public long getHeapUsedMB() { return heapUsedMB; }
        public void setHeapUsedMB(long heapUsedMB) { this.heapUsedMB = heapUsedMB; }
        public long getHeapMaxMB() { return heapMaxMB; }
        public void setHeapMaxMB(long heapMaxMB) { this.heapMaxMB = heapMaxMB; }
    }
}

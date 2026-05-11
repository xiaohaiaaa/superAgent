/**
 * Mock Metrics Server for Prometheus
 * 模拟业务服务暴露 Prometheus 指标，用于测试告警功能
 *
 * 运行方式: node mockMetricsServer.js
 * 暴露端点:
 *   - GET /metrics  Prometheus 格式指标
 *   - GET /health  健康检查
 */

const http = require('http');
const os = require('os');

// 配置：模拟的服务列表
const SERVICES = [
  { name: 'payment-service', instance: 'pod-payment-service-7d8f9c6b5-x2k4m' },
  { name: 'order-service', instance: 'pod-order-service-5c7d8e9f1-m3n2p' },
  { name: 'user-service', instance: 'pod-user-service-8e9f0a1b2-k5j6h' }
];

// 全局指标状态（用于模拟动态变化）
let cpuUsage = 75;
let memoryUsage = 70;
let requestLatency = 200;
let errorRate = 0.5;

// 定时更新指标，模拟动态变化
setInterval(() => {
  // CPU: 模拟波动，70-95%
  cpuUsage = 70 + Math.random() * 25;
  if (cpuUsage > 90) cpuUsage = 92; // 偶尔触发告警阈值

  // Memory: 模拟缓慢增长
  memoryUsage = Math.min(95, memoryUsage + Math.random() * 2);

  // Latency: P99响应时间 ms
  requestLatency = 150 + Math.random() * 600;

  // Error rate: 百分比
  errorRate = Math.random() * 3;
}, 15000); // 每15秒更新一次

// 生成 Prometheus 格式指标
function generateMetrics() {
  const timestamp = Date.now() / 1000;
  let output = '';

  for (const svc of SERVICES) {
    const labels = `service="${svc.name}",instance="${svc.instance}",job="mock-app"`;

    // CPU 使用率 (0-100)
    output += `# TYPE cpu_usage gauge\n`;
    output += `cpu_usage{${labels}} ${cpuUsage.toFixed(2)}\n`;

    // 内存使用率 (0-100)
    output += `# TYPE memory_usage gauge\n`;
    output += `memory_usage{${labels}} ${memoryUsage.toFixed(2)}\n`;

    // JVM 堆内存使用 (GB)
    const jvmHeapUsed = (3.0 + Math.random() * 0.8).toFixed(2);
    const jvmHeapMax = 4.0;
    output += `# TYPE jvm_heap_used gauge\n`;
    output += `jvm_heap_used{${labels}} ${jvmHeapUsed}\n`;
    output += `# TYPE jvm_heap_max gauge\n`;
    output += `jvm_heap_max{${labels}} ${jvmHeapMax}\n`;

    // HTTP 请求延迟 P99 (ms)
    output += `# TYPE http_request_duration_ms gauge\n`;
    output += `http_request_duration_ms{${labels}} ${requestLatency.toFixed(0)}\n`;

    // HTTP 请求错误率 (%)
    output += `# TYPE http_requests_errors_total counter\n`;
    output += `http_requests_errors_total{${labels}} ${errorRate.toFixed(2)}\n`;

    // 请求总数
    output += `# TYPE http_requests_total counter\n`;
    output += `http_requests_total{${labels}} ${10000 + Math.floor(Math.random() * 1000)}\n`;

    // GC 次数
    output += `# TYPE gc_count_total counter\n`;
    output += `gc_count_total{${labels}} ${50 + Math.floor(Math.random() * 20)}\n`;
  }

  // 模拟告警规则相关指标（用于触发 Prometheus 告警）
  // 当这些指标超过阈值时，Prometheus 告警规则会触发

  // 示例告警规则：
  // - alert: HighCPUUsage
  //   expr: cpu_usage > 80
  //   for: 1m

  // 为了让告警更容易触发，我们设置 CPU 偶尔高于 80
  if (cpuUsage > 85) {
    output += `# TYPE alert_cpu_over80 gauge\n`;
    output += `alert_cpu_over80{service="payment-service"} 1\n`;
  }

  return output;
}

// HTTP 服务器
const server = http.createServer((req, res) => {
  if (req.url === '/metrics') {
    res.setHeader('Content-Type', 'text/plain; version=0.0.4');
    res.end(generateMetrics());
  } else if (req.url === '/health') {
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ status: 'ok' }));
  } else if (req.url === '/') {
    res.setHeader('Content-Type', 'text/html');
    res.end(`
      <h1>Mock Metrics Server</h1>
      <p>Services: ${SERVICES.map(s => s.name).join(', ')}</p>
      <p><a href="/metrics">/metrics</a> - Prometheus metrics</p>
      <p><a href="/health">/health</a> - Health check</p>
      <h2>Current Values</h2>
      <ul>
        <li>CPU Usage: ${cpuUsage.toFixed(2)}%</li>
        <li>Memory Usage: ${memoryUsage.toFixed(2)}%</li>
        <li>Request Latency: ${requestLatency.toFixed(0)}ms</li>
        <li>Error Rate: ${errorRate.toFixed(2)}%</li>
      </ul>
      <p>Metrics update every 15 seconds to simulate dynamic changes.</p>
    `);
  } else {
    res.statusCode = 404;
    res.end('Not Found');
  }
});

const PORT = 9100;
server.listen(PORT, () => {
  console.log(`
╔══════════════════════════════════════════════════════════════╗
║           Mock Metrics Server Started                        ║
╠══════════════════════════════════════════════════════════════╣
║  Endpoint: http://localhost:${PORT}/metrics                   ║
║  Health:  http://localhost:${PORT}/health                    ║
╠══════════════════════════════════════════════════════════════╣
║  Services:                                                   ║
${SERVICES.map(s => `║    - ${s.name} (${s.instance})`).join('\n')}
╠══════════════════════════════════════════════════════════════╣
║  Simulated Metrics:                                          ║
║    CPU Usage: ~${cpuUsage.toFixed(0)}% (triggers >80% alert)            ║
║    Memory: ~${memoryUsage.toFixed(0)}% (triggers >85% alert)             ║
║    Latency: ~${requestLatency.toFixed(0)}ms (triggers >3000ms alert)      ║
║    Errors: ~${errorRate.toFixed(1)}%                                             ║
╚══════════════════════════════════════════════════════════════╝

Next step: Configure Prometheus to scrape this endpoint
  and add alerting rules for HighCPUUsage, HighMemoryUsage, etc.
`);
});

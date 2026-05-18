---
description: Create and structure Grafana dashboards for monitoring services. Use when building observability dashboards or visualizing Prometheus metrics.
---

# Grafana Dashboards

$ARGUMENTS

## Dashboard Structure (Top to Bottom)

```
┌─────────────────────────────────┐
│  Critical Metrics (Stat panels) │  ← Error rate, P95 latency, RPS
├─────────────────────────────────┤
│  Key Trends (Time Series)       │  ← Request rate, error rate over time
├─────────────────────────────────┤
│  Detailed Metrics (Tables)      │  ← Per-service breakdown
└─────────────────────────────────┘
```

## RED Method (Services)
- **Rate** — requests per second
- **Errors** — error rate
- **Duration** — latency (P50, P95, P99)

## Key Prometheus Queries

```promql
# Request rate
sum(rate(http_requests_total[5m])) by (service)

# Error rate %
(sum(rate(http_requests_total{status=~"5.."}[5m]))
 / sum(rate(http_requests_total[5m]))) * 100

# P95 latency
histogram_quantile(0.95,
  sum(rate(http_request_duration_seconds_bucket[5m])) by (le, service))

# CPU usage
100 - (avg by(instance)(rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)
```

## Alert Thresholds

```json
{
  "alert": {
    "name": "High Error Rate",
    "conditions": [{
      "evaluator": { "params": [5], "type": "gt" },
      "query": { "params": ["A", "5m", "now"] },
      "reducer": { "type": "avg" }
    }],
    "for": "5m",
    "message": "Error rate above 5%"
  }
}
```

## Dashboard Variables

```json
{
  "templating": {
    "list": [{
      "name": "service",
      "type": "query",
      "datasource": "Prometheus",
      "query": "label_values(http_requests_total, service)",
      "refresh": 1,
      "multi": true
    }]
  }
}
```

Use in queries: `http_requests_total{service=~"$service"}`

## Provisioning (GitOps)

```yaml
# provisioning/dashboards.yml
apiVersion: 1
providers:
  - name: default
    type: file
    options:
      path: /etc/grafana/dashboards
    allowUiUpdates: true
```

```hcl
# Terraform
resource "grafana_dashboard" "api" {
  config_json = file("${path.module}/dashboards/api.json")
}
```

## Best Practices

- Default time range: Last 6 hours
- Use variables for namespace/service filtering
- Set units correctly (seconds, percent, bytes)
- Add panel descriptions for context
- Consistent colors across dashboards
- Alert on P95 latency + error rate, not just averages

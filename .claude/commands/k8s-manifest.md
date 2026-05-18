---
description: Generate production-ready Kubernetes manifests — Deployments, Services, ConfigMaps, Secrets, PVCs with security best practices.
---

# Kubernetes Manifest Generator

$ARGUMENTS

## Deployment (Standard Pattern)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: <app-name>
  namespace: <namespace>
  labels:
    app.kubernetes.io/name: <app-name>
    app.kubernetes.io/version: "1.0.0"
spec:
  replicas: 3
  selector:
    matchLabels:
      app: <app-name>
  template:
    metadata:
      labels:
        app: <app-name>
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
        - name: <app-name>
          image: <image>:<tag>   # never :latest
          ports:
            - containerPort: 8080
              name: http
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: http
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: http
            initialDelaySeconds: 5
            periodSeconds: 5
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: [ALL]
          envFrom:
            - configMapRef:
                name: <app-name>-config
            - secretRef:
                name: <app-name>-secret
```

## Service

```yaml
# ClusterIP (internal)
apiVersion: v1
kind: Service
metadata:
  name: <app-name>
spec:
  type: ClusterIP
  selector:
    app: <app-name>
  ports:
    - port: 80
      targetPort: 8080
```

## ConfigMap + Secret

```yaml
# ConfigMap — non-sensitive config
apiVersion: v1
kind: ConfigMap
metadata:
  name: <app-name>-config
data:
  SPRING_PROFILES_ACTIVE: production
  LOG_LEVEL: info

---
# Secret — sensitive data (use Sealed Secrets or Vault in production)
apiVersion: v1
kind: Secret
metadata:
  name: <app-name>-secret
type: Opaque
stringData:
  DATABASE_PASSWORD: "changeme"
  JWT_SECRET: "changeme"
```

## Security Checklist

- [ ] Run as non-root user
- [ ] Drop all capabilities
- [ ] Read-only root filesystem
- [ ] Disable privilege escalation
- [ ] Resource requests AND limits set
- [ ] Liveness + readiness probes configured
- [ ] Specific image tag (never `:latest`)
- [ ] Secrets not committed to Git in plaintext

## Validation

```bash
kubectl apply -f manifest.yaml --dry-run=server
kube-score score manifest.yaml
kube-linter lint manifest.yaml
```

## Patterns

| Use Case | Resource |
|----------|----------|
| Stateless web app | Deployment + ClusterIP |
| Database | StatefulSet + Headless Service + PVC |
| Scheduled job | CronJob |
| External access | LoadBalancer or Ingress |

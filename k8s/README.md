# SwiftPay on Kubernetes (Minikube)

Alternative to `docker-compose.yml` — the spec's mandatory stack allows either ("Docker &
Kubernetes (Local or Minikube)" / "docker-compose.yml (or K8s manifests)").

**Honest status: written but not run.** This sandboxed environment has no Minikube or kubectl to
test against, so unlike the rest of this repo, these manifests have not been applied to a real
cluster. Treat the first `kubectl apply` as the actual verification step.

## What's here

| File | What it creates |
|---|---|
| `00-namespace.yaml` | the `swiftpay` namespace everything else lives in |
| `01-secret.yaml` | Postgres credentials (plaintext in git — same limitation as `docker-compose.yml`'s inline credentials; a real deployment should use `kubectl create secret` instead) |
| `02-postgres-init-configmap.yaml` | generated from `db/init.sql` — regenerate this file if you edit the schema, not the other way around |
| `10-postgres.yaml` | Postgres as a Deployment + PVC (a StatefulSet would be the production-grade choice; this matches the project's dev/demo scope) |
| `11-redis.yaml` | Redis, no persistence (idempotency cache only, same as compose) |
| `12-kafka.yaml` | single-node Kafka in KRaft mode — simpler listener config than `docker-compose.yml` needed, since in-cluster DNS means no internal/external listener split is necessary |
| `20-gateway-service.yaml`, `21-ledger-service.yaml`, `22-analytics-worker.yaml` | the three app services, each with init-containers that wait for their dependencies (Postgres/Redis/Kafka) to accept TCP connections before the main container starts |

## Prerequisites

- [Minikube](https://minikube.sigs.k8s.io/docs/start/) and `kubectl` installed
- Docker (already true if you've been running `docker-compose.yml`)

## Step 1 — Start Minikube

```powershell
minikube start
```

## Step 2 — Build the images INTO Minikube's own Docker daemon

Minikube runs its own separate Docker daemon — images built with your regular `docker build` won't
be visible to it. Point your shell at Minikube's daemon first:

```powershell
& minikube docker-env | Invoke-Expression
```

Then build exactly the same way as for docker-compose (same Dockerfiles, no changes needed):

```powershell
docker compose build
```

**Every new PowerShell window needs Step 2 re-run** — `minikube docker-env` only affects the
current shell session.

## Step 3 — Apply the manifests

```powershell
kubectl apply -f k8s/
```

`kubectl` applies files in a directory in alphabetical order, which is why they're numbered
(00, 01, 02, 10, 11, 12, 20, 21, 22) — namespace and config first, then infra, then app services.

## Step 4 — Watch it come up

```powershell
kubectl get pods -n swiftpay -w
```

Expect the app service pods to sit in `Init:0/2` or `Init:0/3` briefly while their init-containers
wait for Postgres/Redis/Kafka, then move to `Running` once dependencies are reachable. Ctrl+C once
everything shows `Running` and `1/1 READY`.

If a pod gets stuck, check its logs:
```powershell
kubectl logs -n swiftpay deployment/gateway-service
kubectl logs -n swiftpay deployment/gateway-service -c wait-for-postgres  # init container logs
```

## Step 5 — Access the services from your machine

Minikube's pods aren't directly reachable at `localhost` the way `docker-compose` ports are —
use `kubectl port-forward` per service, one per terminal window:

```powershell
kubectl port-forward -n swiftpay svc/gateway-service 8081:8081
kubectl port-forward -n swiftpay svc/ledger-service 8082:8082
kubectl port-forward -n swiftpay svc/analytics-worker 8083:8083
```

Once forwarded, everything works exactly as documented in the root README — same curl commands,
same Swagger UI paths, same endpoints.

## Known gaps / not verified

- Never applied to a real cluster — every command above is the intended workflow, not a confirmed
  one. `nc` availability and flag support in `busybox:1.36` for the init-container TCP-wait pattern
  is a very common, well-documented idiom but hasn't been exercised here specifically.
- No `HorizontalPodAutoscaler`, `NetworkPolicy`, resource requests/limits, or `Ingress` — this is
  a local/Minikube dev setup matching the spec's own framing ("Local or Minikube"), not a
  production-hardened deployment.
- Postgres as a Deployment+PVC rather than a StatefulSet is a deliberate simplification for this
  scope, not an oversight — flagged in the table above.

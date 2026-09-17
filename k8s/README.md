# SwiftPay on Kubernetes (Minikube)

Alternative to `docker-compose.yml` — the spec's mandatory stack allows either ("Docker &
Kubernetes (Local or Minikube)" / "docker-compose.yml (or K8s manifests)").

**Honest status: applied to a real Minikube cluster, two real bugs found and fixed, but full
end-to-end stability was never achieved** on the machine this was tested on — see the
Verification log below for the specifics of what was fixed and what remains genuinely unresolved.
`docker-compose.yml` is the fully verified path; treat this as a partially-tested alternative.

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

## Verification log

- **Applied to a real cluster** — Minikube (`docker` driver, since Docker Desktop's own built-in
  Kubernetes failed to initialize on this machine with a generic `kubeadm init` error across two
  different provisioning modes; Minikube's driver sidesteps that entirely).
- **Real bug found and fixed**: Kafka crash-looped with `Unable to register with the controller
  quorum` — `KAFKA_CONTROLLER_QUORUM_VOTERS` pointed the single-node broker at itself via the
  `kafka` Service name, and Kubernetes Service self-connection ("hairpin NAT") is unreliable on
  several CNI implementations. Fixed by using `localhost:9093` for self-referential
  controller-quorum communication instead (see the comment in `12-kafka.yaml`). Confirmed via
  `kubectl logs --previous`: after the fix, Kafka's KRaft controller registered successfully and
  the broker fully started ("Kafka Server started") — a genuine improvement over the pre-fix state,
  even though a separate issue (below) prevented full end-to-end stability.
- **Real bug found and fixed**: `ErrImageNeverPull` on all three app services — the manifests
  referenced `swiftpay/gateway-service:latest` (with a slash), but `docker compose build` actually
  produces `swiftpay-gateway-service:latest` (Compose's default `<project>-<service>` naming, with
  a hyphen). Fixed across all three app manifests to match the actual build output.
- **Unresolved: broad, unexplained pod instability on this specific machine.** Even after both
  fixes above, a full machine reboot, a completely fresh namespace, freshly rebuilt images, and
  disabling Docker Desktop's Resource Saver, Postgres, Redis, Kafka, and all three app
  services — including the official, battle-tested Postgres and Redis images with trivial health
  checks — intermittently restart and cycle through `CrashLoopBackOff`. Memory (`minikube ssh --
  free -h`: 5.3GB available) and CPU (`nproc`: 12 cores, `/proc/loadavg`: ~3-4, well under
  capacity) were both directly measured and ruled out as the cause. This looks like an
  environmental issue specific to this machine's Minikube/Docker Desktop/WSL2 installation, not a
  bug in the SwiftPay manifests — but it was not resolved before development time on this
  genuinely optional verification path (docker-compose already satisfies the spec's requirement)
  was called complete.
- **What IS confirmed working from these tests**: the manifest set's logic is sound enough to get
  every component to `Running` at least once each (postgres, redis, kafka, all three app services
  all reached `1/1 Running` at various points), and both real bugs found were genuine, fixable
  issues in the YAML rather than something structurally wrong with the design.

## Known gaps
- No `HorizontalPodAutoscaler`, `NetworkPolicy`, resource requests/limits, or `Ingress` — this is
  a local/Minikube dev setup matching the spec's own framing ("Local or Minikube"), not a
  production-hardened deployment.
- Postgres as a Deployment+PVC rather than a StatefulSet is a deliberate simplification for this
  scope, not an oversight — flagged in the table above.

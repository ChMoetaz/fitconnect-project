# FitConnect — DevOps

This document describes how FitConnect is **deployed and operated**. The application itself is documented in [README.md](README.md); the full formal concept is in [concept.md](concept.md).

The application runs on a **single-node k3s (Kubernetes) cluster on an Azure VM**, in two isolated environments, exposed over HTTPS, and deployed automatically by a CI/CD pipeline.

---

## Architecture

Five layers, bottom to top:

```
5. Automation      GitHub Actions (CI/CD)  +  Python IaC script
4. Access          DuckDNS (DNS) + Traefik (ingress) + cert-manager (HTTPS)
3. Application      backend / frontend / PostgreSQL, packaged with Helm
2. Cluster          k3s (Kubernetes)
1. Infrastructure   Azure VM  +  network / firewall
```

```text
Developer
   │  push (dev)  /  merge PR (main)
   ▼
GitHub  ──►  GitHub Actions runner
                │  test → build images → push to GHCR → ssh + helm upgrade
                ▼
        GitHub Container Registry (GHCR)
                │  k3s pulls images
                ▼
   ┌──────────── Azure VM · k3s (single node) ─────────────┐
   │  Traefik ingress  +  cert-manager (Let's Encrypt/TLS)  │
   │                                                        │
   │  ns fitconnect-dev            ns fitconnect-prod       │
   │   ├─ backend  (1x)             ├─ backend  (2x)        │
   │   ├─ frontend (1x)             ├─ frontend (2x)        │
   │   └─ postgres (PVC)            └─ postgres (PVC)        │
   │                                                        │
   │  ns monitoring:  Prometheus  +  Grafana                │
   └────────────────────────────────────────────────────────┘
```

The pipeline runs on a **GitHub-hosted runner** and deploys to the VM over **SSH + Helm** — the pipeline execution is separate from the application runtime.

---

## Environments

Two isolated environments as Kubernetes namespaces, each with its own database and its own FQDNs.

| | dev | prod |
|---|---|---|
| **Namespace** | `fitconnect-dev` | `fitconnect-prod` |
| **Frontend** | `dev.fitconnect-moetaz.duckdns.org` | `fitconnect-moetaz.duckdns.org` |
| **API** | `api-dev.fitconnect-moetaz.duckdns.org` | `api.fitconnect-moetaz.duckdns.org` |
| **Database** | own PostgreSQL (own PVC + credentials) | own PostgreSQL (own PVC + credentials) |
| **Replicas (back/front)** | 1 / 1 | 2 / 2 |
| **Deploy trigger** | push to `dev` (automatic) | merge to `main` + 2 approvals |

---

## CI/CD

The pipeline ([.github/workflows/deploy.yml](.github/workflows/deploy.yml)) runs on every push and selects the target environment from the branch:

```
push → dev    :  test → build → deploy to fitconnect-dev   (automatic)
merge → main  :  test → build → deploy to fitconnect-prod  (2 reviewers must approve)
```

Stages:
1. **test** — backend `mvn test` (H2) + frontend `npm ci` && build.
2. **build** — backend and frontend images built and pushed to **GHCR**, tagged with the commit SHA. The frontend is built per environment (its API URL is baked in at build time).
3. **deploy** — the chart is copied to the VM and applied with `helm upgrade`. On `main`, the deploy job runs in the GitHub `production` **Environment** with **2 required reviewers** (double sign-off), so it pauses for approval.

A change therefore propagates through the environments by moving through the branches: land on `dev` (deploys dev) → merge to `main` (deploys prod after approval).

---

## Kubernetes objects (Helm chart)

The chart `k8s/fitconnect/` renders:

| Object | Used for |
|---|---|
| **Deployment** | backend, frontend (stateless, replicas, rolling update) |
| **StatefulSet** | PostgreSQL (stateful, with a persistent volume) |
| **Service** | stable internal address for each component |
| **ConfigMap** | non-secret backend config (datasource URL, JVM options) |
| **Secret** | passwords, JWT secret, API keys |
| **PersistentVolumeClaim** | PostgreSQL data (k3s `local-path` storage) |
| **Ingress** | external access, routed by hostname (Traefik) |
| **ClusterIssuer / Certificate** *(cert-manager)* | HTTPS certificates (Let's Encrypt) |

### Availability & zero-downtime
Production runs 2 replicas with a rolling update (`maxUnavailable: 0`, `maxSurge: 1`) and readiness/liveness probes (TCP on the backend — no Spring Actuator; HTTP `/` on the frontend). A new pod must be ready before an old one is removed → zero-downtime deploys.
**Limitation:** single node → pod-level HA only (not node-level).

---

## Infrastructure as Code

Two idempotent steps:

| File | Runs on | Creates / does |
|---|---|---|
| [infra/provision.py](infra/provision.py) | your machine (Azure SDK) | resource group, vnet/subnet, static public IP, NSG (22/80/443), NIC, the VM |
| [infra/bootstrap.sh](infra/bootstrap.sh) | the VM | installs k3s + Helm + cert-manager, applies the TLS issuer |

Every Azure call is a `create_or_update` (upsert) and the shell script checks before installing, so both are safe to re-run.

### Deploy from scratch
```bash
# 1. Provision the Azure VM (from your machine)
pip install -r infra/requirements.txt && az login
python infra/provision.py

# 2. Install the cluster software (on the VM)
bash infra/bootstrap.sh

# 3. Deploy (CI/CD does this automatically; manual example for prod):
helm upgrade --install fitconnect-prod ./k8s/fitconnect \
  -n fitconnect-prod --create-namespace \
  -f k8s/values-prod.yaml -f k8s/values-secret-prod.yaml
```

---

## TLS / DNS

- **DuckDNS** provides the domain `fitconnect-moetaz.duckdns.org` (and its subdomains) pointing at the VM's public IP.
- **cert-manager + Let's Encrypt** (HTTP-01 challenge) issue and renew browser-trusted certificates automatically.
- An earlier iteration used `nip.io` with a self-signed CA; the project moved to DuckDNS + Let's Encrypt because Let's Encrypt cannot reliably issue for the shared `nip.io` domain.

---

## Monitoring

Prometheus + Grafana run in the `monitoring` namespace (via the `kube-prometheus-stack` chart, config in [k8s/monitoring/](k8s/monitoring/)), exposed at `https://grafana.fitconnect-moetaz.duckdns.org`.

- **Prometheus** collects (scrapes) the metrics; **Grafana** visualizes them.
- A dedicated dashboard, *"FitConnect — dev & prod"*, shows pod health, CPU/memory per pod, restarts and replica status for both environments.
- Logging: the app writes to stdout/stderr, inspectable with `kubectl logs`.

---

## Secrets

Database passwords, JWT secret and API keys live in `k8s/values-secret-{dev,prod}.yaml` — **git-ignored**, present only on the VM, with **different** credentials per environment. The chart has required-value guards: if a secret is missing, Helm refuses to deploy.

---

### Note on AI assistance
Parts of this DevOps setup and documentation were produced with AI assistance and are disclosed in the Git history with `ai:`-prefixed commit messages containing the prompt excerpts, per the course policy.

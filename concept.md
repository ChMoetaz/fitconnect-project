# Project Concept: Kubernetes-Based DevOps Lifecycle for FitConnect

**Author:** Moetez Cherni
**Registration Number:** 109463
**Course:** DevOps
**Repository:** [https://github.com/ChMoetaz/fitconnect-project](https://github.com/ChMoetaz/fitconnect-project)

---

## 1. Project Goal

The goal of this project is to implement the software lifecycle of a web application in a reproducible, cloud-hosted Kubernetes environment.

The application used is **FitConnect**, a digital fitness platform with a REST/WebSocket **backend** (Spring Boot), a single-page **frontend** (React / Vite) and a **PostgreSQL** database as a backing service. The focus of this project is not the application's business logic but the **DevOps setup around it**: building, testing, publishing container images, deploying to multiple environments, exposing services over HTTPS, and observing the running system through monitoring.

The setup runs on a **single-node k3s cluster hosted on an Azure virtual machine**. k3s is a lightweight, fully compatible Kubernetes distribution; using one VM keeps the scope and cost realistic while still allowing the implementation of Kubernetes concepts such as deployments, services, namespaces, ingress, rolling updates and horizontal scaling.

---

## 2. Repository Model

The project uses a **single mono-repository**:

[https://github.com/ChMoetaz/fitconnect-project](https://github.com/ChMoetaz/fitconnect-project)

Both the application and the infrastructure live together, which keeps the lifecycle transparent and reviewable in one place. The repository contains:

* `fitconnect-backend/` — Spring Boot source + `Dockerfile`
* `fitconnect-frontend/` — React (Vite) source + `Dockerfile` + `nginx.conf`
* `k8s/fitconnect/` — Helm chart for the application (backend, frontend, PostgreSQL, ingress)
* `k8s/values-*.yaml` — per-environment configuration
* `k8s/cluster-issuer.yaml`, `k8s/letsencrypt-issuer.yaml` — TLS issuers
* `k8s/monitoring/` — Prometheus + Grafana configuration and dashboard
* `infra/provision.py` — Infrastructure as Code (creates the Azure VM and network)
* `infra/bootstrap.sh` — installs the cluster software (k3s, Helm, cert-manager)
* `.github/workflows/deploy.yml` — the CI/CD pipeline
* `README.md` and `concept.md`

Branching model: work happens on `dev`; promotion to production happens by merging `dev` into `main`.

---

## 3. Technology Stack

| Component | Technology | Reason |
| --- | --- | --- |
| Application | FitConnect — Spring Boot backend + React (Vite) frontend | Existing deployable workload with a database dependency |
| Build tools | Maven (backend), npm / Vite (frontend) | Reproducible builds already part of the app |
| Version control | GitHub | Stores the mono-repository |
| CI/CD | GitHub Actions (GitHub-hosted runners) | Triggered by VCS changes; deploys over SSH to the cluster |
| Artifact registry | GitHub Container Registry (GHCR) | Container images are explicitly published and versioned |
| Runtime environment | k3s (single-node Kubernetes) on an Azure VM | Cloud-hosted environment with full Kubernetes features |
| Deployment automation | Helm | Reproducible deployment with environment-specific values |
| Backing service | PostgreSQL (self-hosted in-cluster) | Database required by the application |
| Ingress / vhosting | Traefik (bundled with k3s) | Exposes services through FQDNs and routes by hostname |
| DNS | DuckDNS | Free real domain for the VM's public IP |
| TLS | cert-manager + Let's Encrypt | Trusted HTTPS certificates, issued and renewed automatically |
| Monitoring | Prometheus + Grafana (kube-prometheus-stack) | Metrics collection and dashboards, provisioned in-cluster |
| Logging | Kubernetes container logs | Application writes to stdout/stderr, inspectable via kubectl |
| Infrastructure as Code | Python (Azure SDK) + shell bootstrap | Reproducible, idempotent provisioning of server and cluster |

The tools are chosen because they fit this setup and directly support the assignment requirements: reproducibility, automation, published artifacts, HTTPS access, monitoring, multiple environments, redundancy and zero-downtime deployment.

---

## 4. Target Environments

The project provides two target environments inside the cluster, separated by Kubernetes namespaces.

| Environment | Namespace | Purpose | Deployment trigger |
| --- | --- | --- | --- |
| `dev` | `fitconnect-dev` | Non-production environment to validate a new version | Push to the `dev` branch |
| `prod` | `fitconnect-prod` | Production environment for the reviewed deployment | Merge to `main` + **two-reviewer approval** |

Each environment has its own Helm values, services, ingress hostnames and **its own PostgreSQL instance**, so the two environments run independent configurations and data while using the same deployment mechanism.

Production runs with **at least two replicas** of the backend and frontend. Production deployment requires a **manual approval by two reviewers** (GitHub Environment protection rule), which implements the required double sign-off and prevents accidental releases.

---

## 5. Infrastructure Architecture

The infrastructure runs on a single Azure virtual machine (Ubuntu 24.04). Only the base tools are installed on the host (k3s, which bundles containerd, Helm and kubectl). All project services run inside Kubernetes:

* FitConnect backend
* FitConnect frontend
* PostgreSQL (one instance per environment)
* Traefik ingress controller (bundled with k3s)
* cert-manager
* Prometheus and Grafana

### Architecture Diagram

```text
Developer
   |
   | git push (dev)  /  merge PR (main)
   v
GitHub repository
   |
   | triggers workflow
   v
GitHub Actions (GitHub-hosted runner)
   |  test -> build images -> push to GHCR -> ssh + helm upgrade
   v
GitHub Container Registry (GHCR)
   |
   | k3s pulls the image
   v
Azure VM  —  k3s (single node)
   |
   |-- namespace: fitconnect-dev
   |     |-- backend Deployment (1 replica)
   |     |-- frontend Deployment (1 replica)
   |     |-- PostgreSQL StatefulSet + PVC
   |     |-- Ingress: dev.fitconnect-moetaz.duckdns.org / api-dev...
   |
   |-- namespace: fitconnect-prod
   |     |-- backend Deployment (>= 2 replicas)
   |     |-- frontend Deployment (>= 2 replicas)
   |     |-- PostgreSQL StatefulSet + PVC
   |     |-- Ingress: fitconnect-moetaz.duckdns.org / api...
   |
   |-- namespace: monitoring
   |     |-- Prometheus
   |     |-- Grafana  (Ingress: grafana.fitconnect-moetaz.duckdns.org)
   |
   |-- Traefik ingress  +  cert-manager (Let's Encrypt)
```

The pipeline runs on a **GitHub-hosted runner** and deploys to the VM over **SSH + Helm**. This separates the pipeline execution process from the deployed application runtime.

---

## 6. Infrastructure as Code and Reproducibility

The infrastructure is described in code, in two idempotent steps:

* **`infra/provision.py`** — uses the Azure SDK to create the resource group, virtual network, static public IP, network security group (ports 22/80/443), network interface and the VM. Every call is a `create_or_update` (upsert), so the script can be re-run without creating duplicates.
* **`infra/bootstrap.sh`** — runs on the VM to install k3s, Helm and cert-manager, and to apply the TLS issuer. It checks for existing components before installing, so it is also re-runnable.

Application deployment is then handled by Helm (`helm upgrade --install`), which is itself idempotent. This makes the whole environment reproducible from the repository: provision the VM, bootstrap the cluster, deploy the chart.

---

## 7. Application Lifecycle and Pipeline

The CI/CD pipeline ([.github/workflows/deploy.yml](.github/workflows/deploy.yml)) runs the application through the required stages: **test, build, deploy**.

### 7.1 Test Stage
A push to `dev` or `main` triggers the pipeline. It runs the backend unit tests (`mvn test`, H2 in-memory) and the frontend build (`npm ci` + `npm run build`). A failing test stops the pipeline before anything is published.

### 7.2 Build & Publish Stage
On success, the backend and frontend images are built and pushed to **GHCR**, tagged with the commit SHA. The container image is the explicit, versioned artifact of the project. The frontend is built per environment, because its API URL is compiled in at build time.

### 7.3 Deploy Stage
Deployment is **branch-based**:

* **Push to `dev`** → deploy to `fitconnect-dev` (automatic).
* **Merge to `main`** → deploy to `fitconnect-prod`, gated by the `production` GitHub Environment with **two required reviewers**.

The deploy job copies the chart to the VM and runs, for example:

```bash
helm upgrade --install fitconnect-prod ./k8s/fitconnect \
  --namespace fitconnect-prod \
  -f k8s/values-prod.yaml -f k8s/values-secret-prod.yaml \
  --set backend.image.tag=$SHA \
  --set frontend.image.tag=$SHA-prod
```

A change therefore propagates through the environments by moving through the branches: land on `dev` (deploys dev) → merge to `main` (deploys prod after approval).

---

## 8. Deployment Strategy, Redundancy and Zero Downtime

Production runs with at least two replicas:

```yaml
backend:  { replicas: 2 }
frontend: { replicas: 2 }
```

Kubernetes rolling updates keep the application reachable during deployment:

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 0
    maxSurge: 1
```

Probes ensure traffic only reaches ready pods. The backend has no Spring Actuator, so it uses TCP probes on port 8080; the frontend uses an HTTP probe on `/`:

```yaml
readinessProbe:
  tcpSocket:  { port: 8080 }   # backend
```

Because a new pod must become ready before an old one is removed, deployments are effectively **zero-downtime**.

**Honest limitation:** the cluster is a single node, so this provides **pod-level** high availability (survives pod crashes and rollouts) but not node-level HA. Full node redundancy would require a multi-node cluster.

---

## 9. Backing Service and Persistence Layer

FitConnect requires a database. **PostgreSQL** is deployed inside Kubernetes as the backing service, as a **StatefulSet with a PersistentVolumeClaim** (backed by the k3s `local-path` storage class), so data survives pod restarts.

Each environment has **its own PostgreSQL instance** with its own volume and credentials. Application configuration (datasource URL, credentials, JWT secret, API keys) is injected through **ConfigMaps and Secrets**, never hardcoded in the source. Secrets are provided per environment through git-ignored values files and are required by the chart (deployment fails if a secret is missing).

---

## 10. FQDN, HTTPS and Ingress

All external services are reachable through FQDNs over HTTPS, using a free **DuckDNS** domain pointing at the VM's public IP.

| Service | FQDN |
| --- | --- |
| Production frontend | `fitconnect-moetaz.duckdns.org` |
| Production API | `api.fitconnect-moetaz.duckdns.org` |
| Dev frontend | `dev.fitconnect-moetaz.duckdns.org` |
| Dev API | `api-dev.fitconnect-moetaz.duckdns.org` |
| Grafana | `grafana.fitconnect-moetaz.duckdns.org` |

TLS is terminated at the **Traefik** ingress. Certificates are issued and renewed automatically by **cert-manager** using **Let's Encrypt** (HTTP-01 challenge). An earlier iteration used `nip.io` with a self-signed CA; the project moved to DuckDNS + Let's Encrypt to obtain browser-trusted certificates, because Let's Encrypt cannot reliably issue for the shared `nip.io` domain.

---

## 11. Monitoring and Logging

Monitoring uses **Prometheus** and **Grafana**, provisioned in-cluster via the `kube-prometheus-stack` Helm chart in a dedicated `monitoring` namespace, and exposed over HTTPS.

Prometheus collects metrics from Kubernetes and the node; Grafana visualizes them. Monitored aspects include:

* pod availability and restarts
* CPU and memory usage per pod / namespace
* deployment replica status
* cluster health

A dedicated dashboard, **"FitConnect — dev & prod"**, shows the resource usage and health of both application environments side by side.

Logging is kept simple: the application writes to stdout/stderr, inspectable with `kubectl logs`. A full logging stack (EFK/Loki) is intentionally out of scope.

---

## 12. 12-Factor Considerations

| Factor | Application in this project |
| --- | --- |
| Codebase | Single repository tracked in Git, deployed to multiple environments |
| Dependencies | Declared via Maven (backend) and npm (frontend) |
| Config | Injected through Helm values, ConfigMaps and Secrets |
| Backing services | PostgreSQL treated as an attached backing service |
| Build, release, run | Test/build, image publishing and deploy are separated by the pipeline |
| Processes | Backend and frontend are stateless; state lives in PostgreSQL |
| Port binding | Backend on 8080, frontend on 80, exposed via Services/Ingress |
| Concurrency | Production runs multiple replicas |
| Disposability | Kubernetes replaces pods and rolls out new versions gracefully |
| Dev/prod parity | Both environments use the same chart with different values |
| Logs | Written to stdout/stderr |

---

## 13. Review Demonstration Plan

During the review, a full lifecycle iteration can be demonstrated:

1. Show the repository structure and this concept.
2. Show the running environments reachable over HTTPS (dev, prod, Grafana).
3. Introduce a small visible change in the application.
4. Commit and push to `dev`.
5. Show the CI pipeline triggered by the push (test → build → deploy dev).
6. Show the published image in GHCR.
7. Open a pull request `dev → main` and merge it.
8. Show the production deployment waiting for the two-reviewer approval.
9. Approve and let it deploy.
10. Verify production stays reachable during the rollout.
11. Show rollout status and the Grafana dashboards.

---

## 14. Scope Limitation

The project intentionally avoids a managed Kubernetes service (AKS/EKS/GKE), a managed database, external secret managers and a full logging stack. It uses a single self-managed VM with k3s.

The focus remains on:

* version control and branch-based promotion
* build and test automation
* artifact publishing (GHCR)
* Kubernetes deployment via Helm
* Infrastructure as Code (Python + shell)
* dev and production environments with separate databases
* two-reviewer production approval (double sign-off)
* redundancy and zero-downtime deployment
* FQDN and HTTPS access
* monitoring
* reproducibility from scratch

The known trade-off is single-node high availability (pod-level, not node-level), documented in section 8.

---

## 15. Indication of Source

All external sources and AI-assisted changes are indicated according to the course requirements.

For static sources, deep links or bibliographic references are placed close to the affected content.

For AI-assisted text or code, the related changes are committed separately. Commit messages use the required `ai:` prefix and include a human-readable excerpt of the prompt history that led to the change.

Example commit title:

```text
ai(claude-opus-4.8): helm chart for app, postgres and ingress
```

The purpose is to make transparent which parts of the concept or implementation were influenced by AI-based interaction.

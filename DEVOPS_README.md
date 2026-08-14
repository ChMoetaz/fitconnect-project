# FitConnect — DevOps

How FitConnect is **deployed and operated**. The application itself is documented in
[README.md](README.md); the full formal concept is in [concept.md](concept.md).

The application runs on a **single-node k3s (Kubernetes) cluster on an Azure VM**, in two
isolated environments (`dev`, `prod`), exposed over HTTPS, deployed by a CI/CD pipeline,
and observed with Prometheus + Grafana. Everything is defined in this repository and can be
reproduced from scratch (see [Reproduce from scratch](#reproduce-from-scratch)).

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

## Environments

| | dev | prod |
|---|---|---|
| **Namespace** | `fitconnect-dev` | `fitconnect-prod` |
| **Frontend** | `dev.fitconnect-moetaz.duckdns.org` | `fitconnect-moetaz.duckdns.org` |
| **API** | `api-dev.fitconnect-moetaz.duckdns.org` | `api.fitconnect-moetaz.duckdns.org` |
| **Database** | own PostgreSQL (own PVC + credentials) | own PostgreSQL (own PVC + credentials) |
| **Replicas (back/front)** | 1 / 1 | 2 / 2 |
| **Deploy trigger** | push to `dev` (automatic) | merge to `main` + 2 approvals |

Grafana is shared: `grafana.fitconnect-moetaz.duckdns.org`.

---

## Reproduce from scratch

Everything below can be run from the repository content. Replace `fitconnect-moetaz`
with your own DuckDNS name and `<vm-ip>` with the provisioned VM's public IP.

### 1. Provision the Azure VM (from your machine)
```bash
cd infra
pip install -r requirements.txt
az login
cp .env.example .env          # set AZURE_SUBSCRIPTION_ID + AZURE_SSH_PUBLIC_KEY_PATH
set -a; source .env; set +a
python provision.py           # idempotent; prints the VM public IP at the end
```
`provision.py` creates the resource group, network, static public IP, the VM, and a
network security group that **opens ports 22, 80 and 443** — so no manual firewall step is
needed.

### 2. Point DNS at the VM (DuckDNS)
1. Sign in at [duckdns.org](https://www.duckdns.org) and create a subdomain (e.g. `fitconnect-moetaz`).
2. Set its **current ip** to the VM's public IP.

`fitconnect-moetaz.duckdns.org` and any `*.fitconnect-moetaz.duckdns.org` now resolve to the VM.

### 3. Install the cluster software (on the VM)
```bash
ssh <user>@<vm-ip>
sudo curl -fsSL https://get.docker.com | sh          # Docker, to build images locally
git clone https://github.com/ChMoetaz/fitconnect-project.git ~/fitconnect
cd ~/fitconnect
bash infra/bootstrap.sh                               # k3s + Helm + cert-manager + TLS issuers
export KUBECONFIG=$HOME/.kube/config
```

### 4. Create the per-environment secrets (on the VM)
```bash
cd ~/fitconnect/k8s
for env in dev prod; do
cat > values-secret-$env.yaml <<EOF
postgres:
  auth:
    password: "$(openssl rand -hex 16)"
backend:
  secrets:
    jwtSecret: "$(openssl rand -base64 48)"
    geminiApiKey: ""
    googleMapsApiKey: ""
EOF
done
```
These files are git-ignored and hold **different** credentials per environment.

### 5. Build and import the images (on the VM)
The frontend's API URL is baked in at build time, so it is built once per environment.
```bash
cd ~/fitconnect
docker build -t fitconnect-backend:latest ./fitconnect-backend
docker build --build-arg VITE_API_BASE_URL=https://dev.fitconnect-moetaz.duckdns.org \
  -t fitconnect-frontend:dev ./fitconnect-frontend
docker build --build-arg VITE_API_BASE_URL=https://fitconnect-moetaz.duckdns.org \
  -t fitconnect-frontend:prod ./fitconnect-frontend
for img in fitconnect-backend:latest fitconnect-frontend:dev fitconnect-frontend:prod; do
  docker save "$img" | sudo k3s ctr images import -
done
```

### 6. Deploy both environments (on the VM)
```bash
cd ~/fitconnect/k8s
helm upgrade --install fitconnect-dev ./fitconnect -n fitconnect-dev --create-namespace \
  -f values-dev.yaml -f values-secret-dev.yaml \
  --set backend.image.repository=fitconnect-backend --set backend.image.tag=latest \
  --set frontend.image.repository=fitconnect-frontend --set frontend.image.tag=dev

helm upgrade --install fitconnect-prod ./fitconnect -n fitconnect-prod --create-namespace \
  -f values-prod.yaml -f values-secret-prod.yaml \
  --set backend.image.repository=fitconnect-backend --set backend.image.tag=latest \
  --set frontend.image.repository=fitconnect-frontend --set frontend.image.tag=prod
```

### 7. Install monitoring (on the VM)
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  -f ~/fitconnect/k8s/monitoring/values.yaml \
  --set grafana.adminPassword='<choose-a-password>'
```
Then import `k8s/monitoring/dashboard-fitconnect.json` into Grafana (Dashboards → Import).

### 8. Verify
```bash
kubectl get pods -A | grep -E 'fitconnect|monitoring'
kubectl get certificate -A          # all should reach READY=True
```
Open (browser-trusted HTTPS):
- prod → `https://fitconnect-moetaz.duckdns.org`
- dev → `https://dev.fitconnect-moetaz.duckdns.org`
- Grafana → `https://grafana.fitconnect-moetaz.duckdns.org`

---

## CI/CD (automated deployments)

Once the cluster is up (steps 1–4 above), deployments are automated by the pipeline
([.github/workflows/deploy.yml](.github/workflows/deploy.yml)) instead of the manual
steps 5–6.

```
push → dev    :  test → build → deploy to fitconnect-dev   (automatic)
merge → main  :  test → build → deploy to fitconnect-prod  (2 reviewers must approve)
```

Stages:
1. **test** — backend `mvn test` (H2) + frontend `npm ci && npm run build`.
2. **build** — images built and pushed to **GHCR**, tagged with the commit SHA (frontend per environment).
3. **deploy** — chart copied to the VM, applied with `helm upgrade`. On `main`, the job runs in the GitHub `production` **Environment** with **2 required reviewers**, so it pauses for approval (double sign-off).

### One-time setup to enable it
- **GitHub → Secrets:** `SSH_HOST`, `SSH_USER`, `SSH_KEY` (a deploy key added to the VM's `~/.ssh/authorized_keys`), `VITE_GOOGLE_MAPS_API_KEY`.
- **GitHub → Environments:** `dev` (no rules) and `production` (enable **Required reviewers**, add 2 people).
- **GHCR packages** `fitconnect-backend` / `fitconnect-frontend`: set to **public** after the first build (so k3s can pull them).

The VM keeps the git-ignored `values-secret-{dev,prod}.yaml` from step 4; the pipeline
references them at deploy time.

---

## Reference

### Kubernetes objects (Helm chart `k8s/fitconnect/`)

| Object | Used for |
|---|---|
| **Deployment** | backend, frontend (stateless, replicas, rolling update) |
| **StatefulSet** | PostgreSQL (stateful, persistent volume) |
| **Service** | stable internal address for each component |
| **ConfigMap** | non-secret backend config (datasource URL, JVM options) |
| **Secret** | passwords, JWT secret, API keys |
| **PersistentVolumeClaim** | PostgreSQL data (k3s `local-path` storage) |
| **Ingress** | external access, routed by hostname (Traefik) |
| **ClusterIssuer / Certificate** *(cert-manager)* | HTTPS certificates (Let's Encrypt) |

### Availability & zero-downtime
Production runs 2 replicas with a rolling update (`maxUnavailable: 0`, `maxSurge: 1`) and
probes (TCP on the backend — no Spring Actuator; HTTP `/` on the frontend). A new pod must
be ready before an old one is removed → zero-downtime deploys.
**Limitation:** single node → pod-level HA only (not node-level).

### Infrastructure as Code

| File | Runs on | Creates / does |
|---|---|---|
| [infra/provision.py](infra/provision.py) | your machine (Azure SDK) | resource group, vnet/subnet, static public IP, NSG (22/80/443), NIC, the VM |
| [infra/bootstrap.sh](infra/bootstrap.sh) | the VM | installs k3s + Helm + cert-manager, applies the TLS issuer |

Both are idempotent (Azure upserts; the shell script checks before installing).

### TLS / DNS
- **DuckDNS** provides the domain pointing at the VM's public IP.
- **cert-manager + Let's Encrypt** (HTTP-01) issue and renew trusted certificates automatically.
- An earlier iteration used `nip.io` + a self-signed CA; the project moved to DuckDNS +
  Let's Encrypt because Let's Encrypt cannot reliably issue for the shared `nip.io` domain.

### Monitoring
Prometheus + Grafana (`kube-prometheus-stack`, config in [k8s/monitoring/](k8s/monitoring/))
run in the `monitoring` namespace, exposed over HTTPS. Prometheus scrapes the metrics,
Grafana visualizes them; a dedicated dashboard *"FitConnect — dev & prod"* shows pod health,
CPU/memory, restarts and replica status for both environments. Logs: `kubectl logs`.

### Secrets
DB passwords, JWT secret and API keys live in `k8s/values-secret-{dev,prod}.yaml` —
**git-ignored**, present only on the VM, **different per environment**. The chart has
required-value guards: if a secret is missing, Helm refuses to deploy.

### Common operations
```bash
kubectl -n fitconnect-prod get pods
kubectl -n fitconnect-prod logs deploy/fitconnect-prod-backend
kubectl -n fitconnect-prod rollout restart deploy/fitconnect-prod-frontend
helm -n fitconnect-prod rollback fitconnect-prod        # roll back a release
```

---

### Note on AI assistance
Parts of this DevOps setup and documentation were produced with AI assistance and are
disclosed in the Git history with `ai:`-prefixed commit messages, per the course policy.

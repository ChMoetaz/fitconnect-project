# FitConnect

FitConnect is a digital fitness platform combining three things existing apps (Freeletics, MyFitnessPal, Strava, ClassPass, BetterMe) never do all together:

1. **AI-generated personalized training plans**
2. **Coach matching**
3. **Local fitness communities**

University project (Masterprojekt / exposé) — Medieninformatik, BHT Berlin.

**Authors**: Moetez Cherni

> Deployment, infrastructure and CI/CD are documented separately in **[DEVOPS_README.md](DEVOPS_README.md)** and **[concept.md](concept.md)**.

---

## Project structure

This is a monorepo containing two independent projects:

```
fitconnect-project/
├── backend/     # Spring Boot REST API + WebSocket chat
└── frontend/    # React (Vite) single-page app
```

---

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3.4, Spring Data JPA, Spring Security |
| Database | PostgreSQL (via Docker) |
| Auth | Stateless JWT (HMAC-SHA256) |
| AI | Google Gemini API (training plan generation, adaptation, personalized recommendations) |
| Maps | Google Geocoding API + Google Maps JavaScript API |
| Real-time | STOMP over WebSocket (SockJS), for the in-app group chat |
| Frontend | React 19, Vite, react-router-dom, Tailwind CSS, axios |
| API docs | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5, Mockito, Spring Boot Test (H2 in-memory) |

---

## Features

### Must-haves
- User registration & login (JWT)
- Onboarding (fitness goal, level, training frequency, sport)
- AI-generated training plans (Google Gemini)
- Coach matching (browse, filter by sport, geolocation)
- Community groups (browse, join/leave, geolocation)
- Progress tracking

### Nice-to-haves
- **Adaptive training plans** — re-tunes an existing plan from real logged progress
- **Achievement system** — badges automatically awarded from progress history
- **Fitness events** — group events tied to a community, with registration
- **Google Maps integration** — interactive map view for coaches and community groups, `/nearby` search
- **In-app real-time chat** — WebSocket group chat per community, history persisted
- **AI-personalized recommendations** — Gemini-ranked coach/community suggestions based on user profile (in addition to the classic browse/filter views)

### Admin
A seeded `ADMIN` account can manage users (list, change role, delete), and manage/edit/delete coaches and community groups, via a dedicated admin section in the frontend navigation.

---

## Getting started

### Prerequisites
- Java 17+
- Maven
- Node.js 18+ / npm
- Docker (for PostgreSQL)
- A Google Gemini API key ([Google AI Studio](https://aistudio.google.com))
- A Google Maps API key with **Geocoding API** and **Maps JavaScript API** enabled ([Google Cloud Console](https://console.cloud.google.com))

### 1. Backend setup

```bash
cd backend
cp .env.example .env
```

Edit `.env` and fill in:
```
JWT_SECRET=<a random string, at least 32 characters>
GEMINI_API_KEY=<your Gemini API key>
GOOGLE_MAPS_API_KEY=<your Google Maps API key>
```

**Start PostgreSQL:**
```bash
docker compose up -d postgres
```

**Run the backend:**
```bash
mvn spring-boot:run
```
Or open the project in IntelliJ and run `FitconnectBackendApplication`.

The API is available at `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

**Run tests:**
```bash
mvn test
```

**Alternative — run everything (backend + DB) via Docker:**
```bash
docker compose up -d --build
docker compose down   # add -v to also drop the DB volume
```

### 2. Frontend setup

```bash
cd frontend
npm install
cp .env.example .env
```

Edit `.env`:
```
VITE_API_BASE_URL=http://localhost:8080
VITE_GOOGLE_MAPS_API_KEY=<your Google Maps API key>
```

**Run the dev server:**
```bash
npm run dev
```
The app is available at `http://localhost:5173`.

**Build for production:**
```bash
npm run build
```

---

## Default admin account

A default admin account is seeded automatically at backend startup:

```
email:    admin@gmail.com
password: azerty123
```

⚠️ This is a development-only credential. Change it before any real deployment.

---

## Authentication

- `POST /api/users/register` and `POST /api/users/login` return an `accessToken` (JWT, valid 24h).
- Include it on all protected requests: `Authorization: Bearer <accessToken>`.
- In Swagger UI, use the **Authorize** button (top right) to paste the token once and have it applied to every subsequent request.

---

## Main API endpoints (overview)

| Area | Endpoints |
|---|---|
| Auth | `POST /api/users/register`, `POST /api/users/login` |
| Users | `GET /api/users/{id}`, `POST /api/users/{id}/onboarding` |
| Training plans | `GET/POST /api/users/{id}/training-plans`, `POST .../training-plans/{id}/adapt` |
| Coaches | `GET/POST /api/coaches`, `GET /api/coaches/recommend`, `GET /api/coaches/nearby`, `PUT/DELETE /api/coaches/{id}` *(admin)* |
| Community | `GET/POST /api/community-groups`, `POST .../join`, `GET .../nearby`, `PUT/DELETE .../{id}` |
| Events | `GET/POST /api/community-groups/{id}/events`, register/unregister |
| Progress | `GET/POST /api/users/{id}/progress` |
| Achievements | `GET /api/achievements`, `GET /api/users/{id}/achievements` |
| Chat (REST) | `GET /api/community-groups/{id}/messages` |
| Chat (WebSocket) | `/ws?token=<jwt>` (SockJS), STOMP destinations `/app/...` and `/topic/...` |
| AI recommendations | `GET /api/users/{id}/coaches/recommended`, `GET /api/users/{id}/community-groups/recommended` |
| Admin | `GET /api/admin/users`, `PATCH /api/admin/users/{id}/role`, `DELETE /api/admin/users/{id}` |

Full, interactive documentation: `http://localhost:8080/swagger-ui.html`.

---

## Notes

- Secrets (`JWT_SECRET`, `GEMINI_API_KEY`, `GOOGLE_MAPS_API_KEY`) are never committed — always provided via `.env` (git-ignored) or environment variables.
- The Gemini model used is a **preview** model and may occasionally return `503` under high demand; the backend retries automatically and degrades gracefully on persistent failure.
- Google Maps geocoding degrades gracefully: if it fails (bad address, quota, missing key), the affected coach/group is still created — it simply won't appear on the map or in `/nearby` results.

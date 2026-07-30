# FitConnect – Backend

Spring Boot backend of the FitConnect project (training plans, coach matching, community).

## Stack
- Java 17, Spring Boot 3.3
- Spring Web, Spring Data JPA, Bean Validation
- Spring Security + JWT (jjwt) — stateless auth on every `/api/**` endpoint
- Spring WebFlux `WebClient` — used to call the Google Gemini API (plan generation / recommendations) and the Google Maps Geocoding API
- Spring WebSocket (STOMP) — real-time community-group chat
- PostgreSQL (H2 in-memory for tests)
- springdoc-openapi (Swagger UI)
- Lombok

## Running locally

You need a `.env` file for the secrets (it is git-ignored). Copy the template and fill it in:
```bash
cp .env.example .env
# set at least JWT_SECRET (min 32 chars); GEMINI_API_KEY is optional (only plan generation needs it)
```

### Option A — everything in Docker (backend + DB together)
```bash
docker compose up -d --build
```
This builds the backend image and starts both containers: PostgreSQL (`fitconnect`/`fitconnect`,
port 5432) and the Spring Boot backend. A Postgres healthcheck + `depends_on` make the backend
wait until the DB is ready. Stop with `docker compose down` (add `-v` to also drop the DB volume).

The host port is `8080` by default; override it with `BACKEND_PORT` in `.env` if 8080 is taken.

### Option B — DB in Docker, app from the IDE
```bash
docker compose up -d postgres      # start only the database
```
Then run `FitconnectBackendApplication` from IntelliJ (make sure `JWT_SECRET` — and optionally
`GEMINI_API_KEY` — are set in the Run Configuration), or from the command line:
```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

## Tests
```bash
mvn test
```
JUnit 5 + Mockito unit tests for every service, plus Spring Boot integration tests (register →
login → onboarding → plan generation, and the JWT 401/403/200 matrix) running on in-memory H2 —
no Docker or real API keys needed to run the suite.

## Project structure
```
src/main/java/com/fitconnect/backend/
├── domain/       # JPA entities (User, UserProfile, CoachProfile, SportType,
│                 #   CommunityGroup, TrainingPlan, Exercise, ProgressRecord,
│                 #   Event, Message, Achievement, UserAchievement)
├── repository/   # Spring Data JPA repositories
├── service/      # Business logic (User, Onboarding, TrainingPlan, AiTrainingPlan,
│                 #   CoachRecommendation, Recommendation, Community, Event, Message,
│                 #   ProgressTracking, Achievement, AdminUser, Geocoding, SportType)
├── controller/   # REST controllers (+ ChatController for WebSocket/STOMP)
├── dto/          # Request/response objects
├── security/     # JWT filter chain, WebSocket handshake auth, error handlers
├── config/       # CORS, PasswordEncoder, WebSocket, OpenAPI, typed properties, seeders
├── util/         # GeoUtils (Haversine distance for /nearby)
└── exception/    # Global error handling
```

## Authentication

All `/api/**` endpoints (except `register`, `login`, and the Swagger/OpenAPI docs) require a
`Authorization: Bearer <token>` header. A token is returned by `register` and `login` and is valid
for 24h. Admin-only endpoints (`/api/admin/**`) additionally require the `ADMIN` role. A default
admin account (`admin@gmail.com`) is seeded at startup — see the note in "Before you deposit" below.

## Main endpoints

| Method | URL | Description |
|---|---|---|
| POST | `/api/users/register` | Create an account (returns a JWT) |
| POST | `/api/users/login` | Log in (returns a JWT) |
| GET | `/api/users/{userId}` | User details |
| POST | `/api/users/{userId}/onboarding` | Submit onboarding |
| GET/POST | `/api/users/{userId}/training-plans` | List plans / generate one via Gemini |
| POST | `/api/users/{userId}/training-plans/{planId}/adapt` | Adapt a plan from recent progress |
| DELETE | `/api/users/{userId}/training-plans/{planId}` | Delete a plan |
| GET/POST | `/api/users/{userId}/progress` | Progress tracking |
| GET | `/api/users/{userId}/coaches/recommended` | AI-recommended coaches |
| GET | `/api/users/{userId}/community-groups/recommended` | AI-recommended groups |
| GET | `/api/users/{userId}/achievements` | The user's earned achievements |
| GET | `/api/achievements` | All available achievements |
| GET | `/api/coaches` | List coaches |
| GET | `/api/coaches/recommend?sportTypeId=` | Recommend coaches by sport |
| GET | `/api/coaches/nearby?lat=&lng=&radiusKm=` | Coaches within a radius |
| POST/PUT/DELETE | `/api/coaches`, `/api/coaches/{coachId}` | Create / update / delete a coach profile |
| GET/POST | `/api/community-groups` | List / create groups |
| PUT/DELETE | `/api/community-groups/{communityId}` | Update / delete a group |
| GET | `/api/community-groups/nearby?lat=&lng=&radiusKm=` | Groups within a radius |
| POST | `/api/community-groups/{groupId}/join` / `/leave` | Join / leave a group |
| GET/POST | `/api/community-groups/{groupId}/events` | List / create events |
| POST | `/api/community-groups/{groupId}/events/{eventId}/register` / `/unregister` | Attend / cancel |
| GET | `/api/community-groups/{groupId}/messages` | Last 50 chat messages (history) |
| GET | `/api/admin/users` | (ADMIN) List all users |
| PATCH | `/api/admin/users/{userId}/role` | (ADMIN) Change a user's role |
| DELETE | `/api/admin/users/{userId}` | (ADMIN) Delete a user |

## Real-time chat

Group chat runs over STOMP-on-WebSocket (`config/WebSocketConfig`, authenticated in `security/`).
Clients send to `/app/community-groups/{groupId}/messages` and subscribe to
`/topic/community-groups/{groupId}/messages`; message history is fetched via the REST endpoint above.
`ws-test.html` at the repo root is a throwaway manual test page for this (git-ignored, not part of the app).

## Before you deposit
- **Never commit or zip the real `.env`** — it holds live secrets (`JWT_SECRET`, `GEMINI_API_KEY`,
  `GOOGLE_MAPS_API_KEY`). It is git-ignored; ship `.env.example` instead.
- A default admin (`admin@gmail.com`) is seeded at startup with a well-known development password
  (`config/AdminSeeder`). Change it — or the seeder — before any real deployment.

## Next steps
- Refresh-token support (currently the JWT just expires after 24h)
- CI pipeline running `mvn test` on every push
- A real cloud deployment (the Docker image and full `docker compose` stack are ready and tested)

# PawSpot

A full-stack application for reporting lost and found pets. Visitors can browse listings without an account, while authenticated users can publish posts with a photo and manage their own listings.

Built as a personal project with **Java, Spring Boot, React, TypeScript and PostgreSQL**. The interface is primarily in Greek.

**Status:** In active development. Browsing, login and post management are available in the frontend. Registration and account deletion are implemented in the backend; their frontend flows are still planned.

## Features

- **Lost and found listings:** Create posts with a photo, location, pet species and description; edit details, mark a post as resolved or delete it.
- **Search and filtering:** Browse paginated results by lost/found category, species and city. City matching supports partial, case-insensitive and accent-insensitive searches. Filters and page selection are stored in the URL.
- **Ownership-based authorization:** Users can edit and delete only their own posts, with access checks enforced by the backend.
- **Cookie-based authentication:** JWT access and refresh tokens in HTTP-only cookies, BCrypt password hashing and CSRF protection.
- **Refresh-token lifecycle:** Token rotation, reuse detection, refresh-session revocation and scheduled cleanup of expired token records.
- **Authentication across tabs:** Web Locks coordinate authentication operations, while shared identity tracking prevents stale requests from being retried under a different user.
- **Image storage:** Cloudinary uploads with file validation and cleanup when posts are deleted.
- **Account deletion API:** Password confirmation before removing the user, posts and refresh-session records, followed by image-cleanup attempts.

The backend also supports keyword search over titles and descriptions, plus status filtering and sorting. The keyword-search input is not yet connected to the frontend.

## Tech stack

| Area | Technologies |
| --- | --- |
| Backend | Java 17, Spring Boot, Spring Security, Spring Data JPA, Jakarta Validation |
| Database | PostgreSQL 17, PostgreSQL `unaccent` extension |
| Frontend | React, TypeScript, Vite, Tailwind CSS, React Router |
| API and server state | Axios, TanStack Query |
| Mapping and image storage | MapStruct, Cloudinary |
| Backend testing | JUnit, Mockito, Spring Boot Test, Testcontainers |
| Frontend testing | Vitest, React Testing Library, MSW, Playwright |
| Local tooling | Maven Wrapper, npm, Docker Compose |

## Implementation highlights

- **Composable database filters:** [JPA Specifications](backend/src/main/java/com/pawspot/post/specification/PetPostSpecification.java) combine optional filters and use PostgreSQL `unaccent` for city matching. Pagination adds an ID tie-breaker to keep ordering stable.
- **Transactional token rotation:** [Refresh-token rotation](backend/src/main/java/com/pawspot/auth/refreshtoken/service/RotationTransactionService.java) locks the user and refresh-session rows before updating token state. Reusing a revoked refresh token revokes its session and token family.
- **Coordinated frontend authentication:** The [API client](frontend/src/api/client.ts) retries an eligible request once after refresh, with identity checks before and after refresh. [Web Locks](frontend/src/api/authLock.ts) serialize authentication operations across tabs.
- **Database and image consistency:** If saving a new post fails after an image upload, the service attempts to remove that image. Database deletion happens before external image cleanup; cleanup failures are logged.

## Run locally

### Prerequisites

- JDK 17 or newer.
- Node.js 24.15 or newer within the Node.js 24 release line, with npm.
- Docker with Docker Compose, with the Docker daemon running.
- A Cloudinary account for uploading images.
- Bash or a compatible shell for the commands below; OpenSSL for generating a signing key.

### 1. Clone and configure

```bash
git clone https://github.com/malvin-hoxha/pawspot.git
cd pawspot
cp .env.example .env
```

Fill in `.env` with local values. Add the Cloudinary variables, which are not included in the current example file:

```dotenv
POSTGRES_DB=pawspot
POSTGRES_USER=pawspot
POSTGRES_PASSWORD=replace-with-a-local-password
JWT_SECRET=replace-with-a-base64-signing-key
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret
FRONTEND_ORIGIN=http://localhost:5173
```

Generate a signing key and copy its output into `JWT_SECRET`:

```bash
openssl rand -base64 32
```

Use shell-compatible values in `.env`, quoting values that contain spaces or shell metacharacters. The file is ignored by Git.

### 2. Start PostgreSQL

From the repository root:

```bash
docker compose up -d postgres
docker compose exec postgres pg_isready
```

Once PostgreSQL is ready, enable the extension used for city searches:

```bash
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "CREATE EXTENSION IF NOT EXISTS unaccent;"'
```

Docker Compose runs PostgreSQL only. The backend and frontend run separately. Database data is retained in a named Docker volume.

### 3. Start the backend

In a terminal at the repository root:

```bash
set -a
source .env
set +a
cd backend
./mvnw spring-boot:run
```

Exporting the variables is necessary: Spring Boot does not automatically load the root `.env` file. The backend listens at `http://localhost:8080` and creates or updates its local schema through Hibernate.

### 4. Start the frontend

In another terminal at the repository root:

```bash
cd frontend
npm ci
npm run dev -- --port 5173 --strictPort
```

Open **http://localhost:5173**. Vite proxies `/api` requests to the backend on port 8080. Keep port 5173 for the documented CORS and Playwright configuration.

### 5. Create a local account

Until the registration page is added, register through the API. With both servers running, fetch a CSRF cookie and send its value in the registration request:

```bash
curl --fail --silent --show-error \
  --cookie-jar /tmp/pawspot-cookies.txt \
  http://localhost:5173/api/auth/csrf

CSRF_TOKEN="$(awk '$6 == "XSRF-TOKEN" {print $7}' /tmp/pawspot-cookies.txt)"

curl --fail --show-error \
  --cookie /tmp/pawspot-cookies.txt \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  --data '{"name":"Local User","email":"local@example.com","password":"LocalDev123!"}' \
  http://localhost:5173/api/auth/register
```

Then open `/login` in the browser and sign in with that local account. A fresh database starts without posts; creating a post requires valid Cloudinary credentials.

The default cookie settings are intended for local HTTP development. An HTTPS deployment requires secure cookies and deployment-specific configuration.

## Tests

### Backend

From `backend/`:

```bash
./mvnw test
```

Integration tests use a separate PostgreSQL 17 Testcontainer and test-only configuration. Docker must be running. Coverage includes post authorization and validation, refresh-token rotation and reuse, account deletion and concurrent database operations.

### Frontend unit and integration tests

From `frontend/`, after `npm ci`:

```bash
npm run test:run
```

These tests cover API-client behavior and authentication hooks, including delayed requests and login/logout races.

### Browser tests

Keep the backend and Vite development server running. Create two distinct local test accounts through the registration API, then run the following from `frontend/`:

```bash
npx playwright install chromium
export E2E_USER_A_EMAIL='first-test-user@example.com'
export E2E_USER_A_PASSWORD='replace-with-first-test-password'
export E2E_USER_B_EMAIL='second-test-user@example.com'
export E2E_USER_B_PASSWORD='replace-with-second-test-password'
npx playwright test
```

Use the actual credentials of the two accounts you created. Playwright does not start the application servers or register these users automatically. The suite exercises concurrent refresh, cross-tab logout, expired sessions and retries after an identity change.

## API overview

| Method | Endpoint | Purpose / access |
| --- | --- | --- |
| `GET` | `/api/auth/csrf` | Initialize CSRF protection |
| `POST` | `/api/auth/register` | Create an account |
| `POST` | `/api/auth/login` | Authenticate and set token cookies |
| `POST` | `/api/auth/refresh` | Rotate a valid refresh token |
| `POST` | `/api/auth/logout` | Revoke the refresh session and clear cookies |
| `GET` | `/api/auth/me` | Get the authenticated user |
| `GET` | `/api/posts` | Public, filtered and paginated listings |
| `GET` | `/api/posts/{id}` | Public post details |
| `POST` | `/api/posts` | Authenticated; multipart fields and `image` |
| `GET` | `/api/posts/my` | Authenticated user's posts |
| `PATCH` | `/api/posts/{id}` | Owner only; partial JSON update |
| `DELETE` | `/api/posts/{id}` | Owner only |
| `DELETE` | `/api/users/me` | Authenticated; password confirmation in JSON |

State-changing requests require a valid CSRF token, including registration and login. Browser requests use cookies and the `X-XSRF-TOKEN` header.

Listing query parameters: `type`, `status`, `species`, `city`, `search`, `page`, `size`, `sortBy` and `direction`. Pagination is zero-based; page size defaults to 10 and is limited to 100.

## Project structure

| Path | Contents |
| --- | --- |
| `backend/src/main/java/com/pawspot/` | Authentication, security, posts, users, image storage and exception handling |
| `backend/src/test/` | Unit and PostgreSQL integration tests |
| `frontend/src/api/` | HTTP client, CSRF and refresh coordination |
| `frontend/src/features/auth/` | Authentication API functions, hooks and route protection |
| `frontend/src/pages/` | Browsing, post management and login pages |
| `frontend/tests/` | API-client, authentication and browser tests |
| `docker-compose.yml` | Local PostgreSQL service |

## Next steps

- Connect keyword search to the frontend filters.
- Add registration and account-deletion screens, and improve the login experience.
- Add private communication between a listing's owner and interested users.

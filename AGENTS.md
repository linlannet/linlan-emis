# AGENTS.md — linlan-emis (BuddyOne)

## Project
Java 17 + Spring Boot 3.5.16 **multi-tenant IAM / SSO** platform (local-account auth, OAuth2/OIDC authorization server, tenant context, role-based access). Version 0.1.0.

## Layout
- `backend/`        emis-backend — Spring Boot app (artifactId emis-backend, v0.1.0); local auth + JJWT + TenantContext + @RequiresRole (fail-closed)
- `test-client/`    linlan-emis-test-client — demo OIDC authorization-code client (v0.1.0)
- `py-api/`         Python API (placeholder)
- `frontend/`       Web console (placeholder)
- `scripts/`        bash helpers: package.sh, start-local-jar.sh, start-mysql-jar.sh, verify-oidc.sh
- `docs/`           01-architecture.md, 02-user-tenant-model.md, 03-oauth2-authorization-server.md, 04-permission-model.md

Dependency direction: business rules live in the backend's service/security layer; controllers only translate protocol; JWT/tenant semantics are shared via `TenantContext` and `@RequiresRole`.

## Build & Test
- Backend build: `cd backend && mvn -q -DskipTests clean package`
- Backend run (local/H2, zero external deps): `./scripts/start-local-jar.sh --build` (serves http://localhost:8080)
- Test-client build/run: `cd test-client && mvn -q -DskipTests clean package`
- Default accounts: admin/admin123 (roleLevel=0), guest/guest123 (roleLevel=2)
- Login: `POST /api/auth/login` `{ userName, password }` -> `Result<LoginResponse>`

## Conventions
- Java 17 records for DTOs / value objects
- JWT via jjwt 0.12.x; password hashing via spring-security-crypto (BCrypt)
- Multi-tenancy: `tenant_id` + `TenantContext`; authorization via `@RequiresRole` (fail-closed)
- Tech stack aligned with dsh-java: Spring Boot 3.5.16 / Java 17 / MyBatis 3.0.5
- Docs are Chinese by default; keep `README.md` and `README.en.md` in sync

## Do Not
- Do not edit or commit `target/`
- Do not log secrets (passwords, JWT, tokens)
- Do not bypass `@RequiresRole` / `TenantContext` isolation
- Do not add a new third-party dependency without explicit justification

## Key Docs
- `README.md`, `README.en.md`
- `docs/01-architecture.md` ... `docs/04-permission-model.md`
- `llms.txt`, `CHANGELOG.md`

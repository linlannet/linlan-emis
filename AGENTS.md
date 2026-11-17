# AGENTS.md — linlan-emis (BuddyOne)

## Project
Java 17 + Spring Boot 3.5.16 **multi-tenant IAM / SSO** platform (local-account auth, GitHub App / WeChat QR federation, OAuth2/OIDC authorization server, tenant context, role-based access). Version 1.1.0.

## Layout
- `backend/`        emis-backend — Spring Boot app (artifactId emis-backend, v1.1.0); local auth + JJWT + TenantContext + @RequiresRole (fail-closed)
- `backend/src/main/java/net/linlan/emis/federated/` — federation vertical slice (config / error / provider / dto / state / security / identity / audit / repository / web)
- `test-client/`    linlan-emis-test-client — demo OIDC authorization-code client (v1.0.0)
- `py-api/`         Python API (placeholder)
- `frontend/`       Web console (placeholder)
- `scripts/`        bash helpers: package.sh, start-local-jar.sh, start-mysql-jar.sh, verify-oidc.sh
- `docs/1.1.0/`     v1.1.0 design set (spec.md, design.md, tasks.md, release-v1.1.0-development-notes.md)
- `docs/1.0.0/`     v1.0.0 design set + existing design (01-architecture.md, 02-user-tenant-model.md, 03-oauth2-authorization-server.md, 04-permission-model.md)

Dependency direction: business rules live in the backend's service/security layer; controllers only translate protocol; JWT/tenant semantics are shared via `TenantContext` and `@RequiresRole`. Federation code is isolated under `net.linlan.emis.federated` and only touches existing layers through `JwtUtil`, `EmisUserMapper`, `UserService` and `UserController`.

## Build & Test
- Backend build: `cd backend && mvn -q -DskipTests clean package`
- Backend run (local/H2, zero external deps): `./scripts/start-local-jar.sh --build` (serves http://localhost:8080)
- Unit tests: `cd backend && mvn -q test`
- Federation end-to-end IT (H2 + mocked provider, never calls real GitHub/WeChat): `cd backend && mvn -q -Dtest=FederatedLoginIT test`
- Test-client build/run: `cd test-client && mvn -q -DskipTests clean package`
- Default accounts: admin/admin123 (roleLevel=0), guest/guest123 (roleLevel=2)
- Login: `POST /api/auth/login` `{ userName, password }` -> `Result<LoginResponse>`

## Federation (v1.1.0)
- Providers: **GitHub App** (user-to-server code exchange, PKCE S256) and **WeChat Open Platform website QR** (`snsapi_login`).
- Identity keys: GitHub numeric user id; WeChat **unionid** (openid is only the latest auxiliary value). Missing unionid -> `40115`, never fall back to openid.
- First login self-provisions into experience tenant `tenant_id=2` and experience role `role_level=2`; no admin approval; existing email/phone conflict -> `40910` (no silent merge).
- Endpoints: `GET /api/auth/federated/{provider}/authorize`, `GET .../{provider}/bind/authorize`, `GET .../{provider}/callback`, `GET .../bindings`, `DELETE .../bindings/{bindingId}`, `GET .../providers`, `GET .../wechat/in-app-browser-guide`.
- Config is environment-only: `EMIS_FEDERATED_GITHUB_*`, `EMIS_FEDERATED_WECHAT_*`, `EMIS_FEDERATED_PROVISIONING_*`, `EMIS_FEDERATED_STATE_*`, `EMIS_FEDERATED_RATE_LIMIT_*`.
- Bind target comes only from the session identity; request bodies/headers `user_id` / `tenant_id` / `role_level` are never read.
- One-shot state (TTL <= 10 min) + digest cookie; authorization codes are single-use; provider access tokens live only in local variables.

## Conventions
- Java 17 records for DTOs / value objects
- JWT via jjwt 0.12.x; password hashing via spring-security-crypto (BCrypt)
- Multi-tenancy: `tenant_id` + `TenantContext`; authorization via `@RequiresRole` (fail-closed)
- Tech stack aligned with dsh-java: Spring Boot 3.5.16 / Java 17 / MyBatis 3.0.5
- Docs are Chinese by default; keep `README.md` and `README.en.md` in sync

## Do Not
- Do not edit or commit `target/`
- Do not log secrets (passwords, JWT, tokens, authorization codes, client_secret / AppSecret, PEM)
- Do not write any secret into `application*.yml`, `schema.sql` or database tables
- Do not bypass `@RequiresRole` / `TenantContext` isolation
- Do not add a provider registry, SPI/ServiceLoader discovery, priority routing or provider admin CRUD — the provider contract is one interface plus config
- Do not add a new third-party dependency without explicit justification

## Key Docs
- `README.md`, `README.en.md`
- `docs/1.1.0/spec.md`, `docs/1.1.0/design.md`, `docs/1.1.0/tasks.md`, `docs/1.1.0/release-v1.1.0-development-notes.md`
- `docs/1.0.0/01-architecture.md` ... `docs/1.0.0/04-permission-model.md`
- `llms.txt`, `CHANGELOG.md`

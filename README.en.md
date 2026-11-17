# linlan-emis (BuddyOne)

> **TL;DR** · A standalone **multi-tenant Identity & Access Management (IAM/SSO)** platform on Java 17 + Spring Boot: local-account auth, GitHub App / WeChat QR federation (v1.1.0), a standard OAuth2/OIDC authorization server, tenant context, and role-based access.
> **Keywords**: IAM, SSO, OAuth2, OIDC, Multi-tenant, Spring Boot, Java, JWT
> **For AI**: [llms.txt](./llms.txt) · [AGENTS.md](./AGENTS.md)

linlan-emis (codename **BuddyOne**) is an independent, multi-tenant identity and access management middle-platform. It is the unified identity provider (IdP/SSO) for the Linlan product family — **DSH-Desktop** logs in as an OIDC public client; **dsh-java** / **linlan-xiaolan** act as OAuth2 resource servers consuming the JWT it issues.

- **Repository**: <https://github.com/linlannet/linlan-emis>
- **Port**: `8080` (works out of the box with H2)
- **License**: see repository root

---

## Version History

All version changes are recorded in [CHANGELOG](./CHANGELOG.md). This README keeps only stable architecture and usage documentation.

## Table of Contents

- [1. Features](#1-features)
- [2. Tech Stack](#2-tech-stack)
- [3. Quick Start](#3-quick-start)
  - [3.1 Requirements](#31-requirements)
  - [3.2 Build](#32-build)
  - [3.3 Run (Local / H2)](#33-run-local--h2)
  - [3.4 Run (MySQL)](#34-run-mysql)
  - [3.5 Default Accounts](#35-default-accounts)
  - [3.6 Endpoints](#36-endpoints)
- [4. Architecture](#4-architecture)
- [5. Modules](#5-modules)
- [6. Security & Isolation](#6-security--isolation)
- [7. Roadmap](#7-roadmap)

---

## 1. Features

- **Authentication**: local accounts (username/password, BCrypt); **GitHub App + WeChat QR federation** (v1.1.0, opt-in); OTP/SMS/MFA are planned.
- **Authorization**: standard **OAuth2 + OIDC** authorization server (spring-authorization-server 1.x) issuing access/refresh/id_token (JWT, JWKS-verifiable).
- **Multi-tenancy**: `tenant_id` kernel + `TenantContext` + row-level isolation.
- **Basic roles**: `role_level` (0 主任 / 1 研究员 / 2 访客) as cross-product semantics.
- **Fail-closed**: `@RequiresRole` and `RoleInterceptor` deny by default.

## 2. Tech Stack

| Item | Value |
|---|---|
| Spring Boot | 3.5.16 |
| Java | 17 |
| MyBatis | 3.0.5 |
| JWT | jjwt 0.12.6 |
| Password | spring-security-crypto (BCrypt) |
| OAuth2 AS | spring-security-oauth2-authorization-server 1.x |
| DB | MySQL (prod) / H2 (local) |

## 3. Quick Start

### 3.1 Requirements

- JDK 17+
- Maven 3.9+
- (optional) MySQL 8 for the production profile

### 3.2 Build

```bash
cd backend && mvn -q -DskipTests clean package
```

### 3.3 Run (Local / H2)

```bash
./scripts/start-local-jar.sh --build
```

Opens at http://localhost:8080 with an in-memory H2 database (zero external dependencies). H2 console: http://localhost:8080/h2-console.

### 3.4 Run (MySQL)

Configure `backend/src/main/resources/application-mysql.yml` (datasource), then:

```bash
cd backend && mvn -q -DskipTests clean package
java -jar backend/target/emis-backend-1.1.0.jar --spring.profiles.active=mysql
```

### 3.5 Default Accounts

Seeded by `DataInitializer`:

| user | password | role_level |
|---|---|---|
| admin | admin123 | 0 (主任, can delete others) |
| guest | guest123 | 2 (普通用户) |

### 3.6 Endpoints

| Method | Path | Note |
|---|---|---|
| POST | `/api/auth/login` | `{ userName, password }` -> `Result<LoginResponse>` |
| POST | `/api/auth/register` | register |
| GET | `/api/auth/me` | current user |
| DELETE | `/api/auth/user/{id}` | delete user (requires roleLevel=0) |
| GET | `/api/auth/federated/{provider}/authorize` | start federation (`github` / `wechat`; `response_mode=redirect\|json`) |
| GET | `/api/auth/federated/{provider}/bind/authorize` | bind from an authenticated session |
| GET | `/api/auth/federated/{provider}/callback` | unified callback (login vs bind decided by server-side state) |
| GET | `/api/auth/federated/bindings` | current user's bindings (masked subject) |
| DELETE | `/api/auth/federated/bindings/{bindingId}` | unbind (owner only) |
| GET | `/api/auth/federated/providers` | provider status (roleLevel=0, booleans only) |
| GET | `/h2-console` | H2 console (local only) |

#### Federation (v1.1.0)

- **GitHub**: GitHub **App** user-to-server authorization-code exchange (`ghu_` token); identity key is the numeric user ID (never login/email); no installation dependency, no organization restriction. App private key is optional, server-side only, and never blocks login.
- **WeChat**: Open Platform **website QR** login (`snsapi_login`); identity key is **unionid**; openid is only recorded as the latest value. Missing unionid is rejected with `40115` — never falls back to openid.
- **First login**: no admin approval, inserted directly; unknown users are self-provisioned into the **experience tenant (`tenant_id=2`)** and **experience role (`role_level=2`)**.
- **Conflicts**: an existing account with the same email/phone returns `40910`; users must sign in locally and bind explicitly (no silent merge).
- **Tokens**: platform JWT is the existing HS256 token (identical claims to local login); OIDC remains RS256 + `/oauth2/jwks`.
- **In-app browser**: WeChat `MicroMessenger` receives a guide page asking to open the link in a system browser, with local password login as fallback.
- **Error codes**: `50300` not configured / `40110` cancelled / `40111` invalid state / `40112` invalid code / `40113` missing subject / `40114` credentials rejected / `40115` missing unionid / `40310` not bound / `40311` disabled / `40312` bind conflict / `40313` last login method / `40314` experience tenant unavailable / `40910` email conflict / `42900` rate limited / `50200` provider error.

All secrets are injected via environment variables; repository YAML never contains them:

```bash
export EMIS_FEDERATED_GITHUB_ENABLED=true
export EMIS_FEDERATED_GITHUB_CLIENT_ID=<GitHub App client_id>
export EMIS_FEDERATED_GITHUB_CLIENT_SECRET=<GitHub App client_secret>
export EMIS_FEDERATED_GITHUB_REDIRECT_URI=http://localhost:8080/api/auth/federated/github/callback

export EMIS_FEDERATED_WECHAT_ENABLED=true
export EMIS_FEDERATED_WECHAT_APP_ID=<website app AppID>
export EMIS_FEDERATED_WECHAT_APP_SECRET=<AppSecret>
export EMIS_FEDERATED_WECHAT_REDIRECT_URI=http://localhost:8080/api/auth/federated/wechat/callback
```

> `emis.tenant.header-trust-enabled` defaults to `false`: untrusted `X-Role-Level` / `X-User-Id` / `X-Tenant-Id` headers no longer override the JWT identity; enable it only for trusted service-to-service links.

## 4. Architecture

BuddyOne is an independent Spring Boot service. Downstream products authenticate via standard OIDC: **DSH-Desktop** is an OIDC public client; **dsh-java** is reworked as an OAuth2 resource server validating BuddyOne-issued JWTs through JWKS, while keeping an `ApiKeyAuthInterceptor` for inter-service machine credentials. See `docs/1.0.0/01-architecture.md` for the deployment topology.

## 5. Modules

- `backend/` — `emis-backend` (Spring Boot app, v1.1.0)
- `test-client/` — `linlan-emis-test-client` (demo OIDC auth-code client, v1.0.0)
- `py-api/` — Python API (placeholder)
- `frontend/` — Web console (placeholder)
- `scripts/` — `package.sh`, `start-local-jar.sh`, `start-mysql-jar.sh`, `verify-oidc.sh`
- `docs/1.1.0/` — v1.1.0 design set: `spec.md`, `design.md`, `tasks.md`, `release-v1.1.0-development-notes.md`
- `docs/1.0.0/` — design set: `spec.md` (requirements), `design.md` (technical), `tasks.md` (tasks), `release-v1.0.0-development-notes.md` (release notes); existing design: `01-architecture.md`, `02-user-tenant-model.md`, `03-oauth2-authorization-server.md`, `04-permission-model.md`

## 6. Security & Isolation

- Passwords hashed with BCrypt (`spring-security-crypto`).
- JWT signed/verified via jjwt 0.12.x.
- `TenantContext` propagates `tenant_id`; `@RequiresRole` enforces `role_level` fail-closed.
- One-shot login state (TTL ≤10 min) + digest cookie; GitHub uses PKCE(S256); authorization codes are single-use.
- Federation secrets (client_secret / AppSecret) are environment-only: never persisted, logged, or returned.
- Plaintext secrets/tokens/authorization codes must never be logged; audit/log subjects are always masked.

## 7. Roadmap

- **P0** (done): minimal backend skeleton — local login + JJWT + tenant + role.
- **P1** (done, v1.1.0): GitHub App + WeChat QR federation (experience tenant/role, self-provisioning without approval); OTP/SMS remain candidates.
- **P2** (done): spring-authorization-server 1.x (standard OAuth2/OIDC + JWKS).
- **P3**: `tenant_id` hard isolation + unified permission semantics.

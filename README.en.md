# linlan-emis (BuddyOne)

> **TL;DR** · A standalone **multi-tenant Identity & Access Management (IAM/SSO)** platform on Java 17 + Spring Boot: local-account auth, a standard OAuth2/OIDC authorization server, tenant context, and role-based access.
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

- **Authentication**: local accounts (username/password, BCrypt); third-party federation (GitHub/Google/WeChat/Feishu) and OTP/SMS/MFA are planned.
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
java -jar backend/target/emis-backend-0.1.0.jar --spring.profiles.active=mysql
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
| GET | `/h2-console` | H2 console (local only) |

## 4. Architecture

BuddyOne is an independent Spring Boot service. Downstream products authenticate via standard OIDC: **DSH-Desktop** is an OIDC public client; **dsh-java** is reworked as an OAuth2 resource server validating BuddyOne-issued JWTs through JWKS, while keeping an `ApiKeyAuthInterceptor` for inter-service machine credentials. See `docs/01-architecture.md` for the deployment topology.

## 5. Modules

- `backend/` — `emis-backend` (Spring Boot app, v0.1.0)
- `test-client/` — `linlan-emis-test-client` (demo OIDC auth-code client, v0.1.0)
- `py-api/` — Python API (placeholder)
- `frontend/` — Web console (placeholder)
- `scripts/` — `package.sh`, `start-local-jar.sh`, `start-mysql-jar.sh`, `verify-oidc.sh`
- `docs/` — `01-architecture.md`, `02-user-tenant-model.md`, `03-oauth2-authorization-server.md`, `04-permission-model.md`

## 6. Security & Isolation

- Passwords hashed with BCrypt (`spring-security-crypto`).
- JWT signed/verified via jjwt 0.12.x.
- `TenantContext` propagates `tenant_id`; `@RequiresRole` enforces `role_level` fail-closed.
- Plaintext secrets/tokens must never be logged.

## 7. Roadmap

- **P0** (done): minimal backend skeleton — local login + JJWT + tenant + role.
- **P1**: third-party federation + OTP/SMS.
- **P2**: spring-authorization-server 1.x (standard OAuth2/OIDC + JWKS).
- **P3**: `tenant_id` hard isolation + unified permission semantics.

-- linlan-emis 最小化租户/权限 schema
-- 整合来源：
--   dsh-java-ontology -> onto_sys_user（tenant_id + role_level 多租户/角色层级）
--   emis-java-backend -> UserInfo 用户字段词汇（username/password/nick_name/email/phonenumber/avatar...）

CREATE TABLE IF NOT EXISTS emis_tenant (
    tenant_id   BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tenant_name VARCHAR(128) NOT NULL,
    status      CHAR(1)      NOT NULL DEFAULT '0',
    created_at  DATETIME     DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS emis_user (
    user_id     BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT,
    role_level  INT          NOT NULL DEFAULT 2 COMMENT '0=主任/1=研究员/2=访客',
    user_name   VARCHAR(64)  NOT NULL UNIQUE,
    nick_name   VARCHAR(64),
    email       VARCHAR(128),
    phonenumber VARCHAR(32),
    avatar_url  VARCHAR(255),
    password    VARCHAR(128) NOT NULL,
    status      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0=正常 1=停用',
    del_flag    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0=正常 1=删除',
    login_ip    VARCHAR(64),
    login_date  DATETIME,
    created_by  VARCHAR(64),
    created_at  DATETIME     DEFAULT NOW(),
    updated_by  VARCHAR(64),
    updated_at  DATETIME,
    remark      VARCHAR(255)
);

-- ===== Spring Authorization Server 1.x 客户端/授权记录表（H2 MySQL 模式 + 生产 MySQL 通用） =====
-- 来源：spring-security-oauth2-authorization-server 官方 schema（oauth2_registered_client / oauth2_authorization / oauth2_authorization_consent）
CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id                              VARCHAR(100) NOT NULL,
    client_id                       VARCHAR(100) NOT NULL,
    client_id_issued_at             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    client_secret                   VARCHAR(200) DEFAULT NULL,
    client_secret_expires_at        TIMESTAMP    DEFAULT NULL,
    client_name                     VARCHAR(200) NOT NULL,
    client_authentication_methods   VARCHAR(1000) NOT NULL,
    authorization_grant_types       VARCHAR(1000) NOT NULL,
    redirect_uris                   VARCHAR(1000) DEFAULT NULL,
    post_logout_redirect_uris       VARCHAR(1000) DEFAULT NULL,
    scopes                          VARCHAR(1000) NOT NULL,
    client_settings                 VARCHAR(2000) NOT NULL,
    token_settings                  VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id                              VARCHAR(100) NOT NULL,
    registered_client_id            VARCHAR(100) NOT NULL,
    principal_name                  VARCHAR(200) NOT NULL,
    authorization_grant_type        VARCHAR(100) NOT NULL,
    authorized_scopes               VARCHAR(1000) DEFAULT NULL,
    attributes                      BLOB         DEFAULT NULL,
    state                           VARCHAR(500) DEFAULT NULL,
    authorization_code_value        BLOB         DEFAULT NULL,
    authorization_code_issued_at    TIMESTAMP    DEFAULT NULL,
    authorization_code_expires_at   TIMESTAMP    DEFAULT NULL,
    authorization_code_metadata     VARCHAR(2000) DEFAULT NULL,
    access_token_value              BLOB         DEFAULT NULL,
    access_token_issued_at          TIMESTAMP    DEFAULT NULL,
    access_token_expires_at         TIMESTAMP    DEFAULT NULL,
    access_token_metadata           VARCHAR(2000) DEFAULT NULL,
    access_token_type               VARCHAR(100) DEFAULT NULL,
    access_token_scopes             VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value             BLOB         DEFAULT NULL,
    oidc_id_token_issued_at         TIMESTAMP    DEFAULT NULL,
    oidc_id_token_expires_at        TIMESTAMP    DEFAULT NULL,
    oidc_id_token_metadata          VARCHAR(2000) DEFAULT NULL,
    refresh_token_value             BLOB         DEFAULT NULL,
    refresh_token_issued_at         TIMESTAMP    DEFAULT NULL,
    refresh_token_expires_at        TIMESTAMP    DEFAULT NULL,
    refresh_token_metadata          VARCHAR(2000) DEFAULT NULL,
    user_code_value                 VARCHAR(200) DEFAULT NULL,
    user_code_issued_at             TIMESTAMP    DEFAULT NULL,
    user_code_expires_at            TIMESTAMP    DEFAULT NULL,
    user_code_metadata              VARCHAR(2000) DEFAULT NULL,
    device_code_value               BLOB         DEFAULT NULL,
    device_code_issued_at           TIMESTAMP    DEFAULT NULL,
    device_code_expires_at          TIMESTAMP    DEFAULT NULL,
    device_code_metadata            VARCHAR(2000) DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id            VARCHAR(100) NOT NULL,
    principal_name                  VARCHAR(200) NOT NULL,
    authorities                     VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

CREATE INDEX ix_oauth2_authorization_rpid_pn
    ON oauth2_authorization (registered_client_id, principal_name);

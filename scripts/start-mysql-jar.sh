#!/usr/bin/env bash
# 启动 linlan-emis 后端（MySQL 生产模式，需本地/可达 MySQL 实例）
#
# 对应 Spring profile：默认（application.yml，MySQL 数据源）
#   库名   emisdb_2.0
#   用户   lldba
#   密码   DB_Lldba_2023!
#   URL    jdbc:mysql://127.0.0.1:3306/emisdb_2.0
#
# 前置条件：
#   1) 已安装并启动 MySQL（本机 3306，或调整 application.yml 中的 url）
#   2) 已创建数据库 emisdb_2.0，并存在用户 lldba / 密码 DB_Lldba_2023!
#   3) 首次启动会自动执行 schema.sql 建表并播种默认账号（admin / guest）
#
# 用法：
#   ./start-mysql-jar.sh               直接运行已有 jar（backend/target/*.jar）
#   ./start-mysql-jar.sh --build       先 mvn 打包再运行
#   JAVA_OPTS="-Xmx1g" ./start-mysql-jar.sh --build   打包并通过环境变量追加 JVM 参数
#   其余位置参数作为 Spring Boot 程序参数，如 ./start-mysql-jar.sh --server.port=9090
#
# 启动后：
#   地址          http://localhost:8080
#   登录接口      POST /api/auth/login   { userName, password }  ->  Result<LoginResponse>
#   当前用户      GET  /api/auth/me
#   注销用户      DELETE /api/auth/user/{id}   （需 roleLevel=0，即 admin）
#
# 默认账号（DataInitializer 播种）：
#   admin / admin123   roleLevel=0（主任，可注销他人）
#   guest / guest123   roleLevel=2（普通用户）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 脚本位于 scripts/，后端在仓库根的 backend/ 目录
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)/backend"
cd "$BACKEND_DIR"

BUILD=0
REST=()
for a in "$@"; do
  case "$a" in
    --build|-b) BUILD=1 ;;
    *) REST+=("$a") ;;
  esac
done

JAR="$(ls target/*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)"
if [[ "$BUILD" -eq 1 || -z "$JAR" ]]; then
  echo "== 构建 linlan-emis-backend =="
  mvn -q -DskipTests clean package
  JAR="$(ls target/*.jar 2>/dev/null | grep -v '\.original$' | head -1)"
fi

if [[ -z "$JAR" ]]; then
  echo "未找到可运行的 jar，请先执行 ./start-mysql-jar.sh --build" >&2
  exit 1
fi

# JVM 参数通过环境变量传入，例如：JAVA_OPTS="-Xmx1g" ./start-mysql-jar.sh
# 其余参数作为 Spring Boot 程序参数（如 --server.port=9090）
if [[ ${#REST[@]} -gt 0 ]]; then
  set -- "${REST[@]}"
fi

echo "== 启动 linlan-emis-backend (MySQL 模式) jar: $JAR =="
echo "   profile=default(MySQL) | 端口 8080 | 数据源 application.yml"
echo "   前置：MySQL 已启动，库 emisdb_2.0 / 用户 lldba 就绪"
echo "   默认账号 admin/admin123(roleLevel=0)  guest/guest123(roleLevel=2)"
echo "   Ctrl+C 停止"
exec java ${JAVA_OPTS:-} -jar "$JAR" "$@"

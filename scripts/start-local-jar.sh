#!/usr/bin/env bash
# 启动 linlan-emis 后端（本地模式 / H2 内存库，零外部依赖即可跑）
#
# 对应 Spring profile：standalone（application-standalone.yml，H2 内存库）
#   —— 不需要任何外部数据库，开箱即用，适合本地联调与 dsh-java-app 登录验证。
#
# 用法：
#   ./start-local-jar.sh               直接运行已有 jar（backend/target/*.jar）
#   ./start-local-jar.sh --build       先 mvn 打包再运行（源码变更后使用，如安全/CSRF 调整）
#   JAVA_OPTS="-Xmx512m" ./start-local-jar.sh --build   打包并通过环境变量追加 JVM 参数
#   其余位置参数作为 Spring Boot 程序参数，如 ./start-local-jar.sh --server.port=9090
#
# 启动后：
#   地址          http://localhost:8080
#   登录接口      POST /api/auth/login   { userName, password }  ->  Result<LoginResponse>
#   注册接口      POST /api/auth/register
#   当前用户      GET  /api/auth/me
#   注销用户      DELETE /api/auth/user/{id}   （需 roleLevel=0，即 admin）
#   H2 控制台     http://localhost:8080/h2-console
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
  echo "未找到可运行的 jar，请先执行 ./start-local-jar.sh --build" >&2
  exit 1
fi

# JVM 参数通过环境变量传入，例如：JAVA_OPTS="-Xmx512m" ./start-local-jar.sh
# 其余参数作为 Spring Boot 程序参数（如 --server.port=9090）
if [[ ${#REST[@]} -gt 0 ]]; then
  set -- "${REST[@]}"
fi

echo "== 启动 linlan-emis-backend (本地/H2 模式) jar: $JAR =="
echo "   profile=standalone | 端口 8080 | 数据库 H2 内存 | 登录 POST /api/auth/login"
echo "   默认账号 admin/admin123(roleLevel=0)  guest/guest123(roleLevel=2)"
echo "   Ctrl+C 停止"
exec java ${JAVA_OPTS:-} -jar "$JAR" --spring.profiles.active=standalone "$@"

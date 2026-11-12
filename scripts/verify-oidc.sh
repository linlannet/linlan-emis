#!/usr/bin/env bash
# 验证 linlan-emis(OAuth2 AS) + test-client(标准 OIDC 客户端) 的授权码登录全流程，
# 并解码 id_token / access_token 确认 tenant_id / role_level / user_name 已注入。
#
# 前置：
#   - backend(:8080) 已启动。未启动时可加 --start，本脚本会用 scripts/start-local-jar.sh
#     以 standalone(H2) 模式拉起本地后端（零外部依赖）。
#   - test-client(:8081) 已启动（标准 OIDC 客户端，独立 maven 模块，需自行启动）。
#
# 用法：
#   ./verify-oidc.sh           假定 backend / test-client 已在运行，直接校验
#   ./verify-oidc.sh --start   若 backend 未启动，则自动以本地 H2 模式拉起
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BK=http://localhost:8080
CL=http://localhost:8081

AUTO_START=0
for a in "$@"; do
  case "$a" in
    --start) AUTO_START=1 ;;
    -h|--help)
      sed -n '2,18p' "$0"; exit 0 ;;
    *) echo "Unknown option: $a" >&2; exit 1 ;;
  esac
done

loc(){ grep -i '^[Ll]ocation:' | tr -d '\r' | awk '{print $2}'; }

# 若 backend 未就绪，按需要自动拉起（standalone / H2）
if ! curl -s -o /dev/null "$BK/.well-known/openid-configuration"; then
  if [[ "$AUTO_START" -eq 1 ]]; then
    echo "== backend 未启动，使用 start-local-jar.sh 拉起本地 H2 后端 =="
    nohup "$SCRIPT_DIR/start-local-jar.sh" > /tmp/emis-verify.log 2>&1 &
    for i in $(seq 1 60); do
      sleep 1
      if curl -s -o /dev/null "$BK/.well-known/openid-configuration"; then
        echo "backend 就绪（${i}s）"; break
      fi
    done
  else
    echo "backend($BK) 未就绪。请先启动 backend（如 ./start-local-jar.sh），或加 --start 自动拉起。" >&2
    exit 1
  fi
fi

rm -f /tmp/v_client.jar /tmp/v_back.jar

echo "== 1) OIDC Discovery =="
curl -s -o /dev/null -w "discovery http=%{http_code}\n" "$BK/.well-known/openid-configuration"
echo "== 2) JWKS =="
curl -s "$BK/oauth2/jwks" | python3 -c "import sys,json;print('jwks keys=',len(json.load(sys.stdin).get('keys',[])))"

echo "== 3) 客户端发起授权 -> 跳转到 backend authorize =="
AUTH=$(curl -s -c /tmp/v_client.jar -o /dev/null -D - "$CL/oauth2/authorization/buddyone" | loc)
echo "authorize_url=${AUTH:0:60}..."
LOGIN=$(curl -s -c /tmp/v_back.jar -b /tmp/v_back.jar -o /dev/null -D - "$AUTH" | loc)
echo "未登录时 authorize 跳转: $LOGIN"

echo "== 4) 表单登录(admin/admin123) =="
CSRF=$(curl -s -c /tmp/v_back.jar -b /tmp/v_back.jar "$BK/login" | sed -n 's/.*name="_csrf" type="hidden" value="\([^"]*\)".*/\1/p' | head -1)
curl -s -c /tmp/v_back.jar -b /tmp/v_back.jar -o /dev/null -D - -d "username=admin&password=admin123&_csrf=$CSRF" "$BK/login" >/dev/null
FINAL=$(curl -s -c /tmp/v_back.jar -b /tmp/v_back.jar -o /dev/null -D - "$AUTH" | loc)
CODE=$(echo "$FINAL" | sed -n 's/.*code=\([^&]*\).*/\1/p')
echo "拿到授权码 code_len=${#CODE}"

echo "== 5) 用 code 换 token =="
curl -s -u "test-client:test-secret" --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=$CODE" --data-urlencode "redirect_uri=$CL/login/oauth2/code/buddyone" \
  "$BK/oauth2/token" -o /tmp/v_tokens.json
python3 -c "import json;d=json.load(open('/tmp/v_tokens.json'));print('token keys=',list(d.keys()))" 2>/dev/null

echo "== 6) 解码 id_token / access_token 的自定义 claim =="
python3 - <<'PY'
import json,base64
d=json.load(open('/tmp/v_tokens.json'))
def dec(tok):
    s=tok.split('.')[1]; s+='='*(-len(s)%4)
    return json.loads(base64.urlsafe_b64decode(s))
for name in ('access_token','id_token'):
    t=d.get(name)
    if not t: continue
    print(f"--- {name} claims ---")
    print(json.dumps(dec(t),indent=2,ensure_ascii=False))
PY
echo "== 完成 =="

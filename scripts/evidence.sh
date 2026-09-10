#!/usr/bin/env bash
#
# Runs the functional test matrix of section 6.1 against a running server and
# prints the status and content type of every response. It works exactly the
# same against the local server and against the EC2 instance:
#
#   ./scripts/evidence.sh http://localhost:8080 | tee docs/evidence/local.txt
#   ./scripts/evidence.sh http://<public address>:8080 | tee docs/evidence/ec2.txt

set -uo pipefail

BASE="${1:-http://localhost:8080}"

printf 'Functional evidence for %s\n' "$BASE"
printf 'Generated on %s\n\n' "$(date -Is)"
printf '%-6s %-38s %-34s %10s  %s\n' "STATUS" "REQUEST" "CONTENT-TYPE" "BYTES" "CHECK"
printf '%s\n' "--------------------------------------------------------------------------------------------------------"

# $1 expected status, $2 method, $3 path
probe() {
  local expected="$1" method="$2" path="$3"
  local out status type bytes verdict

  out="$(curl -s -o /dev/null -X "$method" \
        -w '%{http_code}|%{content_type}|%{size_download}' \
        --max-time 30 "${BASE}${path}" 2>/dev/null)"

  status="$(cut -d'|' -f1 <<<"$out")"
  type="$(cut -d'|' -f2 <<<"$out")"
  bytes="$(cut -d'|' -f3 <<<"$out")"

  if [[ "$status" == "$expected" ]]; then verdict="ok"; else verdict="UNEXPECTED (wanted $expected)"; fi
  printf '%-6s %-38s %-34s %10s  %s\n' "$status" "$method $path" "${type:-none}" "${bytes:-0}" "$verdict"
}

echo "# Static resources"
probe 200 GET "/"
probe 200 GET "/index.html"
probe 200 GET "/css/styles.css"
probe 200 GET "/js/app.js"
probe 200 GET "/images/logo.png"
probe 200 GET "/images/cloud.jpg"

echo
echo "# Hardcoded services -- valid input"
probe 200 GET "/app/hello?name=Ada%20Lovelace"
probe 200 GET "/app/square?value=7"
probe 200 GET "/app/square?value=-2.5"
probe 200 GET "/app/time"
probe 200 GET "/app/health"

echo
echo "# Hardcoded services -- invalid input"
probe 400 GET "/app/hello"
probe 400 GET "/app/hello?name="
probe 400 GET "/app/square"
probe 400 GET "/app/square?value=abc"
probe 400 GET "/app/slow?ms=600000"
probe 404 GET "/app/unknown-service"

echo
echo "# Controlled errors"
probe 404 GET "/images/missing.png"
probe 404 GET "/not-here.html"
probe 405 POST "/app/hello?name=Ada"
probe 405 PUT "/index.html"
probe 405 DELETE "/app/health"

echo
echo "# Path traversal (curl normalizes the URL, so the raw path is sent by hand)"
for raw in "/../../../../etc/passwd" "/%2e%2e/%2e%2e/etc/passwd" "/images/../../pom.xml"; do
  status="$(curl -s -o /dev/null -w '%{http_code}' --path-as-is --max-time 10 "${BASE}${raw}")"
  if [[ "$status" == "403" ]]; then verdict="ok, rejected"; else verdict="UNEXPECTED (wanted 403)"; fi
  printf '%-6s %-38s %-34s %10s  %s\n' "$status" "GET $raw" "application/json" "-" "$verdict"
done

echo
echo "# Repeated requests: fifteen consecutive operations in one server run"
failures=0
for i in $(seq 1 15); do
  status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "${BASE}/app/square?value=${i}")"
  [[ "$status" == "200" ]] || failures=$((failures + 1))
done
if [[ $failures -eq 0 ]]; then
  echo "15/15 succeeded against the same running process."
else
  echo "${failures} of 15 failed."
fi

echo
echo "# Sample payloads"
for path in "/app/hello?name=Ada" "/app/square?value=12" "/app/time" "/app/health" "/app/square?value=abc"; do
  printf '\nGET %s\n' "$path"
  curl -s -D - -o - --max-time 30 "${BASE}${path}" | sed -n '1p;/^Content-Type/p;/^Content-Length/p;$p'
done

echo
echo "Done."

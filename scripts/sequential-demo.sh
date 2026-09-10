#!/usr/bin/env bash
#
# Makes the sequential limitation measurable, which is what section 6.2 asks
# for. Two clients act at almost the same time:
#
#   client A asks for a deliberately slow service (5 s)
#   client B asks, half a second later, for a service that costs nothing
#
# On a concurrent server B would answer in a few milliseconds. Here B waits for
# A to finish, because the single server thread is still inside A's request and
# has not returned to accept() yet.
#
#   ./scripts/sequential-demo.sh http://localhost:8080

set -uo pipefail

BASE="${1:-http://localhost:8080}"
SLOW_MILLIS="${SLOW_MILLIS:-5000}"

stamp() { date +%H:%M:%S.%3N; }
now()   { date +%s%3N; }

printf 'Sequential limitation demo against %s\n' "$BASE"
printf 'Generated on %s\n\n' "$(date -Is)"

start_all=$(now)

printf '%s  client A  ->  GET /app/slow?ms=%s\n' "$(stamp)" "$SLOW_MILLIS"
(
  a0=$(now)
  curl -s -o /dev/null --max-time 60 "${BASE}/app/slow?ms=${SLOW_MILLIS}"
  a1=$(now)
  printf '%s  client A  <-  answered after %s ms\n' "$(stamp)" "$((a1 - a0))"
) &
slow_pid=$!

# Half a second later the second client asks for something trivially cheap.
sleep 0.5

printf '%s  client B  ->  GET /app/time   (a request that costs nothing)\n' "$(stamp)"
b0=$(now)
curl -s -o /dev/null --max-time 60 "${BASE}/app/time"
b1=$(now)
waited=$((b1 - b0))
printf '%s  client B  <-  answered after %s ms\n' "$(stamp)" "$waited"

wait "$slow_pid"

printf '\nTotal wall time: %s ms\n\n' "$(( $(now) - start_all ))"

if (( waited > 1000 )); then
  cat <<EOF
Interpretation
--------------
Client B needed ${waited} ms for a request the server answers in about one
millisecond when it is idle. Nothing was slow about B: it simply waited in the
accept queue while the single thread was still serving A.

The browser stays responsive because fetch() is asynchronous on the client
side, but asynchronous is not concurrent: the server still processes one
connection at a time.
EOF
else
  cat <<EOF
Interpretation
--------------
Client B answered in ${waited} ms, so it did not wait. Check that the slow
request was really running (the server log should show /app/slow) and that both
clients are talking to the same process.
EOF
fi

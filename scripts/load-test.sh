#!/bin/bash
set -e

BASE="http://localhost:8080/api/v1"
CONCURRENCY=${1:-50}
TOTAL_REQUESTS=${2:-500}

echo "============================================"
echo "  PulseQ Load Test"
echo "  Concurrency: $CONCURRENCY"
echo "  Total Requests: $TOTAL_REQUESTS"
echo "  $(date '+%Y-%m-%d %H:%M:%S KST')"
echo "============================================"

# 1. Login
echo -e "\n[SETUP] Logging in..."
LOGIN=$(curl -s -X POST "$BASE/tenants/login" \
  -H 'Content-Type: application/json' \
  --data-raw '{"email":"kafka-p3@test.io","password":"KafkaTest2026"}')
TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null)
AUTH="Authorization: Bearer $TOKEN"

# 2. Create test event
echo "[SETUP] Creating load test event..."
SLUG="loadtest-$(date +%s)"
EVENT=$(curl -s -X POST "$BASE/events" \
  -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Load Test\",\"slug\":\"$SLUG\",\"maxCapacity\":100000,\"rateLimit\":1000,\"startAt\":\"2026-01-01T00:00:00Z\",\"endAt\":\"2027-01-01T00:00:00Z\",\"botDetectionEnabled\":false}")
EVENT_ID=$(echo "$EVENT" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(d.get('eventId','') or d.get('id',''))" 2>/dev/null)
echo "  Event ID: $EVENT_ID"

# 3. Activate event
docker exec pulseq-postgres psql -U pulseq -d pulseq -c "UPDATE events SET status='ACTIVE' WHERE id='$EVENT_ID';" > /dev/null 2>&1
echo "  Event activated"

# ============================================
# Test 1: Queue Enter (POST) — throughput
# ============================================
echo -e "\n============================================"
echo "  Test 1: POST /queues/{id}/enter"
echo "  $TOTAL_REQUESTS requests, $CONCURRENCY concurrent"
echo "============================================"

TMPDIR=$(mktemp -d)
RESULTS_FILE="$TMPDIR/results.txt"

enter_request() {
  local i=$1
  local start_ms=$(python3 -c "import time; print(int(time.time()*1000))")
  local HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE/queues/$EVENT_ID/enter" \
    -H "$AUTH" \
    -H 'Content-Type: application/json' \
    -d "{\"userId\":\"loadtest_user_$i\",\"metadata\":{\"ip\":\"203.0.113.$((i % 254 + 1))\",\"userAgent\":\"Mozilla/5.0 LoadTest\"}}")
  local end_ms=$(python3 -c "import time; print(int(time.time()*1000))")
  local latency=$((end_ms - start_ms))
  echo "$latency $HTTP_CODE" >> "$RESULTS_FILE"
}

export -f enter_request
export BASE EVENT_ID AUTH RESULTS_FILE

START_TIME=$(python3 -c "import time; print(time.time())")

# Run concurrent requests
seq 1 $TOTAL_REQUESTS | xargs -P $CONCURRENCY -I {} bash -c 'enter_request {}'

END_TIME=$(python3 -c "import time; print(time.time())")
ELAPSED=$(python3 -c "print(round($END_TIME - $START_TIME, 2))")

# Calculate stats
python3 << PYEOF
import sys

latencies = []
status_codes = {}

with open("$RESULTS_FILE") as f:
    for line in f:
        parts = line.strip().split()
        if len(parts) == 2:
            latencies.append(int(parts[0]))
            code = parts[1]
            status_codes[code] = status_codes.get(code, 0) + 1

if not latencies:
    print("  No results collected!")
    sys.exit(1)

latencies.sort()
total = len(latencies)
avg = sum(latencies) / total
p50 = latencies[int(total * 0.5)]
p90 = latencies[int(total * 0.9)]
p95 = latencies[int(total * 0.95)]
p99 = latencies[min(int(total * 0.99), total - 1)]
min_l = latencies[0]
max_l = latencies[-1]
tps = round(total / $ELAPSED, 1)

print(f"\n  Results:")
print(f"  ────────────────────────────────")
print(f"  Total Requests : {total}")
print(f"  Elapsed Time   : {$ELAPSED}s")
print(f"  Throughput     : {tps} req/s")
print(f"  ────────────────────────────────")
print(f"  Latency (ms):")
print(f"    min  : {min_l}")
print(f"    avg  : {round(avg, 1)}")
print(f"    p50  : {p50}")
print(f"    p90  : {p90}")
print(f"    p95  : {p95}")
print(f"    p99  : {p99}")
print(f"    max  : {max_l}")
print(f"  ────────────────────────────────")
print(f"  Status Codes:")
for code, count in sorted(status_codes.items()):
    pct = round(count / total * 100, 1)
    print(f"    {code}: {count} ({pct}%)")
print(f"  ────────────────────────────────")
PYEOF

# ============================================
# Test 2: Queue Status (GET) — read throughput
# ============================================
echo -e "\n============================================"
echo "  Test 2: GET /queues/{id}/status"
echo "  $TOTAL_REQUESTS requests, $CONCURRENCY concurrent"
echo "============================================"

RESULTS_FILE2="$TMPDIR/results2.txt"

status_request() {
  local start_ms=$(python3 -c "import time; print(int(time.time()*1000))")
  local HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    "$BASE/queues/$EVENT_ID/status" \
    -H "$AUTH")
  local end_ms=$(python3 -c "import time; print(int(time.time()*1000))")
  local latency=$((end_ms - start_ms))
  echo "$latency $HTTP_CODE" >> "$RESULTS_FILE2"
}

export -f status_request
export RESULTS_FILE2

START_TIME2=$(python3 -c "import time; print(time.time())")
seq 1 $TOTAL_REQUESTS | xargs -P $CONCURRENCY -I {} bash -c 'status_request'
END_TIME2=$(python3 -c "import time; print(time.time())")
ELAPSED2=$(python3 -c "print(round($END_TIME2 - $START_TIME2, 2))")

python3 << PYEOF
latencies = []
status_codes = {}
with open("$RESULTS_FILE2") as f:
    for line in f:
        parts = line.strip().split()
        if len(parts) == 2:
            latencies.append(int(parts[0]))
            code = parts[1]
            status_codes[code] = status_codes.get(code, 0) + 1

if not latencies:
    print("  No results!")
    import sys; sys.exit(1)

latencies.sort()
total = len(latencies)
avg = sum(latencies) / total
p50 = latencies[int(total * 0.5)]
p90 = latencies[int(total * 0.9)]
p95 = latencies[int(total * 0.95)]
p99 = latencies[min(int(total * 0.99), total - 1)]
tps = round(total / $ELAPSED2, 1)

print(f"\n  Results:")
print(f"  ────────────────────────────────")
print(f"  Total Requests : {total}")
print(f"  Elapsed Time   : {$ELAPSED2}s")
print(f"  Throughput     : {tps} req/s")
print(f"  ────────────────────────────────")
print(f"  Latency (ms):")
print(f"    min  : {latencies[0]}")
print(f"    avg  : {round(avg, 1)}")
print(f"    p50  : {p50}")
print(f"    p90  : {p90}")
print(f"    p95  : {p95}")
print(f"    p99  : {p99}")
print(f"    max  : {latencies[-1]}")
print(f"  ────────────────────────────────")
print(f"  Status Codes:")
for code, count in sorted(status_codes.items()):
    pct = round(count / total * 100, 1)
    print(f"    {code}: {count} ({pct}%)")
PYEOF

# ============================================
# Test 3: Bot Detection — overhead measurement
# ============================================
echo -e "\n============================================"
echo "  Test 3: Bot Detection overhead"
echo "  100 requests with botDetection=true"
echo "============================================"

BOT_SLUG="botload-$(date +%s)"
BOT_EVENT=$(curl -s -X POST "$BASE/events" \
  -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Bot Load Test\",\"slug\":\"$BOT_SLUG\",\"maxCapacity\":100000,\"rateLimit\":1000,\"startAt\":\"2026-01-01T00:00:00Z\",\"endAt\":\"2027-01-01T00:00:00Z\",\"botDetectionEnabled\":true,\"botScoreThreshold\":0.8}")
BOT_EVENT_ID=$(echo "$BOT_EVENT" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(d.get('eventId','') or d.get('id',''))" 2>/dev/null)
docker exec pulseq-postgres psql -U pulseq -d pulseq -c "UPDATE events SET status='ACTIVE' WHERE id='$BOT_EVENT_ID';" > /dev/null 2>&1

RESULTS_FILE3="$TMPDIR/results3.txt"
export BOT_EVENT_ID RESULTS_FILE3

bot_enter_request() {
  local i=$1
  local start_ms=$(python3 -c "import time; print(int(time.time()*1000))")
  local HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE/queues/$BOT_EVENT_ID/enter" \
    -H "$AUTH" \
    -H 'Content-Type: application/json' \
    -d "{\"userId\":\"bot_load_$i\",\"metadata\":{\"ip\":\"203.0.113.$((i % 254 + 1))\",\"userAgent\":\"Mozilla/5.0 LoadTest\"}}")
  local end_ms=$(python3 -c "import time; print(int(time.time()*1000))")
  local latency=$((end_ms - start_ms))
  echo "$latency $HTTP_CODE" >> "$RESULTS_FILE3"
}

export -f bot_enter_request

START_TIME3=$(python3 -c "import time; print(time.time())")
seq 1 100 | xargs -P 20 -I {} bash -c 'bot_enter_request {}'
END_TIME3=$(python3 -c "import time; print(time.time())")
ELAPSED3=$(python3 -c "print(round($END_TIME3 - $START_TIME3, 2))")

python3 << PYEOF
latencies = []
with open("$RESULTS_FILE3") as f:
    for line in f:
        parts = line.strip().split()
        if len(parts) == 2:
            latencies.append(int(parts[0]))

if not latencies:
    print("  No results!")
    import sys; sys.exit(1)

latencies.sort()
total = len(latencies)
avg = sum(latencies) / total
p50 = latencies[int(total * 0.5)]
p95 = latencies[int(total * 0.95)]
p99 = latencies[min(int(total * 0.99), total - 1)]
tps = round(total / $ELAPSED3, 1)

print(f"\n  Results (with ML bot detection):")
print(f"  ────────────────────────────────")
print(f"  Throughput     : {tps} req/s")
print(f"  Latency: avg={round(avg,1)}ms, p50={p50}ms, p95={p95}ms, p99={p99}ms")
print(f"  ────────────────────────────────")
PYEOF

# Cleanup
rm -r "$TMPDIR" 2>/dev/null

echo -e "\n============================================"
echo "  LOAD TEST COMPLETE"
echo "  $(date '+%Y-%m-%d %H:%M:%S KST')"
echo "============================================"

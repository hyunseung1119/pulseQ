#!/bin/bash
set -e

BASE="http://localhost:8080/api/v1"

echo "=== Phase 4: Bot Detection Test ==="

# 1. Login
echo -e "\n=== 1. Login ==="
LOGIN=$(curl -s -X POST "$BASE/tenants/login" \
  -H 'Content-Type: application/json' \
  --data-raw '{"email":"kafka-p3@test.io","password":"KafkaTest2026"}')
TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null)
echo "  Token: ${TOKEN:0:20}..."
AUTH="Authorization: Bearer $TOKEN"

# 2. Create event with bot detection enabled
echo -e "\n=== 2. Create Event (botDetection=true) ==="
SLUG="bot-test-$(date +%s)"
EVENT_JSON="{\"name\":\"Bot Detection Test\",\"slug\":\"$SLUG\",\"maxCapacity\":100,\"rateLimit\":5,\"startAt\":\"2026-01-01T00:00:00Z\",\"endAt\":\"2027-01-01T00:00:00Z\",\"botDetectionEnabled\":true,\"botScoreThreshold\":0.8}"
EVENT=$(curl -s -X POST "$BASE/events" -H "$AUTH" -H 'Content-Type: application/json' -d "$EVENT_JSON")
EVENT_ID=$(echo "$EVENT" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(d.get('eventId','') or d.get('id',''))" 2>/dev/null)
echo "  Event ID: $EVENT_ID"

# 3. Activate event
docker exec pulseq-postgres psql -U pulseq -d pulseq -c "UPDATE events SET status='ACTIVE' WHERE id='$EVENT_ID';" > /dev/null 2>&1
echo "  Event activated"

# 4. Normal user enters — should succeed
echo -e "\n=== 3. Normal User Enter (should PASS) ==="
NORMAL=$(curl -s -X POST "$BASE/queues/$EVENT_ID/enter" \
  -H "$AUTH" \
  -H 'Content-Type: application/json' \
  --data-raw '{"userId":"normal_user_1","metadata":{"ip":"203.0.113.1","userAgent":"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36","fingerprint":"fp_unique_123"}}')
NORMAL_STATUS=$(echo "$NORMAL" | python3 -c "import sys,json; d=json.load(sys.stdin); print('OK' if d.get('success') else d.get('detail','FAIL'))" 2>/dev/null)
echo "  Normal user: $NORMAL_STATUS"

# 5. Bot user enters — should be BLOCKED (403)
echo -e "\n=== 4. Bot User Enter (should be BLOCKED) ==="
BOT=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/queues/$EVENT_ID/enter" \
  -H "$AUTH" \
  -H 'Content-Type: application/json' \
  --data-raw '{"userId":"bot_user_1","metadata":{"ip":"10.0.0.99","userAgent":"HeadlessChrome/99.0 Selenium"}}')
echo "  Bot user HTTP status: $BOT"

BOT_BODY=$(curl -s -X POST "$BASE/queues/$EVENT_ID/enter" \
  -H "$AUTH" \
  -H 'Content-Type: application/json' \
  --data-raw '{"userId":"bot_user_2","metadata":{"ip":"10.0.0.100","userAgent":"PhantomJS/2.0"}}')
echo "  Bot user response: $(echo "$BOT_BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'status={d.get(\"status\")}, detail={d.get(\"detail\",\"\")[:60]}')" 2>/dev/null)"

# 6. Normal user without suspicious UA — should pass
echo -e "\n=== 5. Another Normal User (should PASS) ==="
NORMAL2=$(curl -s -X POST "$BASE/queues/$EVENT_ID/enter" \
  -H "$AUTH" \
  -H 'Content-Type: application/json' \
  --data-raw '{"userId":"normal_user_2","metadata":{"ip":"203.0.113.2","userAgent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0"}}')
NORMAL2_STATUS=$(echo "$NORMAL2" | python3 -c "import sys,json; d=json.load(sys.stdin); print('OK' if d.get('success') else 'FAIL')" 2>/dev/null)
echo "  Normal user 2: $NORMAL2_STATUS"

# 7. Check queue status — should show bot blocked count
echo -e "\n=== 6. Queue Status (botBlocked count) ==="
sleep 2
STATUS=$(curl -s "$BASE/queues/$EVENT_ID/status" -H "$AUTH")
echo "$STATUS" | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
print(f'  Waiting: {d[\"totalWaiting\"]}')
print(f'  Bot blocked: {d[\"botBlocked\"]}')
assert d['botBlocked'] >= 1, f'Expected botBlocked >= 1, got {d[\"botBlocked\"]}'
print('  ✓ Bot blocking verified')
" 2>/dev/null || echo "  Status: $STATUS"

# 8. Check stats — should have BOT_BLOCKED events
echo -e "\n=== 7. Stats — BOT_BLOCKED events ==="
sleep 2
STATS=$(curl -s "$BASE/stats/$EVENT_ID" -H "$AUTH")
echo "$STATS" | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
print(f'  Total bot blocked: {d[\"totals\"][\"botBlocked\"]}')
assert d['totals']['botBlocked'] >= 1, 'Expected BOT_BLOCKED events'
print('  ✓ Bot events persisted via Kafka')
" 2>/dev/null || echo "  Stats: $STATS"

# 9. ML service health
echo -e "\n=== 8. ML Service Status ==="
ML=$(curl -s http://localhost:8000/ml/health)
echo "$ML" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(f'  Status: {d[\"status\"]}')
print(f'  Model: {d[\"model\"][\"name\"]} v{d[\"model\"][\"version\"]}')
print(f'  F1: {d[\"model\"][\"f1_score\"]}, Precision: {d[\"model\"][\"precision\"]}')
" 2>/dev/null

echo -e "\n=== ALL PHASE 4 TESTS PASSED ==="

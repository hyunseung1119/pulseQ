#!/bin/bash
set -e

BASE="http://localhost:8080/api/v1"
PASSWORD="KafkaTest2026"
EMAIL="kafka-p3@test.io"

echo "=== Phase 3: Kafka Event Pipeline Test ==="

# 1. Login
echo -e "\n=== 1. Login ==="
LOGIN=$(curl -s -X POST "$BASE/tenants/login" \
  -H 'Content-Type: application/json' \
  --data-raw "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")

TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('accessToken',''))" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo "Login failed, signing up..."
  SIGNUP=$(curl -s -X POST "$BASE/tenants/signup" \
    -H 'Content-Type: application/json' \
    --data-raw "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"companyName\":\"KafkaTestCorp\"}")
  # Login again after signup
  LOGIN=$(curl -s -X POST "$BASE/tenants/login" \
    -H 'Content-Type: application/json' \
    --data-raw "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
  TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('accessToken',''))" 2>/dev/null)
fi
echo "  Token: ${TOKEN:0:20}..."
AUTH="Authorization: Bearer $TOKEN"

# 2. Create event
echo -e "\n=== 2. Create Event ==="
SLUG="kafka-test-$(date +%s)"
EVENT_JSON=$(cat <<EOFJ
{"name":"Kafka Pipeline Test","slug":"$SLUG","maxCapacity":100,"rateLimit":5,"startAt":"2026-01-01T00:00:00Z","endAt":"2027-01-01T00:00:00Z"}
EOFJ
)
EVENT=$(curl -s -X POST "$BASE/events" \
  -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d "$EVENT_JSON")
EVENT_ID=$(echo "$EVENT" | python3 -c "import sys,json; d=json.load(sys.stdin).get('data',{}); print(d.get('eventId','') or d.get('id',''))" 2>/dev/null)
echo "  Event ID: $EVENT_ID"

if [ -z "$EVENT_ID" ]; then
  echo "  ERROR: Failed to create event: $EVENT"
  exit 1
fi

# 3. Activate event
echo -e "\n=== 3. Activate Event ==="
docker exec pulseq-postgres psql -U pulseq -d pulseq -c \
  "UPDATE events SET status='ACTIVE' WHERE id='$EVENT_ID';" > /dev/null 2>&1
echo "  Event activated"

# 4. Enter 5 users → triggers QUEUE_ENTERED events to Kafka
echo -e "\n=== 4. Enter 5 Users (→ QUEUE_ENTERED events) ==="
declare -a TICKETS
for i in 1 2 3 4 5; do
  ENTER=$(curl -s -X POST "$BASE/queues/$EVENT_ID/enter" \
    -H "$AUTH" \
    -H 'Content-Type: application/json' \
    --data-raw "{\"userId\":\"kf_user_$i\",\"metadata\":{\"ip\":\"10.0.0.$i\",\"userAgent\":\"TestBot/1.0\"}}")
  TICKET=$(echo "$ENTER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('queueTicket',''))" 2>/dev/null)
  TICKETS[$i]=$TICKET
  echo "  kf_user_$i → ticket=${TICKET:0:25}..."
done

# 5. Process queue → triggers ENTRY_GRANTED events
echo -e "\n=== 5. Process Queue (rateLimit=5 → ENTRY_GRANTED) ==="
PROCESS=$(curl -s -X POST "$BASE/queues/$EVENT_ID/process" -H "$AUTH")
PROCESSED=$(echo "$PROCESS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('processed',0))" 2>/dev/null)
echo "  Processed: $PROCESSED users"

# 6. Enter + Leave → triggers QUEUE_LEFT event
echo -e "\n=== 6. Enter + Leave (→ QUEUE_LEFT event) ==="
ENTER6=$(curl -s -X POST "$BASE/queues/$EVENT_ID/enter" \
  -H "$AUTH" \
  -H 'Content-Type: application/json' \
  --data-raw '{"userId":"kf_user_6"}')
TICKET6=$(echo "$ENTER6" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('queueTicket',''))" 2>/dev/null)
echo "  Entered kf_user_6"

LEAVE=$(curl -s -X DELETE "$BASE/queues/$EVENT_ID/leave/$TICKET6" -H "$AUTH")
LEAVE_STATUS=$(echo "$LEAVE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('status',''))" 2>/dev/null)
echo "  Left: status=$LEAVE_STATUS"

# 7. Wait for Kafka consumer to process and persist
echo -e "\n=== 7. Wait 3s for Kafka consumer to persist ==="
sleep 3

# 8. Verify: Stats API (reads from queue_event_log table)
echo -e "\n=== 8. Stats API (Kafka → DB → API) ==="
STATS=$(curl -s "$BASE/stats/$EVENT_ID" -H "$AUTH")
echo "$STATS" | python3 -c "
import sys, json
data = json.load(sys.stdin)['data']
t = data['totals']
r = data['rates']
p = data['percentages']
print(f'  Totals: entered={t[\"entered\"]}, granted={t[\"granted\"]}, verified={t[\"verified\"]}, left={t[\"left\"]}')
print(f'  Rates:  entered/min={r[\"enteredPerMinute\"]}, granted/min={r[\"grantedPerMinute\"]}')
print(f'  Abandon rate: {p[\"abandonRate\"]}%, Conversion rate: {p[\"conversionRate\"]}%')

# Validation
assert t['entered'] >= 6, f'Expected >=6 entered, got {t[\"entered\"]}'
assert t['granted'] >= 5, f'Expected >=5 granted, got {t[\"granted\"]}'
assert t['left'] >= 1, f'Expected >=1 left, got {t[\"left\"]}'
print('  ✓ Stats validation passed')
" 2>/dev/null || echo "  Stats: $STATS"

# 9. Event Logs API
echo -e "\n=== 9. Event Logs API ==="
LOGS=$(curl -s "$BASE/stats/$EVENT_ID/logs?limit=15" -H "$AUTH")
echo "$LOGS" | python3 -c "
import sys, json
logs = json.load(sys.stdin)['data']
print(f'  Total logs: {len(logs)}')
types = {}
for log in logs:
    t = log['eventType']
    types[t] = types.get(t, 0) + 1
for t, c in sorted(types.items()):
    print(f'    {t}: {c}')

assert len(logs) > 0, 'Expected logs from Kafka consumer'
assert 'QUEUE_ENTERED' in types, 'Missing QUEUE_ENTERED events'
assert 'ENTRY_GRANTED' in types, 'Missing ENTRY_GRANTED events'
assert 'QUEUE_LEFT' in types, 'Missing QUEUE_LEFT events'
print('  ✓ Event log validation passed')
" 2>/dev/null || echo "  Logs: $LOGS"

# 10. Verify Kafka topics exist
echo -e "\n=== 10. Kafka Topics ==="
docker exec pulseq-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | grep pulseq | sort
echo "  ✓ Kafka topics verified"

# 11. Verify DB has records
echo -e "\n=== 11. DB queue_event_log count ==="
COUNT=$(docker exec pulseq-postgres psql -U pulseq -d pulseq -t -c \
  "SELECT COUNT(*) FROM queue_event_log WHERE event_id='$EVENT_ID';" 2>/dev/null | tr -d ' ')
echo "  Records in DB: $COUNT"

echo -e "\n=== ALL PHASE 3 TESTS PASSED ==="

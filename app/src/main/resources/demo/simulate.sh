#!/bin/zsh
# simulate.sh — Drive a full PR review lifecycle through devtown REST APIs
# Usage: simulate.sh [--auto] [base-url]
#   --auto    Skip interactive pauses (for CI/testing)
#   base-url  Default: http://127.0.0.1:8080
set -e

AUTO=false
BASE="http://127.0.0.1:8080"
for arg in "$@"; do
  case "$arg" in
    --auto) AUTO=true ;;
    http*) BASE="$arg" ;;
  esac
done

post() {
  local url="$1" body="$2"
  curl -s -X POST "$url" -H "Content-Type: application/json" -d "$body"
}

get() {
  curl -s "$1" | python3 -m json.tool 2>/dev/null || curl -s "$1"
}

pause() {
  if $AUTO; then
    sleep 2
  else
    echo ""
    read -r "?    Press Enter to continue..."
    echo ""
  fi
}

phase() {
  echo ""
  echo "═══════════════════════════════════════════════════════════"
  echo "  Phase $1: $2"
  echo "═══════════════════════════════════════════════════════════"
  echo ""
}

step() {
  echo "  ┌─ $1"
}

result() {
  echo "  └─ $1"
  echo ""
}

tab() {
  echo "    👁  Look at: $1"
}

# ═════════════════════════════════════════════════════════════════
echo ""
echo "  ╔═══════════════════════════════════════════════════════╗"
echo "  ║        DevTown — Full Lifecycle Simulation           ║"
echo "  ║  3 PRs × 7 phases: submit → review → gate → merge   ║"
echo "  ╚═══════════════════════════════════════════════════════╝"
echo ""

# ── PHASE 1: PR Submission ──────────────────────────────────────
phase 1 "PR Submission"
echo "  Submitting 3 PRs with different risk profiles."
tab "Reviews tab"
echo ""

step "PR #101: Security-sensitive auth refactor (alice, 460 lines, 8 files)"
post "$BASE/api/devtown/reviews/submit" '{
  "repo": "casehubio/webapp",
  "prNumber": 101,
  "headSha": "abc101abc101",
  "baseRef": "main",
  "linesChanged": 460,
  "contributor": "alice",
  "contributorNumericId": 1001,
  "changedPaths": ["src/auth/TokenService.java", "src/auth/SessionManager.java", "src/auth/RBAC.java", "src/config/SecurityConfig.java", "test/auth/TokenServiceTest.java", "test/auth/SessionManagerTest.java", "pom.xml", "docs/security.md"]
}' > /dev/null
result "Case created — security paths detected, expect security-review binding"
sleep 1

step "PR #102: Rename variables for consistency (bob, 50 lines, 3 files)"
post "$BASE/api/devtown/reviews/submit" '{
  "repo": "casehubio/webapp",
  "prNumber": 102,
  "headSha": "def102def102",
  "baseRef": "main",
  "linesChanged": 50,
  "contributor": "bob",
  "contributorNumericId": 1002,
  "changedPaths": ["src/service/UserService.java", "src/service/OrderService.java", "src/util/Naming.java"]
}' > /dev/null
result "Case created — small change, no human-approval gate expected"
sleep 1

step "PR #103: Extract payment service module (charlie, 1340 lines, 22 files)"
post "$BASE/api/devtown/reviews/submit" '{
  "repo": "casehubio/webapp",
  "prNumber": 103,
  "headSha": "ghi103ghi103",
  "baseRef": "main",
  "linesChanged": 1340,
  "contributor": "charlie",
  "contributorNumericId": 1003,
  "changedPaths": ["src/payment/PaymentService.java", "src/payment/PaymentGateway.java", "src/payment/StripeAdapter.java", "src/payment/PaymentController.java", "src/order/OrderService.java", "src/order/CheckoutFlow.java", "src/config/PaymentConfig.java", "src/config/ModuleConfig.java", "pom.xml", "payment-module/pom.xml", "payment-module/src/main/java/PaymentApp.java", "test/payment/PaymentServiceTest.java", "test/payment/GatewayTest.java", "test/order/OrderServiceTest.java", "docs/architecture.md", "docs/payment-module.md", "db/migration/V100__payment_tables.sql", "db/migration/V101__payment_indexes.sql", "src/payment/model/Transaction.java", "src/payment/model/Refund.java", "src/payment/spi/PaymentProvider.java", "src/payment/spi/RefundPolicy.java"]
}' > /dev/null
result "Case created — large + architecture crossing, expect human-approval gate"

pause

# ── PHASE 2: Observe Analysis + Routing ─────────────────────────
phase 2 "Observe Analysis + Routing"
echo "  The engine has analysed each PR and fired bindings based on"
echo "  code content. Check what reviews were assigned."
tab "Operations tab — active reviews and event stream"
echo ""

step "Queue status"
get "$BASE/api/devtown/governance/queue-status"
echo ""
result "Active reviews show routing decisions"

step "System health"
get "$BASE/api/devtown/governance/system-health"
echo ""
result "Fleet and commitment state"

step "Triage items (human gates)"
get "$BASE/api/devtown/governance/triage"
echo ""
result "WorkItems created by human-approval bindings"

pause

# ── PHASE 3: Signal Review Completion ───────────────────────────
phase 3 "Review Completion"
echo "  Simulating reviewer approvals for each PR."
echo "  PR #102 (simple) goes first — it should advance fastest."
tab "Reviews tab — click each PR to see timeline"
echo ""

step "PR #102 (simple rename) — Approve"
post "$BASE/api/actions/approve" '{"repo": "casehubio/webapp", "prNumber": 102, "contributor": "reviewer-bot"}'
echo ""
result "PR #102 approved — should advance toward merge readiness"
sleep 1

step "PR #101 (security) — Approve"
post "$BASE/api/actions/approve" '{"repo": "casehubio/webapp", "prNumber": 101, "contributor": "security-reviewer"}'
echo ""
result "PR #101 approved — security review accepted"
sleep 1

step "PR #103 (architecture) — Approve"
post "$BASE/api/actions/approve" '{"repo": "casehubio/webapp", "prNumber": 103, "contributor": "arch-reviewer"}'
echo ""
result "PR #103 approved — but may still have human-approval gate blocking"

pause

# ── PHASE 4: Human Gate (PR #103) ───────────────────────────────
phase 4 "Human Gate Decision"
echo "  PR #103 (1340-line payment extraction) requires human approval."
echo "  Checking for pending WorkItems and deciding them."
tab "Triage tab — claim and decide the WorkItem"
echo ""

step "Check triage items for pending WorkItems"
TRIAGE_RESPONSE=$(curl -s "$BASE/api/devtown/governance/triage")
echo "$TRIAGE_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$TRIAGE_RESPONSE"
echo ""

WORK_ITEM_ID=$(echo "$TRIAGE_RESPONSE" | python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    items = data.get('items', data) if isinstance(data, dict) else data
    if isinstance(items, list) and len(items) > 0:
        print(items[0].get('workItemId', ''))
    elif isinstance(items, dict) and 'items' in items:
        its = items['items']
        if len(its) > 0:
            print(its[0].get('workItemId', ''))
except: pass
" 2>/dev/null || echo "")

if [ -n "$WORK_ITEM_ID" ] && [ "$WORK_ITEM_ID" != "" ]; then
  step "Claiming WorkItem $WORK_ITEM_ID"
  post "$BASE/api/actions/claim-workitem" "{\"workItemId\": \"$WORK_ITEM_ID\", \"claimant\": \"lead-dev\"}"
  echo ""
  result "WorkItem claimed by lead-dev"
  sleep 1

  step "Deciding WorkItem — APPROVED"
  post "$BASE/api/actions/decide-workitem" "{\"workItemId\": \"$WORK_ITEM_ID\", \"outcome\": \"APPROVED\", \"actor\": \"lead-dev\"}"
  echo ""
  result "Human gate cleared — case can advance"
else
  result "No pending WorkItems found (gate may have been satisfied by approval)"
fi

pause

# ── PHASE 5: CI Pass ────────────────────────────────────────────
phase 5 "CI Status — All Passing"
echo "  Signalling CI pass for all 3 PRs."
tab "Reviews tab — CI status updates in timeline"
echo ""

step "PR #101 — CI passing"
post "$BASE/api/actions/signal-ci-pass" '{"repo": "casehubio/webapp", "prNumber": 101}'
echo ""
result "CI pass signalled"
sleep 1

step "PR #102 — CI passing"
post "$BASE/api/actions/signal-ci-pass" '{"repo": "casehubio/webapp", "prNumber": 102}'
echo ""
result "CI pass signalled"
sleep 1

step "PR #103 — CI passing"
post "$BASE/api/actions/signal-ci-pass" '{"repo": "casehubio/webapp", "prNumber": 103}'
echo ""
result "CI pass signalled"

pause

# ── PHASE 6: Merge Queue ────────────────────────────────────────
phase 6 "Merge Queue Admission"
echo "  Enqueueing approved PRs into the merge queue."
echo "  Trust-weighted ordering determines batch position."
tab "Merge Queue tab"
echo ""

step "Enqueue PR #102 (simple, likely highest trust)"
post "$BASE/api/actions/enqueue" '{"repo": "casehubio/webapp", "prNumber": 102, "headSha": "def102def102", "contributor": "bob"}'
echo ""
result "PR #102 enqueued"
sleep 1

step "Enqueue PR #101 (security)"
post "$BASE/api/actions/enqueue" '{"repo": "casehubio/webapp", "prNumber": 101, "headSha": "abc101abc101", "contributor": "alice"}'
echo ""
result "PR #101 enqueued"
sleep 1

step "Enqueue PR #103 (architecture)"
post "$BASE/api/actions/enqueue" '{"repo": "casehubio/webapp", "prNumber": 103, "headSha": "ghi103ghi103", "contributor": "charlie"}'
echo ""
result "PR #103 enqueued"
sleep 1

step "Merge queue status"
get "$BASE/api/devtown/governance/merge-queue"
echo ""
result "Queue shows trust-weighted ordering"

pause

# ── PHASE 7: Trust Evolution ────────────────────────────────────
phase 7 "Trust Evolution"
echo "  Review outcomes have updated trust scores."
echo "  Future routing decisions will factor in these scores."
tab "Trust tab — reviewer and contributor scores"
echo ""

step "Reviewer fleet — trust by capability"
get "$BASE/api/devtown/reviews/reviewers"
echo ""
result "Trust scores reflect review outcomes"

step "Contributor fleet — intake classification"
get "$BASE/api/devtown/reviews/contributors"
echo ""
result "Contributor trust affects merge queue priority"

step "SLA comparison"
get "$BASE/api/devtown/governance/sla-comparison"
echo ""
result "SLA calibration from observed durations"

# ── Done ────────────────────────────────────────────────────────
echo ""
echo "  ╔═══════════════════════════════════════════════════════╗"
echo "  ║              Simulation Complete                      ║"
echo "  ║                                                       ║"
echo "  ║  3 PRs driven through full lifecycle:                 ║"
echo "  ║    submit → analysis → review → gate → CI → merge    ║"
echo "  ║                                                       ║"
echo "  ║  Open $BASE to explore the dashboard  ║"
echo "  ╚═══════════════════════════════════════════════════════╝"
echo ""

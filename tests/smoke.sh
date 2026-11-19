#!/usr/bin/env bash
set -euo pipefail

BASE="${MUNIM_BASE_URL:-http://localhost:8080/api/v1}"
AUTH_OWNER="Authorization: Bearer dev-owner"
AUTH_OP="Authorization: Bearer dev-procurement"
AUTH_EVAL="Authorization: Bearer dev-evaluator"
key(){ python3 -c 'import uuid; print(uuid.uuid4())'; }

# 1. Health check
HEALTH="$(curl -fsS http://localhost:8080/actuator/health)"
echo "$HEALTH" | jq -e '.status == "UP"' >/dev/null

# 2. Seed material & add supplier
MATERIALS="$(curl -fsS -H "$AUTH_OWNER" "$BASE/materials")"
MATERIAL_ID="$(echo "$MATERIALS" | jq -r '.items[0].id')"
SUPPLIER="$(curl -fsS -X POST -H "$AUTH_OWNER" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/suppliers" -d '{"name":"Smoke Steel Supplier","location":"Synthetic test","isSynthetic":true}')"
SUPPLIER_ID="$(echo "$SUPPLIER" | jq -r '.id')"

# 3. Create RFQ
RFQ_BODY="$(jq -n --arg materialId "$MATERIAL_ID" --arg deadline "$(date -u -v+3d +%Y-%m-%dT%H:%M:%S+00:00)" '{materialId:$materialId,specification:"2 mm IS 2062 sheet",quantity:"500.0000",unit:"kg",requiredProcurementDeliveryAt:$deadline,timezone:"Asia/Kolkata",allowSplitSourcing:false}')"
RFQ="$(curl -fsS -X POST -H "$AUTH_OWNER" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/rfqs" -d "$RFQ_BODY")"
RFQ_ID="$(echo "$RFQ" | jq -r '.id')"

# 4. Add Quote
NOW="$(date -u +%Y-%m-%dT%H:%M:%S+00:00)"
UNTIL="$(date -u -v+7d +%Y-%m-%dT%H:%M:%S+00:00)"
PROMISED="$(date -u -v+2d +%Y-%m-%dT%H:%M:%S+00:00)"
QUOTE_BODY="$(jq -n --arg supplierId "$SUPPLIER_ID" --arg receivedAt "$NOW" --arg promised "$PROMISED" --arg validFrom "$NOW" --arg validUntil "$UNTIL" '{
  supplierId:$supplierId,receivedAt:$receivedAt,rawMessage:"[SYNTHETIC] 500 kg MS 2 mm at INR 95/kg plus GST 18%, separate freight INR 1500.",
  manualFields:{unitPrice:"95.00",quotedQuantity:"500",unit:"kg",priceBasis:"EXCLUSIVE",materialTaxRate:"18",materialItcStatus:"NON_CREDITABLE",freightBasis:"SEPARATE",freightAmount:"1500.00",freightTaxRate:"0",freightPriceBasis:"EXCLUSIVE",freightItcStatus:"CREDITABLE",moq:"0",purchaseIncrement:"1",wastagePercent:"0",quoteQuantityBasis:"INPUT_PURCHASED",promisedDeliveryAt:$promised,validFrom:$validFrom,validUntil:$validUntil}
}')"
QUOTE="$(curl -fsS -X POST -H "$AUTH_OWNER" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/rfqs/$RFQ_ID/quotes" -d "$QUOTE_BODY")"
QUOTE_ID="$(echo "$QUOTE" | jq -r '.id')"
REVISION_ID="$(echo "$QUOTE" | jq -r '.revisionId')"

# 5. Retrieve stored quote & check completeness
STORED_QUOTE="$(curl -fsS -H "$AUTH_OWNER" "$BASE/quotes/$QUOTE_ID/revisions/1")"
echo "$STORED_QUOTE" | jq -e '.rawMessage|contains("SYNTHETIC")' >/dev/null
curl -fsS -H "$AUTH_OWNER" "$BASE/rfqs/$RFQ_ID/completeness?quoteRevisionId=$REVISION_ID" | jq -e '.complete == true' >/dev/null

# 6. Deterministic financial evaluation
EVALUATION_BODY="$(jq -n --arg revisionId "$REVISION_ID" '{quoteRevisionIds:[$revisionId],policyVersion:1}')"
EVALUATION="$(curl -fsS -X POST -H "$AUTH_OWNER" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/rfqs/$RFQ_ID/evaluations" -d "$EVALUATION_BODY")"
EVALUATION_ID="$(echo "$EVALUATION" | jq -r '.id')"
EVALUATION_REPEAT="$(curl -fsS -X POST -H "$AUTH_OWNER" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/rfqs/$RFQ_ID/evaluations" -d "$EVALUATION_BODY")"
SAVED_EVALUATION="$(curl -fsS -H "$AUTH_OWNER" "$BASE/evaluations/$EVALUATION_ID")"
echo "$SAVED_EVALUATION" | jq -e '.result.economicLandedCost == "57550.00" and .result.cashPayable == "57550.00" and (.snapshotSha256|length == 64)' >/dev/null
test "$(echo "$EVALUATION" | jq -c '.result')" = "$(echo "$EVALUATION_REPEAT" | jq -c '.result')"
test "$(echo "$EVALUATION" | jq -r '.snapshotSha256')" = "$(echo "$EVALUATION_REPEAT" | jq -r '.snapshotSha256')"
EVAL_HASH="$(echo "$EVALUATION" | jq -r '.snapshotSha256')"

# 7. Create Award Proposal
PROPOSAL_BODY="$(jq -n --arg revId "$REVISION_ID" --arg hash "$EVAL_HASH" '{quoteRevisionId:$revId,expectedSnapshotHash:$hash}')"
PROPOSAL="$(curl -fsS -X POST -H "$AUTH_OP" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/evaluations/$EVALUATION_ID/award-proposals" -d "$PROPOSAL_BODY")"
PROPOSAL_ID="$(echo "$PROPOSAL" | jq -r '.id')"
echo "$PROPOSAL" | jq -e '.status == "PENDING" and (.snapshotHash|length == 64)' >/dev/null
PROP_HASH="$(echo "$PROPOSAL" | jq -r '.snapshotHash')"

# 8. Verify Operator cannot bypass Owner (403 Forbidden)
HTTP_CODE="$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "$AUTH_OP" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/proposals/$PROPOSAL_ID/decision" -d "{\"decision\":\"APPROVE\",\"expectedSnapshotHash\":\"$PROP_HASH\"}")"
test "$HTTP_CODE" = "403"

# 9. Verify Evaluator cannot access owner queue (403 Forbidden)
HTTP_CODE_EVAL="$(curl -s -o /dev/null -w "%{http_code}" -H "$AUTH_EVAL" "$BASE/approvals")"
test "$HTTP_CODE_EVAL" = "403"

# 10. Owner retrieves approval detail
DETAIL="$(curl -fsS -H "$AUTH_OWNER" "$BASE/approvals/$PROPOSAL_ID")"
echo "$DETAIL" | jq -e '.proposal.status == "PENDING" and .snapshot.economicLandedCost == "57550.00"' >/dev/null

# 11. Owner approves commercial proposal
DECISION_BODY="$(jq -n --arg hash "$PROP_HASH" '{decision:"APPROVE",reason:"Lowest economic landed cost approved by owner",expectedSnapshotHash:$hash}')"
APPROVAL="$(curl -fsS -X POST -H "$AUTH_OWNER" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/proposals/$PROPOSAL_ID/decision" -d "$DECISION_BODY")"
echo "$APPROVAL" | jq -e '.decision == "APPROVE"' >/dev/null

# 12. Verify commercial RFQ status is now AWARDED
AWARDED_RFQ="$(curl -fsS -H "$AUTH_OWNER" "$BASE/rfqs/$RFQ_ID")"
echo "$AWARDED_RFQ" | jq -e '.status == "AWARDED"' >/dev/null

# ---------------------------------------------------------
# Phase 5: AI Supplier Quote Intelligence Smoke Verification
# ---------------------------------------------------------

# 13. Create second RFQ for AI Quote Intelligence test
RFQ2_BODY="$(jq -n --arg materialId "$MATERIAL_ID" --arg deadline "$(date -u -v+3d +%Y-%m-%dT%H:%M:%S+00:00)" '{materialId:$materialId,specification:"2 mm IS 2062 sheet",quantity:"500.0000",unit:"kg",requiredProcurementDeliveryAt:$deadline,timezone:"Asia/Kolkata",allowSplitSourcing:false}')"
RFQ2="$(curl -fsS -X POST -H "$AUTH_OWNER" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/rfqs" -d "$RFQ2_BODY")"
RFQ2_ID="$(echo "$RFQ2" | jq -r '.id')"

# 14. Ingest raw untrusted supplier message without manual fields
RAW_MSG="MS sheet 2 mm, 500 kg, ₹95/kg plus GST 18%, separate freight ₹1,500, deliver in 2 days, validity 7 days."
AI_QUOTE_BODY="$(jq -n --arg supplierId "$SUPPLIER_ID" --arg receivedAt "$NOW" --arg raw "$RAW_MSG" '{
  supplierId:$supplierId,receivedAt:$receivedAt,channel:"PASTED_TEXT",rawMessage:$raw,manualFields:{}
}')"
AI_QUOTE="$(curl -fsS -X POST -H "$AUTH_OP" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/rfqs/$RFQ2_ID/quotes" -d "$AI_QUOTE_BODY")"
AI_QUOTE_ID="$(echo "$AI_QUOTE" | jq -r '.id')"

# 15. Trigger AI Quote Extraction
EXTRACTION="$(curl -fsS -X POST -H "$AUTH_OP" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/quotes/$AI_QUOTE_ID/revisions/1/extract" -d '{}')"
echo "$EXTRACTION" | jq -e '.validationStatus == "VALID" and .candidates.unitPrice.value == "95.00" and .candidates.freightAmount.value == "1500.00" and .candidates.unitPrice.evidence != null' >/dev/null

# 16. Test Clarification Draft Generation
DRAFT="$(curl -fsS -X POST -H "$AUTH_OP" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/quotes/$AI_QUOTE_ID/revisions/1/clarifications" -d '{"expectedFields":["materialTaxRate","priceBasis"]}')"
DRAFT_ID="$(echo "$DRAFT" | jq -r '.id')"
echo "$DRAFT" | jq -e '.status == "DRAFT" and (.draft|contains("Draft only - not sent externally"))' >/dev/null

# 17. Simulate Supplier Response (creates Revision 2)
REPLY_BODY="$(jq -n --arg receivedAt "$NOW" '{responseText:"GST 18% extra, quotation is valid for 7 days.",receivedAt:$receivedAt,simulated:true}')"
REV2="$(curl -fsS -X POST -H "$AUTH_OP" -H "Idempotency-Key: $(key)" -H 'Content-Type: application/json' "$BASE/quotes/$AI_QUOTE_ID/revisions/1/clarifications/$DRAFT_ID/response" -d "$REPLY_BODY")"
echo "$REV2" | jq -e '.revision == 2 and .completenessStatus == "COMPLETE"' >/dev/null

# 18. Cleanup: restore pristine authentic demo data
curl -fsS -X POST -H "$AUTH_OWNER" "$BASE/demo/seed" >/dev/null

echo "PASS: synthetic RFQ -> supplier -> stored manual quote -> completeness -> persisted repeatable evaluation -> human approval workflow -> sole commercial writer award"
echo "PASS: Phase 5 AI quote intelligence -> raw text intake -> candidate extraction -> evidence validation -> clarification draft -> simulated supplier response -> complete revision 2"


-- Clean Reset and Realistic Indian Sheet-Metal Fabrication Demo Seed
TRUNCATE TABLE
  audit_events,
  approvals,
  customer_quotations,
  costing_sheets,
  bom_items,
  boms,
  customer_enquiries,
  evaluation_lines,
  quote_evaluations,
  clarification_messages,
  quote_extractions,
  commercial_proposals,
  supplier_quote_revisions,
  supplier_quotes,
  rfqs,
  suppliers,
  materials,
  idempotency_records
CASCADE;

-- 1. Materials
INSERT INTO materials (id, name, grade, specification, base_unit, active) VALUES
('fdb05a31-6de1-49fd-aea3-2df5b4074b8b', 'IS 2062 Mild Steel Sheet 2mm', 'E250A', '2 mm IS 2062 mild-steel sheet', 'kg', true),
('fdb05a31-6de1-49fd-aea3-2df5b4074b8c', 'SS 304 Stainless Steel Sheet 1.5mm', '304 2B', '1.5 mm SS 304 CR sheet', 'kg', true),
('fdb05a31-6de1-49fd-aea3-2df5b4074b8d', 'Aluminium 5052-H32 Sheet 3mm', '5052-H32', '3 mm Aluminium sheet', 'kg', true);

-- 2. Suppliers
INSERT INTO suppliers (id, name, location, is_synthetic, active) VALUES
('a1111111-1111-1111-1111-111111111111', 'Apex Steel Works (Bhosari, Pune)', 'Plot 42 MIDC Bhosari, Pune 411026', true, true),
('a2222222-2222-2222-2222-222222222222', 'Mahalaxmi Metals & Tubes (Chakan)', 'Phase 2 Chakan MIDC, Pune 410501', true, true),
('a3333333-3333-3333-3333-333333333333', 'Tata Steel Stockist (Kalbadevi)', 'Lohar Chawl, Kalbadevi, Mumbai 400002', true, true),
('a4444444-4444-4444-4444-444444444444', 'Jindal Steel & Power Partner (Nagpur)', 'MIDC Hingna, Nagpur 440028', true, true);

-- 3. RFQs
INSERT INTO rfqs (id, material_id, specification, quantity, unit, required_procurement_delivery_at, reference_at, timezone, allow_split_sourcing, status) VALUES
('b1111111-1111-1111-1111-111111111111', 'fdb05a31-6de1-49fd-aea3-2df5b4074b8b', '2 mm IS 2062 mild-steel sheet', 500.0000, 'kg', now() + interval '3 days', now(), 'Asia/Kolkata', false, 'AWARDED'),
('b2222222-2222-2222-2222-222222222222', 'fdb05a31-6de1-49fd-aea3-2df5b4074b8b', '2 mm IS 2062 mild-steel sheet', 1200.0000, 'kg', now() + interval '5 days', now(), 'Asia/Kolkata', false, 'OPEN');

-- 4. Supplier Quotes (without revisions first to break circular FK)
INSERT INTO supplier_quotes (id, rfq_id, supplier_id, current_revision_id) VALUES
('c1111111-1111-1111-1111-111111111111', 'b1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', null),
('c2222222-2222-2222-2222-222222222222', 'b1111111-1111-1111-1111-111111111111', 'a2222222-2222-2222-2222-222222222222', null);

-- 5. Supplier Quote Revisions
INSERT INTO supplier_quote_revisions (
  id, supplier_quote_id, rfq_id, revision_no, state, received_at, raw_message, raw_message_sha256,
  fields, completeness_status
) VALUES
('d1111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', 'b1111111-1111-1111-1111-111111111111', 1, 'VERIFIED', now() - interval '2 hours',
 'MS sheet 2 mm, 500 kg, ₹95/kg plus GST 18%, separate freight ₹1,500, deliver in 2 days, validity 7 days.',
 encode(digest('MS sheet 2 mm, 500 kg, ₹95/kg plus GST 18%, separate freight ₹1,500, deliver in 2 days, validity 7 days.', 'sha256'), 'hex'),
 '{"unitPrice": "95.00", "quotedQuantity": "500", "unit": "kg", "priceBasis": "EXCLUSIVE", "materialTaxRate": "18", "materialItcStatus": "CREDITABLE", "freightBasis": "SEPARATE", "freightAmount": "1500", "freightTaxRate": "0", "freightPriceBasis": "EXCLUSIVE", "freightItcStatus": "CREDITABLE", "moq": "0", "purchaseIncrement": "1", "wastagePercent": "0", "quoteQuantityBasis": "INPUT_PURCHASED"}'::jsonb,
 'COMPLETE'),
('d2222222-2222-2222-2222-222222222222', 'c2222222-2222-2222-2222-222222222222', 'b1111111-1111-1111-1111-111111111111', 1, 'EXTRACTED', now() - interval '1 hour',
 'Bhai rate 92 lagaya hai 500 kg ka, kal bhej denge, 2500 bhada alag se lgega, gst 18% extra composition no ITC.',
 encode(digest('Bhai rate 92 lagaya hai 500 kg ka, kal bhej denge, 2500 bhada alag se lgega, gst 18% extra composition no ITC.', 'sha256'), 'hex'),
 '{"unitPrice": "92.00", "quotedQuantity": "500", "unit": "kg", "priceBasis": "EXCLUSIVE", "materialTaxRate": "18", "materialItcStatus": "NON_CREDITABLE", "freightBasis": "SEPARATE", "freightAmount": "2500", "freightTaxRate": "0", "freightPriceBasis": "EXCLUSIVE", "freightItcStatus": "NON_CREDITABLE"}'::jsonb,
 'COMPLETE');

-- Update supplier_quotes with current and awarded revisions
UPDATE supplier_quotes SET current_revision_id = 'd1111111-1111-1111-1111-111111111111', awarded_revision_id = 'd1111111-1111-1111-1111-111111111111' WHERE id = 'c1111111-1111-1111-1111-111111111111';
UPDATE supplier_quotes SET current_revision_id = 'd2222222-2222-2222-2222-222222222222' WHERE id = 'c2222222-2222-2222-2222-222222222222';
UPDATE rfqs SET awarded_quote_id = 'c1111111-1111-1111-1111-111111111111' WHERE id = 'b1111111-1111-1111-1111-111111111111';

-- 6. Quote Evaluations
INSERT INTO quote_evaluations (
  id, rfq_id, quote_revision_id, reference_at, calculation_version, input_snapshot, snapshot_sha256, status, result
) VALUES
('e1111111-1111-1111-1111-111111111111', 'b1111111-1111-1111-1111-111111111111', 'd1111111-1111-1111-1111-111111111111',
 now(), '1.1',
 '{"rfqQuantity": "500.0000", "unitPrice": "95.00", "freightAmount": "1500.00"}'::jsonb,
 '4717fdae7c9964d82ae86b10d97680588096c6373714d16c0c54fd560971729c',
 'COMPLETE',
 '{"purchaseQuantity": "500.0000", "materialBase": "47500.00", "materialGst": "8550.00", "freightBase": "1500.00", "freightGst": "0.00", "economicLandedCost": "49000.00", "cashPayable": "57550.00"}'::jsonb);

INSERT INTO evaluation_lines (
  id, evaluation_id, quote_revision_id, material_base, material_gst, freight_base, freight_gst, other_charges, economic_landed_cost, cash_payable, currency
) VALUES
('ea111111-1111-1111-1111-111111111111', 'e1111111-1111-1111-1111-111111111111', 'd1111111-1111-1111-1111-111111111111',
 47500.00, 8550.00, 1500.00, 0.00, 0.00, 49000.00, 57550.00, 'INR');

-- 7. Customer Enquiries
INSERT INTO customer_enquiries (id, customer_name, customer_reference, contact_email, contact_phone, required_delivery_date, status, notes) VALUES
('f1111111-1111-1111-1111-111111111111', 'Defence Chassis Corp (Pune)', 'PO-DCC-2026-081', 'procurement@defencechassis.in', '+91-9876543210', now() + interval '30 days', 'COSTED', 'High precision laser cut armoured chassis brackets'),
('f2222222-2222-2222-2222-222222222222', 'Bharat Forge Ltd (Mundhwa)', 'BF-AUTO-4402', 'sourcing@bharatforge.com', '+91-9822012345', now() + interval '45 days', 'QUOTED', 'Forging enclosure stiffener plates 5mm'),
('f3333333-3333-3333-3333-333333333333', 'Tata Motors Commercial Vehicles', 'TM-CV-991', 'vendor.desk@tatamotors.com', '+91-9923456789', now() + interval '20 days', 'OPEN', 'Heavy vehicle battery tray brackets batch of 250'),
('f4444444-4444-4444-4444-444444444444', 'Mahindra Auto Stamping Div', 'MAH-ST-104', 'stamping@mahindra.com', '+91-9811223344', now() + interval '15 days', 'WON', 'SUV fender support brackets production run'),
('f5555555-5555-5555-5555-555555555555', 'Kirloskar Pneumatic Co', 'KPC-552', 'purchase@kirloskar.co.in', '+91-9765432109', now() + interval '10 days', 'LOST', 'Compressor baseplates quote lost on competitor undercutting');

-- 8. BOMs & BOM Items
INSERT INTO boms (id, enquiry_id, bom_version, batch_quantity, status) VALUES
('ba111111-1111-1111-1111-111111111111', 'f1111111-1111-1111-1111-111111111111', 1, 100.0000, 'FINALIZED'),
('ba222222-2222-2222-2222-222222222222', 'f2222222-2222-2222-2222-222222222222', 1, 200.0000, 'FINALIZED');

INSERT INTO bom_items (
  id, bom_id, material_id, part_name, net_quantity, unit, scrap_percentage,
  scrap_recovery_supported, scrap_recovery_price, sourcing_basis, awarded_quote_revision_id,
  estimated_unit_cost, estimate_rationale
) VALUES
('bb111111-1111-1111-1111-111111111111', 'ba111111-1111-1111-1111-111111111111', 'fdb05a31-6de1-49fd-aea3-2df5b4074b8b',
 'Armoured Chassis Bracket 5mm', 2.5000, 'kg', 10.00, true, 25.00, 'AWARDED_QUOTE', 'd1111111-1111-1111-1111-111111111111', null, null),
('bb222222-2222-2222-2222-222222222222', 'ba222222-2222-2222-2222-222222222222', 'fdb05a31-6de1-49fd-aea3-2df5b4074b8b',
 'Stiffener Plate 3mm', 1.8000, 'kg', 8.00, true, 22.00, 'ESTIMATE', null, 94.00, 'Current spot benchmark from MIDC Bhosari steel market');

-- 9. Costing Sheets
INSERT INTO costing_sheets (
  id, enquiry_id, bom_id, batch_quantity, material_cost, conversion_cost, overhead_cost,
  packaging_cost, freight_cost, total_cost, target_margin, target_price, quoted_price,
  realized_margin, margin_classification, calculation_version, calculation_snapshot, snapshot_sha256
) VALUES
('cc111111-1111-1111-1111-111111111111', 'f1111111-1111-1111-1111-111111111111', 'ba111111-1111-1111-1111-111111111111',
 100.0000, 255.00, 151.50, 30.30, 15.00, 25.00, 476.80, 20.00, 596.00, 650.00,
 26.65, 'HEALTHY_MARGIN', '1.0',
 '{"totalCost": "476.80", "targetPrice": "596.00", "quotedPrice": "650.00", "marginClassification": "HEALTHY_MARGIN"}'::jsonb,
 'd426a032a4ef11bbaea9c304c9436084ea8f0e41e7dccac4e1977ed5facd65ab'),
('cc222222-2222-2222-2222-222222222222', 'f2222222-2222-2222-2222-222222222222', 'ba222222-2222-2222-2222-222222222222',
 200.0000, 165.20, 95.00, 26.00, 12.00, 20.00, 318.20, 20.00, 397.75, 410.00,
 22.39, 'HEALTHY_MARGIN', '1.0',
 '{"totalCost": "318.20", "targetPrice": "397.75", "quotedPrice": "410.00", "marginClassification": "HEALTHY_MARGIN"}'::jsonb,
 'e729a032a4ef11bbaea9c304c9436084ea8f0e41e7dccac4e1977ed5facd88ff');

-- 10. Commercial Proposals
INSERT INTO commercial_proposals (
  id, type, entity_type, entity_id, evaluation_id, quote_revision_id, cost_sheet_id,
  requested_action, requesting_user, status, snapshot_payload, snapshot_hash
) VALUES
('fa111111-1111-1111-1111-111111111111', 'SUPPLIER_AWARD', 'QUOTE_EVALUATION', 'e1111111-1111-1111-1111-111111111111',
 'e1111111-1111-1111-1111-111111111111', 'd1111111-1111-1111-1111-111111111111', null,
 'AWARD_SUPPLIER', 'dev-procurement', 'PENDING',
 '{"supplierName": "Apex Steel Works (Bhosari)", "rfqSpecification": "2 mm IS 2062 mild-steel sheet", "rfqQuantity": "500.0000", "rfqUnit": "kg", "economicLandedCost": "49000.00", "cashPayable": "57550.00", "currency": "INR", "savingsVsNextBest": "7780.00", "riskFlag": null, "unitPrice": "95.00", "freightBase": "1500.00"}'::jsonb,
 '9c0740e5e8e6d483235d11f2ee08285285938142c7d80785a7ce415db1c6844e'),

('fa222222-2222-2222-2222-222222222222', 'CUSTOMER_QUOTE', 'CUSTOMER_QUOTATION', 'ee222222-2222-2222-2222-222222222222',
 null, null, 'cc222222-2222-2222-2222-222222222222',
 'APPROVE_CUSTOMER_QUOTE', 'dev-procurement', 'PENDING',
 '{"customerName": "Bharat Forge Ltd (Mundhwa)", "sellingPrice": "410.00", "customerPayable": "483.80", "batchQuantity": "200.0000", "marginClassification": "HEALTHY_MARGIN", "marginPercent": "22.39", "totalCost": "318.20", "profitPerPart": "91.80", "validUntil": "2026-12-25T18:00:00Z"}'::jsonb,
 'e729a032a4ef11bbaea9c304c9436084ea8f0e41e7dccac4e1977ed5facd88ff'),

('fa333333-3333-3333-3333-333333333333', 'CUSTOMER_QUOTE', 'CUSTOMER_QUOTATION', 'ee111111-1111-1111-1111-111111111111',
 null, null, 'cc111111-1111-1111-1111-111111111111',
 'APPROVE_CUSTOMER_QUOTE', 'dev-procurement', 'APPROVED',
 '{"customerName": "Defence Chassis Corp (Pune)", "sellingPrice": "650.00", "customerPayable": "767.00", "batchQuantity": "100.0000", "marginClassification": "HEALTHY_MARGIN", "marginPercent": "26.65", "totalCost": "476.80", "profitPerPart": "173.20"}'::jsonb,
 'd426a032a4ef11bbaea9c304c9436084ea8f0e41e7dccac4e1977ed5facd65ab'),

('fa444444-4444-4444-4444-444444444444', 'CUSTOMER_QUOTE', 'CUSTOMER_QUOTATION', 'ee111111-1111-1111-1111-111111111111',
 null, null, 'cc111111-1111-1111-1111-111111111111',
 'APPROVE_CUSTOMER_QUOTE', 'dev-procurement', 'REJECTED',
 '{"customerName": "Kirloskar Pneumatic Co", "sellingPrice": "290.00", "customerPayable": "342.20", "marginClassification": "BELOW_MINIMUM_MARGIN", "marginPercent": "8.20", "totalCost": "268.00", "riskFlag": "Margin 8.2% below 10% threshold"}'::jsonb,
 'f538a032a4ef11bbaea9c304c9436084ea8f0e41e7dccac4e1977ed5facd99aa');

-- 11. Customer Quotations
INSERT INTO customer_quotations (
  id, enquiry_id, costing_sheet_id, selling_price, output_gst_rate, customer_payable,
  valid_from, valid_until, payment_terms, margin_classification, approval_status, commercial_proposal_id
) VALUES
('ee111111-1111-1111-1111-111111111111', 'f1111111-1111-1111-1111-111111111111', 'cc111111-1111-1111-1111-111111111111',
 650.00, 18.00, 767.00, now(), now() + interval '30 days', '50% Advance, 50% on Delivery', 'HEALTHY_MARGIN', 'APPROVED', 'fa333333-3333-3333-3333-333333333333'),
('ee222222-2222-2222-2222-222222222222', 'f2222222-2222-2222-2222-222222222222', 'cc222222-2222-2222-2222-222222222222',
 410.00, 18.00, 483.80, now(), now() + interval '30 days', '100% on Dispatch', 'HEALTHY_MARGIN', 'PENDING_APPROVAL', 'fa222222-2222-2222-2222-222222222222');

-- 12. Approvals Audit History
INSERT INTO approvals (
  id, proposal_id, entity_type, entity_id, requested_action, requesting_user,
  decision_status, deciding_user, decision_reason, snapshot_payload, snapshot_hash
) VALUES
('aa111111-1111-1111-1111-111111111111', 'fa333333-3333-3333-3333-333333333333', 'CUSTOMER_QUOTATION', 'ee111111-1111-1111-1111-111111111111',
 'APPROVE_CUSTOMER_QUOTE', 'dev-procurement', 'APPROVED', 'dev-owner',
 'Approved quotation: 26.65% healthy margin exceeds 20% policy target with verified costing snapshot',
 '{"customerName": "Defence Chassis Corp (Pune)", "sellingPrice": "650.00"}'::jsonb,
 'd426a032a4ef11bbaea9c304c9436084ea8f0e41e7dccac4e1977ed5facd65ab'),

('aa222222-2222-2222-2222-222222222222', 'fa444444-4444-4444-4444-444444444444', 'CUSTOMER_QUOTATION', 'ee111111-1111-1111-1111-111111111111',
 'APPROVE_CUSTOMER_QUOTE', 'dev-procurement', 'REJECTED', 'dev-owner',
 'Rejected by Owner: Realized margin of 8.2% breaches mandatory 10% minimum threshold. Unviable commercial risk.',
 '{"customerName": "Kirloskar Pneumatic Co", "sellingPrice": "290.00"}'::jsonb,
 'f538a032a4ef11bbaea9c304c9436084ea8f0e41e7dccac4e1977ed5facd99aa');

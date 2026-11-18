# MUNIM financial rules

Version 1.1. This governs Java BigDecimal calculations. It is an engineering model, not tax advice. GST logic requires accountant validation before real purchasing use.

## Numeric representation

- Quantities/rates: NUMERIC(14,4); money NUMERIC(14,2); percentages NUMERIC(8,4). Never use binary floating point.
- Keep intermediate precision. Round each monetary component once to paise with HALF_UP, then sum rounded components. Display ratios to 2 percentage decimals; persist 4. API decimals are JSON strings.
- Currency is INR. No FX.
- Missing values remain UNKNOWN/null and block confirmed evaluation; never coerce to zero. Explicit user-entered zero is a known value with provenance.

## GST and freight

Material and freight have separate amount basis (INCLUSIVE, EXCLUSIVE, UNKNOWN), tax rate, and ITC status (CREDITABLE, NON_CREDITABLE, UNKNOWN). MVP does not infer freight tax from material tax, calculate tax-on-tax, allocate bundled charges, or model reverse charge/place of supply. Unknown basis/rate/creditability blocks confirmed economic ranking. Accountant validation is required.

For component gross G and tax rate r:
- Exclusive: base=G; tax=round(G*r); cash component=base+tax.
- Inclusive: total=G; base=round(G/(1+r),2); tax=G-base (residual paise assigned to tax).
- Unknown basis/rate: component unknown; no evaluated total.
- Economic component cost = base plus tax only if explicitly non-creditable. Cash payable includes tax payable.

Freight basis is INCLUDED_IN_UNIT_PRICE, SEPARATE, or UNKNOWN. Included freight is not added twice. Separate freight tax is computed independently. Unknown freight tax basis/rate/creditability blocks confirmed total when freight is nonzero.

Example (material only): 100 kg × ₹100/kg exclusive of 18%, non-creditable, plus separate freight ₹500 whose tax is explicitly confirmed zero: material base ₹10,000; GST ₹1,800; freight ₹500; economic and cash total ₹12,300. If material GST is creditable, economic total ₹10,500 while cash remains ₹12,300. This does not authorize assuming freight GST is zero.

## Purchase quantity, increment, MOQ and wastage

Normalize required usable demand u and supplier terms into the material base unit through an approved material conversion. Define supplier increment i>0, MOQ m>=0, and wastage fraction 0<=w<1. If supplier price applies to input purchased quantity:

q_need = u/(1-w) when wastage applies, else u.
q_increment = ceil(q_need/i)*i.
q_moq = ceil(m/i)*i.
q_purchase = max(q_increment,q_moq).
rounding_excess = q_increment-q_need.
moq_excess_over_increment = max(0,q_purchase-q_increment).
excess_vs_usable_demand = max(0,q_purchase-u).

Persist all inputs/results. Wastage only grosses up quantity; no extra wastage surcharge. If price is explicitly for usable output, disable gross-up and record basis. Unknown increment or quote quantity basis blocks completeness. Split sourcing is disabled in MVP.

Example: usable demand 100 kg, 5% wastage, 1 kg increment, MOQ 0: q_need=105.2632 kg, q_increment=q_purchase=106 kg; at ₹100/kg material is ₹10,600 before freight/tax; rounding excess 0.7368 kg. With MOQ 110 kg, q_purchase=110; MOQ excess over incremented need=4 kg; excess over usable demand=10 kg. Display both.

## Economic landed cost vs cash payable

Material base is normalized purchased quantity times unit rate, applying price basis correctly. Do not add freight separately if included in the unit rate.

Economic landed cost = material base + separate freight base + other explicit charges + non-creditable material GST + non-creditable freight GST + other explicit nonrecoverable taxes.

Cash payable = material base + material GST + separate freight base + freight GST + other payable charges.

Show both. Recoverable GST remains cash payable but is excluded from economic cost. Payment terms are display-only; no financing benefit. Rank only complete eligible quotes by economic landed cost.

Eligibility hard checks: exact material/specification; quantity/MOQ/increment feasibility; required procurement deadline; quote validity at decision time; complete unit, price and tax basis. Record reasons. Relative dates resolve against persisted RFQ reference_at and timezone. Supplier-stated relative validity durations resolve from received_at in supplier timezone using calendar days, with provenance; absent valid_from is explicitly set to received_at. Ambiguous/missing receipt time or timezone leaves validity unknown. A quote is valid iff resolved valid_from <= decision_time <= resolved valid_until.

## Customer cost, margin and markup

All required components must be known; blank is not zero. Explicit zero is known.

total_cost = material + labour + overhead + freight + other direct costs
profit = selling_price - total_cost
margin_percent = profit / selling_price * 100 (selling_price > 0)
markup_percent = profit / total_cost * 100 (total_cost > 0; else undefined)

Example: cost ₹10,000, selling price ₹12,500 gives profit ₹2,500, margin 20%, markup 25%. Price below cost warns and cannot be approved by default. Below minimum margin warns and needs owner review; override policy remains unresolved.

## Demo arithmetic from supplied strategy inputs

RFQ: 500 kg, 2 mm MS sheet, delivery within three days.

| Supplier | Supplied facts | Computable facts | Correct evaluation |
|---|---|---|---|
| A | ₹95/kg; 500 kg; material GST extra at 18%; freight ₹1,500; delivery day 2; 7-day validity | Material base ₹47,500; material GST ₹8,550; freight ₹1,500. Cash subtotal ₹57,550 only if freight GST explicitly confirmed zero; otherwise freight tax unknown. If freight tax zero: economic subtotal ₹57,550 if material GST non-creditable, or ₹49,000 if material GST creditable. | Not a confirmed final cost: material ITC and freight GST basis/creditability are absent. |
| B | ₹96/kg; 500 kg; freight included; delivery tomorrow; 15-day terms; GST “as applicable”; no validity stated | Pre-tax stated value ₹48,000 if quantity/unit verified. Do not add freight. | Incomplete: GST rate/price basis/ITC and validity missing; not rankable. |
| C | ₹90/kg; 500 kg; freight ₹3,000; delivery day 6 | Pre-tax material ₹45,000 plus freight ₹3,000 | Excluded for missing 3-day deadline regardless of tax gaps; no confirmed total. |

Thus original scenario can show B clarification and C deadline rejection, but cannot support selecting A, negotiating a preferred supplier, or approving an award. Additional clarification must be separately labeled synthetic data and include all tax and validity fields before continuing that demo path.


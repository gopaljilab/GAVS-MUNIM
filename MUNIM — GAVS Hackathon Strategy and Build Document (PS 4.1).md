# MUNIM — GAVS Hackathon Strategy and Build Document (PS 4.1)

Prepared 18 November 2026. Covers the problem, our solution, expected impact, how PS 4.1 was selected, and the full build plan.

## 1. The Problem, Our Solution and the Impact

### The problem (from official PS 4.1)

**Title:** Small factories overpay for raw material and quote orders at a loss. **Tag:** AGENTIC AI.

The official statement says that buying material means calling 5–10 suppliers repeatedly and still missing better deals. Costing is approximate, so units either quote at a loss or lose the order, and they rarely sell beyond their local network.

**Who lives with this:** owners of fabrication, plastics, garment and component units who handle purchase and quoting themselves between running the shop floor.

**How it is handled today:** rates arrive by phone and WhatsApp and are compared on paper. Quotes are worked out by rule of thumb. Most business comes from a few familiar local buyers.

**What it costs:** units overpay for material, take orders that lose money, miss bids they should have won, and stay dependent on a handful of customers.

**What makes it hard (official):** quotes are unstructured and multilingual, prices are volatile, and vague buyer specifications have to be mapped to what the shop floor can really produce.

**Boundaries (official):** any negotiation stays within limits the owner sets, and a person makes every award and payment.

### Our solution: MUNIM

MUNIM is an AI-powered procurement and profitability assistant for small sheet-metal fabrication businesses. It connects two things the owner does today by memory and paper:

- **Buy smarter:** read the messy supplier quotes the owner already receives, find missing information, calculate true landed cost, reject suppliers that cannot meet the deadline, and draft negotiation messages within the owner's limits.
- **Quote profitably:** use the same cost model to build a customer quotation, show the margin as the owner changes the price, and warn when the quote would lose money.

**The differentiator:** one shared costing engine connects the procurement decision to the customer quotation. Flow: Buy smarter → Calculate accurately → Quote profitably.

**Design principles:**

- The LLM extracts information and drafts messages. It never calculates final financial values. Java services do all costing.
- The agent can draft and recommend. Only the owner can approve a supplier award or a customer quotation.
- All supplier personas, supplier history and prevented-loss figures in the demo are synthetic and labelled as such.

### Expected impact

The impact below follows the problem as stated in the PS. These are hypotheses to demonstrate in a simulated scenario and to validate with real owners. They are not measured results.

- **Less time on supplier calls:** quotes are parsed and compared in one place instead of repeated phone calls.
- **Lower real purchase cost:** comparison uses landed cost (freight, non-creditable GST, other charges, wastage), not just the quoted rate.
- **Fewer loss-making orders:** the quote screen flags any price below total cost and any margin below the owner's minimum.
- **Fewer missed deadlines:** suppliers that cannot deliver in time are excluded, with the reason shown.
- **Owner stays in control:** every commercial action needs approval, and every step is logged.

**How we will show impact honestly:** a simulated prevented-loss scenario, clearly labelled as a scenario estimate, plus a small labelled test set that reports the actual number of fields tested and errors found. We will not claim unsupported accuracy percentages or real savings.

## 2. How We Chose PS 4.1

### 2.1 What was in the ZIP

- 22 PDFs, each one page with a text layer. All were read.
- 18 unique problem statements. The "Real Estate & Land" folder contained four files whose text is identical to the four Agriculture statements (6.1–6.4).
- Numbering starts at 2.x, so a PS 1.x set may have been overwritten (this is an inference). It must be checked on the portal.
- Every statement uses the same template: tag, The problem, Who lives with this, How it gets handled today, What it costs, Who else is in the picture, What makes it hard, Boundaries, Questions to take into the field, Following the money.
- The documents state no evaluation criteria, no required tech stack and no deliverable format.
- 13 statements are tagged Agentic AI and 5 are tagged Applied AI Systems.
- Inference about judging: the recurring Boundaries (human approval, consent, labelled simulation, stated uncertainty), the field questions and the "Following the money" prompts suggest guardrails, field grounding and a who-benefits story matter.

The 18 statements: 2.1 Gold purity and records; 2.2 Karigar gold job-work; 3.1 GST compliance; 3.2 MSME credit and schemes; 4.1 Procurement and quoting; 4.2 Visual defect inspection; 4.3 Predictive maintenance; 5.1 Kirana inventory; 5.2 Village group buying; 5.3 Rural freight pooling; 6.1 Farmer sell timing; 6.2 Livestock health; 6.3 Micro food processors; 6.4 FPO supply forecasting; 7.1 Artisan market access; 7.2 Folk artist bookings; 8.1 SHG fulfilment and pay; 8.2 Rural youth jobs.

### 2.2 Team profile used for the evaluation

- 3rd-year B.E. Computer Science, graduating 2028.
- Strong in Java, Spring Boot, REST APIs, PostgreSQL, full-stack web, React and TypeScript. Basic AI/ML, AI-powered apps and agentic AI concepts.
- Stronger in software engineering than research-level AI. No ML researchers, large cloud budget or proprietary datasets.
- Past projects include Om Restaurant Management System, SamarthLoan, Clinical Insight Engine, MealSync and PRism, plus hackathon and presentation experience.
- Consequence: statements needing hardware, signal or vision ML, regional-language speech or unobtainable data were penalised (4.2, 4.3, 6.4, 2.1).

### 2.3 Scoring method

Each statement was scored 1–10 on 15 criteria: winning potential, problem importance, real-world impact, innovation, agentic fit, technical feasibility, demo potential, differentiation, data availability, implementation risk (10 = low risk), time-to-MVP, scalability, presentation, team skill match, and judge appeal. Judge appeal was shown but not weighted.

Weights: Winning 15%, Importance 10%, Impact 10%, Innovation 10%, Agentic 10%, Feasibility 10%, Demo 10%, Differentiation 8%, Data 5%, Risk 5%, Time-to-MVP 3%, Scalability 2%, Presentation 1%, Skill match 1%.

These scores are subjective judgments, not measured competition results. The weighted totals were computed with a script so they can be re-run if any score changes.

### 2.4 Ranking of all 18 statements (weighted score out of 10)

1. PS 4.1 Procurement and quoting — 7.90
2. PS 2.2 Karigar gold job-work — 7.41
3. PS 8.1 SHG fulfilment and pay — 7.18
4. PS 5.3 Rural freight pooling — 6.89
5. PS 3.2 MSME credit and schemes — 6.79
6. PS 6.3 Micro food processors — 6.62
7. PS 5.1 Kirana inventory — 6.48
8. PS 6.1 Farmer sell timing — 6.34
9. PS 7.1 Artisan market access — 6.32
10. PS 4.3 Predictive maintenance — 6.19
11. PS 6.2 Livestock health — 5.97
12. PS 8.2 Rural youth jobs — 5.81
13. PS 4.2 Visual defect inspection — 5.80
14. PS 7.2 Folk artist bookings — 5.79
15. PS 3.1 GST compliance — 5.79
16. PS 5.2 Village group buying — 5.53
17. PS 2.1 Gold purity and records — 5.20
18. PS 6.4 FPO supply forecasting — 5.03

The main driver of the spread is differentiation. GST, farmer price, artisan listing and job-portal statements have strong social value but are the "obvious app" zone where many teams will build similar things.

### 2.5 Top five at a glance

- **PS 4.1 (7.90):** a genuine agent loop tied to money on both the buying and the quoting side. Weakness: the supplier side must be simulated. Difficulty: medium.
- **PS 2.2 (7.41):** lowest build risk, niche, strong two-phone demo. Weakness: can look like a ledger, and needs correct gold-accounting terms. Difficulty: easy to medium.
- **PS 8.1 (7.18):** strongest human story and voice demo. Weakness: crowded theme, regional-language voice risk. Difficulty: medium.
- **PS 5.3 (6.89):** best measurable benchmark and map demo. Weakness: tagged Applied, weakly agentic, needs optimisation depth. Difficulty: medium to hard.
- **PS 3.2 (6.79):** highest importance and a SamarthLoan head start. Weakness: most obvious fintech statement, hardest to differentiate. Difficulty: medium.

### 2.6 Why PS 4.1 over the next two

- **Versus PS 2.2:** lower risk, but a narrower story that can read as record-keeping, and a wrong domain term in front of judges would hurt.
- **Versus PS 8.1:** a bigger emotional story, but a crowded theme and a real risk from regional-language voice quality.
- **For PS 4.1:** it is tagged Agentic, its own boundaries describe a guardrailed agent, it contains two measurable money problems, it fits the Java/Spring Boot/PostgreSQL/React stack without hardware, and it is a less common pick for student teams.

### 2.7 Red-flag review of PS 4.1

- **Too difficult?** No. The hard part is data, not algorithms.
- **Just a chatbot?** Only if built like one. The UI must show the agent trace and guardrails.
- **Is AI needed?** Yes, for messy multilingual text and drafting. Money math stays deterministic.
- **Too broad?** Yes if both loops are built in full, so the selling side stays thin and the MVP is limited to one vertical.
- **Easily copied?** The idea can be copied. The shared cost model and the guardrail rigour are harder to copy.
- **Weakest point:** supplier behaviour is simulated. It must be labelled.

### 2.8 Second-opinion review

A second written assessment agreed with PS 4.1. Its useful additions were adopted:

- Deadline-aware supplier selection: a late supplier is rejected even if it is cheapest.
- A clarification step where the system detects missing units, GST treatment or validity and drafts a follow-up.
- A what-if margin simulator on the quote screen.
- A prevented-loss metric, labelled as a simulated scenario.

Caveats: its 9/10 style ratings are also unmeasured, and supplier reliability or quality scores need data we do not have, so any such figures are synthetic and kept separate.

## 3. Product Scope

**Three screens and one audit view:**

- **Smart Procurement:** create a purchase request, add supplier quotations, extract fields, flag missing information, draft clarification messages, compare eligible suppliers, generate negotiation proposals.
- **Profitability Intelligence:** enter a customer order, add material, labour and overhead costs, generate a recommended quotation, change the selling price and see margin update, flag loss-making quotations.
- **Agent Activity and Approvals:** show agent steps, calculations and reasons, review drafts, approve or reject actions, view simulated prevented-loss scenarios.

**Must have:** RFQ creation, supplier quote intake, AI extraction, missing-field clarification, landed-cost calculation, deadline-aware supplier selection, negotiation draft, customer cost sheet, profitability what-if, human approval, agent audit trail, simulated prevented-loss metric.

**Optional:** voice-note extraction.

**Do not build:** a supplier marketplace, real WhatsApp integration, real payments, price forecasting, hardware integration, multi-industry support, or automated purchase commitments.

**MVP vertical:** sheet-metal fabrication only.

## 4. Architecture and Agent Workflow

**Stack:** React + TypeScript + Vite (frontend); Java + Spring Boot (REST APIs, orchestration, business rules, approvals); PostgreSQL / Supabase (data and audit); one LLM API (extraction and drafting); Java services (all calculations). Deployment: Vercel + Render or equivalent, with a working local demo as backup. Use the Java version already configured rather than changing toolchains.

**Backend modules (one deployable, not microservices):** supplier, procurement, quote-intake, clarification, cost-engine, supplier-selection, negotiation, profitability, approval, agent-audit.

**Five specialised steps, not seven independent agents:**

- **Intake Agent:** reads supplier messages and extracts price, quantity, unit, GST, freight, MOQ, validity and delivery date as JSON with confidence per field.
- **Clarification Agent:** detects missing units, ambiguous GST and missing validity, and drafts targeted follow-ups. It never invents missing values.
- **Java Cost and Eligibility Engine (deterministic):** validates units, calculates landed cost, checks delivery constraints, ranks eligible suppliers with explainable rules.
- **Procurement and Negotiation Agent:** explains the comparison, drafts counteroffers within owner limits, recommends a supplier and creates an approval request.
- **Quotation Agent:** builds the customer cost sheet, suggests a selling price from owner margin rules, flags potentially loss-making quotes.
- **Verifier and Approval (deterministic plus human gate):** recalculates values, checks owner policies, shows the reason for each warning. The owner approves or rejects every commercial action.

**Flow:** RFQ created → Intake → validate required fields → if missing, Clarification draft → owner reviews → simulated supplier response → re-validate → Java cost engine → deadline eligibility → supplier comparison → negotiation draft → owner approval.

Supplier replies are simulated with predefined responses, and the UI says so.

## 5. Data Model (11 core tables)

- **suppliers:** id, name, contact\_name, phone, location, is\_synthetic.
- **materials:** id, name, grade, specification, base\_unit (example: Mild Steel Sheet, 2 mm, IS 2062, kg).
- **rfqs:** id, material\_id, required\_quantity, required\_unit, required\_delivery\_date, status, created\_at.
- **supplier\_quotes:** id, rfq\_id, supplier\_id, unit\_price, quoted\_quantity, unit, gst\_rate, gst\_treatment, freight, other\_charges, moq, wastage\_percent, payment\_terms\_days, promised\_delivery\_date, valid\_until, raw\_message, extraction\_status, is\_synthetic.
- **clarification\_messages:** id, supplier\_quote\_id, missing\_fields (JSONB), drafted\_message, supplier\_response, status.
- **owner\_policies:** id, minimum\_margin\_percent, maximum\_purchase\_budget, cost\_weight, payment\_terms\_weight, minimum\_quote\_validity\_days.
- **customer\_enquiries:** id, customer\_name, product\_description, quantity, required\_delivery\_date, status.
- **bom\_lines:** id, enquiry\_id, material\_id, quantity\_required, wastage\_percent, estimated\_unit\_cost.
- **cost\_sheets:** id, enquiry\_id, material\_cost, labour\_cost, overhead\_cost, freight\_cost, total\_cost, suggested\_selling\_price, margin\_percent.
- **approvals:** id, reference\_type, reference\_id, proposed\_action, status, decided\_at.
- **agent\_runs:** id, workflow\_type, reference\_id, status, started\_at, completed\_at. Agent steps can be stored as JSONB inside a run, or in a separate agent\_steps table if time allows.

Authentication and multi-tenant support are deferred.

## 6. Calculation Rules

**Landed cost (MVP):** Material Cost + Freight + Non-creditable GST + Other Charges + Wastage Cost. Report the order total and the effective cost per usable unit.

- Recoverable GST is not an economic cost but is shown in cash payable.
- Non-creditable GST is included in economic cost.
- MOQ is considered when computing the purchase quantity.
- Payment terms are displayed as a comparison factor, not silently converted into a financial benefit.
- Never mix kg, sheets and pieces without an explicit conversion.

**Supplier selection in two stages:**

- **Stage 1, hard eligibility:** delivery deadline, material specification, required quantity or feasible MOQ, quote validity for the planned purchase. Failing suppliers are rejected with a reason.
- **Stage 2, explainable ranking:** among eligible suppliers, configurable owner weights (for example 0.8 cost and 0.2 payment terms, illustrative only).

Illustration: Supplier A at ₹100 on time and Supplier B at ₹103 on time are eligible. Supplier C at ₹96 is late and is rejected, so A is recommended and the reason is shown.

**Profitability:** Total Cost = Material + Labour + Overheads + Freight. Profit = Selling Price − Total Cost. Margin % = Profit ÷ Selling Price × 100. The margin recalculates whenever the owner changes the price. Show a loss warning below total cost and a warning below the owner's minimum margin.

## 7. API Design

- POST /api/rfqs, GET /api/rfqs: create and list purchase requests.
- POST /api/rfqs/{id}/quotes: add a supplier quote.
- POST /api/quotes/{id}/extract: extract quotation fields.
- POST /api/quotes/{id}/clarify: generate a clarification draft.
- POST /api/quotes/{id}/clarify-response: submit a simulated response.
- GET /api/rfqs/{id}/comparison: compare supplier quotations.
- POST /api/rfqs/{id}/negotiate: generate a counteroffer.
- POST /api/enquiries: create a customer enquiry.
- GET /api/enquiries/{id}/cost-sheet: calculate order cost.
- POST /api/enquiries/{id}/quote: generate a customer quotation.
- POST /api/cost-sheets/{id}/simulate: recalculate margin for a new price.
- POST /api/approvals/{id}/decision: approve or reject.
- GET /api/agent-runs/{id}: view the agent execution trace.
- GET /api/analytics/summary: demo metrics.

Plain REST polling is enough. Add SSE or WebSockets only if everything else is finished.

## 8. Team Allocation and 3-Day Schedule

Four members, about 9–10 productive hours per day. Each person owns a deliverable to avoid editing the same files.

- **Member 1, Backend and Database Lead:** Spring setup, entities, repositories, controllers, RFQ and quote APIs, cost engine, eligibility, integration support.
- **Member 2, AI and Agent Workflow Lead:** extraction prompts and JSON schema, missing-field workflow, clarification and negotiation drafts, agent trace and audit logging, synthetic supplier messages and responses.
- **Member 3, Frontend Lead:** dashboard, procurement and quote intake, supplier comparison, profitability and margin simulator, approval and agent activity views.
- **Member 4, Integration, QA and Demo Lead:** demo scenario, realistic synthetic quotations, end-to-end tests, calculation checks, presentation, architecture diagram, demo script.

**Coordination rule:** Members 1 and 2 agree on API contracts on Day 1. Member 3 uses mock JSON until the backend is ready. Member 4 owns the shared demo dataset and test checklist.

**Day 1, foundation and procurement backend.** Set up Spring Boot, React and the database. Build RFQ and quote APIs, the first cost-engine pieces, extraction of at least one quote, clarification drafts, the intake and comparison UI, and a stable API contract. Do not perfect the dashboard design.

**Day 2, agent workflow and profitability.** Complete landed cost and eligibility logic, the clarification workflow, negotiation drafts within owner limits, audit logs, the cost-sheet API and margin calculation, the profitability UI and margin simulator, and the approval API and screen. End of day: late suppliers are rejected, missing information triggers clarification, and the owner can change the price and see the margin move.

**Day 3, integration, reliability and presentation.** Morning for bug fixes, validation, error states and a full regression test. Freeze new features by 1 PM. Afternoon for deployment, final integration, presentation, demo rehearsal, and a backup recording or screenshots.

## 9. Demo Scenario (3 minutes)

**Story:** a sheet-metal fabrication owner needs 500 kg of mild steel sheet, 2 mm, delivered within 3 days. All supplier identities and messages below are fictional.

- **Supplier A:** 500 kg MS sheet 2mm, ₹95/kg, GST extra at 18%, freight ₹1,500, delivery in 2 days, 7-day validity.
- **Supplier B:** MS 2mm, 500 kg, 96 rupees per kg, freight included, delivery tomorrow, payment 15 days, GST as applicable.
- **Supplier C:** MS sheet 2mm, ₹90/kg, 500 kg, freight ₹3,000, delivery in 6 days.

**Sequence:**

- 0:00–0:20: the problem in the PS's own words (5–10 supplier calls, approximate costing).
- 0:20–0:50: the three messy messages arrive and the Intake Agent extracts the fields.
- 0:50–1:30: the Clarification Agent notices Supplier B has no explicit GST treatment or validity and drafts a follow-up. The Java engine compares landed cost and rejects Supplier C for missing the deadline.
- 1:30–2:05: the negotiation draft for the preferred eligible supplier, then owner approval.
- 2:05–2:35: a customer enquiry becomes a cost sheet. The owner changes the price and watches the margin move, and a loss-making price is flagged.
- 2:35–3:00: the agent trace, why Supplier C was rejected, what the owner approved, and the simulated prevented-loss scenario, labelled as such.

## 10. Likely Judge Questions and Honest Answers

- **Why use an LLM at all?** Only for unstructured text and drafting. Money math is deterministic Java.
- **How accurate is the parsing?** We report the tested fields and the actual errors on a small labelled synthetic set. Low-confidence fields need owner confirmation. We do not claim production accuracy.
- **Are the suppliers real?** No, they are simulated and labelled. The guardrails and logic are real. A real channel such as WhatsApp Business is a next step.
- **What stops the agent from over-committing?** A policy engine, and agents have no award or payment tools. Only the human approval endpoint can commit.
- **How do you handle price volatility?** Quotes carry validity dates and stale quotes are flagged. We make no price prediction claims.
- **Could the LLM hallucinate numbers?** It never produces final numbers. The verifier recomputes them.
- **Why not use an existing B2B marketplace?** Those ask the owner to move to a new platform. MUNIM starts from the quotes the owner already receives. This is our reasoning, not a verified market claim.
- **Who pays?** Hypotheses to test: the owner directly, or industrial associations and clusters (listed as stakeholders in the PS) for pooled purchasing.
- **Does it scale?** The design can become multi-tenant per unit, with new verticals added through materials and BOM templates.
- **What about privacy?** The MVP uses synthetic data, secrets stay in environment variables, and real deployment would need authentication and tenant isolation.

## 11. Testing Checklist

- Supplier quote with a missing unit: flagged for clarification.
- Ambiguous GST: no GST assumption is invented.
- Late delivery: supplier excluded.
- Expired quote: flagged invalid.
- Lowest price but ineligible: not selected.
- Customer price below cost: loss warning.
- Margin below owner threshold: approval warning.
- Agent recommends a purchase: owner approval required.
- Malformed LLM JSON: validation error and a recovery path.
- Same request recalculated: identical Java result.

## 12. Risks and Mitigations

- **LLM API failure:** keep a manual quote entry fallback and cached responses for the demo.
- **Inconsistent extraction:** validate every structured field in Java.
- **GST mistakes:** explicit GST treatment and unit tests.
- **Too many features:** freeze scope to the must-have list.
- **Frontend and backend mismatch:** agree JSON contracts on Day 1 and use mocks.
- **Deployment failure:** keep a working local demo and a recording.
- **Unsupported impact claims:** label synthetic data and scenario metrics.

## 13. Open Items Before Building

- **Check for PS 1.x (Real Estate and Land).** The ZIP appears to be missing a set. If it exists, re-score it before committing.
- **Confirm the official submission requirements.** Nothing in the uploaded statements specifies them.
- **Field validation.** Speak to at least one real fabricator, even by phone, using the PS field questions: how many suppliers they call, how they compare quotes, how the last quote was worked out, and how often an order turned out to be a loss.
- **Repository setup.** Create the GitHub repository, agree on the API contracts, and split the first day's tasks.

## 14. Field Validation and Scope Boundaries

PS 4.1 lists six "Questions to take into the field." They are questions to put to real factory owners, not features. This section records what MUNIM addresses for each one, where it stops, and what to ask. The solution and build plan above do not change.

**1. How many suppliers does the owner call for a typical purchase, and how long does it take?**

- MUNIM addresses: collects all supplier quotes in one place and compares them, which reduces repeated calling.
- Gap: the real number and time are field facts we do not yet have. They are the "before" baseline.
- Ask the owner: how many suppliers for the last purchase, and how long from first call to decision.

**2. How are quotes compared, and which costs get missed?**

- MUNIM addresses: landed cost covers freight, GST treatment, other charges, MOQ, wastage and payment terms.
- Gap: the costs owners actually miss may differ from our list.
- Ask the owner: which costs surprised them after buying, and check them against the landed-cost formula.

**3. How was the last quote to a buyer worked out?**

- MUNIM addresses: a cost sheet with material, labour, overheads and freight, and a margin simulator.
- Gap: labour and overhead are entered by hand, and real shops may include items the MVP does not model, such as outsourced processes or machine time.
- Ask the owner: walk through the last quote line by line.

**4. How often has an order turned out to be a loss?**

- MUNIM addresses: warns when the price is below total cost or the margin is below the owner's minimum.
- Gap: the frequency is a field fact, and detection is only as good as the cost inputs.
- Ask the owner: how many recent orders lost money, and why they were not caught earlier.

**5. Where do new buyer enquiries come from today?**

- MUNIM addresses: nothing. The MVP starts once an enquiry already exists.
- Out of scope: a marketplace or buyer discovery is deliberately excluded from the 3-day build.
- Ask the owner anyway: it shapes the pitch and the roadmap.

**6. Would neighbouring units buy together?**

- MUNIM addresses: nothing in the MVP. Pooled buying is a possible future extension.
- Out of scope: not in the build plan.
- Ask the owner: whether they would buy jointly, and who they would trust to coordinate.

**How to use this section**

- Ask a real fabricator at least questions 1–4 before or during the build, and record the answers here.
- In the pitch, state clearly that questions 5 and 6 are future work.
- If no owner can be reached, say plainly that the supplier and cost data are synthetic and these questions are still unvalidated.
- Do not claim that MUNIM answers all six questions.

## 15. Final Advice

**Final advice:** build the complete procurement flow first, then connect it to profitability. The biggest mistake would be spending two days on an impressive dashboard and finding on the last day that the calculations, API integration or approval flow do not work.

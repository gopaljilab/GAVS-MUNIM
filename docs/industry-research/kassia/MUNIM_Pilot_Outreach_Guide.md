# MUNIM — MSME Pilot Outreach & Field Validation Guide

> **Confidentiality & Compliance Notice:**  
> This document is an internal research, interview preparation, and engagement framework for the MUNIM engineering and product team. **No unsolicited emails, phone calls, or WhatsApp messages should be dispatched without prior authorization.** All outreach materials are designed strictly for voluntary industry-validation interviews with Karnataka MSME sheet-metal fabrication business owners.

---

## 1. Introduction to MUNIM

**MUNIM** is an intelligent commercial operating system designed specifically for micro, small, and medium-scale sheet-metal fabrication enterprises (MSMEs).

In typical Indian sheet-metal job shops (5 to 50 employees), owners and estimators routinely prepare customer quotations using informal spreadsheets, paper job cards, or manual thumb-rules. However, with volatile raw steel prices (Hot Rolled Coil, Cold Rolled Coil, Galvanized Iron, and Stainless Steel), fabricators face severe margin erosion:
- **Raw steel accounts for a dominant share of total material cost in sheet-metal assemblies.**
- Local steel distributors quote rates with short validity windows (24 to 48 hours) via fragmented WhatsApp messages and phone calls.
- Quotations sent to buyers frequently miss hidden landed costs (freight surcharges, loading/unloading, cutting losses, scrap recovery offsets, and GST Input Tax Credit timing differences).

MUNIM addresses this operational challenge through a unified three-step operating discipline:

$$\textbf{BUY SMARTER} \longrightarrow \textbf{CALCULATE ACCURATELY} \longrightarrow \textbf{QUOTE PROFITABLY}$$

### Core Implemented Platform Capabilities
1. **Quotation Ingestion & Landed Cost Comparison (Buy Smarter):**  
   Ingests multi-supplier raw material quotations (HRC, CRC, GI, SS), normalizes base rates against credit terms, freight charges, and GST ITC eligibility (`FinancialEvaluator.java`), and identifies the true lowest landed cost per kilogram. Features AI-assisted raw quote extraction (`QuoteIntelligenceService.java`).
2. **Deterministic Java BOM Costing Engine (Calculate Accurately):**  
   Calculates precise component costs in `ProfitabilityEngine.java` v1.0:
   - Evaluates gross material quantity based on net quantity and user-specified scrap percentage:  
     $$\text{Gross Quantity} = \text{Net Quantity} \times \left(1 + \frac{\text{Scrap Percentage}}{100}\right)$$
   - Computes dynamic scrap recovery credit:  
     $$\text{Scrap Credit} = (\text{Gross Quantity} - \text{Net Quantity}) \times \text{Scrap Recovery Price}$$
   - Evaluates setup costs, machine cycle hours, and labour hours per part.
   - Applies overhead multipliers, packaging, and outbound freight.
3. **Owner-Gated Profitability Governance (Quote Profitably):**  
   Enforces cryptographic snapshot integrity (SHA-256 canonical hashing) and role-based segregation of duties:
   - **Procurement Buyer (`ROLE_PROCUREMENT`):** Requests, records, and normalizes supplier quotes.
   - **Estimator (`ROLE_ESTIMATOR`):** Builds technical BOMs, enters scrap percentages, and defines operations.
   - **Business Owner (`ROLE_OWNER`):** Retains exclusive approval authority over final customer markups, commercial payment terms, and quotations below target gross margin thresholds (e.g., < 15%).

### Clarified Product Boundaries (Future Roadmap):
- **No Automated 2D CAD Nesting:** MUNIM does not parse CAD DXF/DWG files or perform automated geometric polygon nesting; net part quantity and scrap percentage are input parameters.
- **No Live Steel Ticker Feeds:** MUNIM does not connect to live stock/commodity exchange ticker APIs; quotes are ingested from entered supplier messages.
- **No Automatic PDF Rendering Library:** The backend delivers structured JSON quotation DTOs via REST API; external PDF document rendering is a planned future addition.
- **Client-Server Architecture:** MUNIM runs as a containerized web application (Spring Boot + React), not a standalone native desktop executable.

---

## 2. Professional Email Request Template

**Subject:** Request for 20-Min Industry Research Interview: Raw Material Costing & Steel Quotation Workflows in Karnataka MSMEs

**Recipient:** Managing Director / Proprietor / Partner  
**Target:** Senior Decision Maker at Fabricator (e.g., [SADBHAVA FABRICATORS(P) LTD.](file:///Users/gopalguptamac/Downloads/GAVS-MUNIM/docs/industry-research/kassia/MUNIM_KASSIA_Pilot_Leads.xlsx), [STAINLESS VESSELS FABRICATORS](file:///Users/gopalguptamac/Downloads/GAVS-MUNIM/docs/industry-research/kassia/MUNIM_KASSIA_Pilot_Leads.xlsx), etc.)

---

**Dear [Mr./Ms. Director's Last Name],**

Namaskara.

I am writing to you from the **MUNIM Project**, an engineering research initiative focused on developing digital commercial and production costing tools for sheet-metal fabrication and precision manufacturing MSMEs in Karnataka.

While reviewing the KASSIA (Karnataka Small Scale Industries Association) manufacturing directory, we noted **[Company Name]**'s longstanding presence in [Industrial Area, e.g., Peenya Industrial Area / Bommasandra] in the [Category, e.g., sheet-metal fabrication / press components / engineering] sector.

Given the significant volatility in domestic finished steel prices over recent months—where Hot Rolled and Cold Rolled coil prices fluctuate across local distributors—we are conducting a brief field study to understand how established MSME fabricators manage:
1. Fast-changing raw material quotations received from local steel stockists over WhatsApp and phone.
2. Accurate landed cost estimation (factoring in transportation, handling, cutting scrap, and outsourced finishing like powder coating/plating).
3. Protecting operating profit margins when submitting fixed-price quotations to OEMs and industrial buyers.

**We would be deeply grateful for a brief, 20-minute discussion (in person at your works or via phone/video call) at your convenience.**

**What this is:**
- An academic and engineering research conversation to understand real-world job-shop quotation bottlenecks.
- An opportunity for your team to share feedback that directly shapes software built for Indian MSME fabricators.

**What this is not:**
- We are not selling services, equipment, or commercial software licenses.
- We will never ask for client lists, confidential commercial pricing, or trade secrets. Any workflow examples shared will be fully anonymized.

Could you let us know if you or your senior estimation/commercial head would have 20 minutes available on **[Proposed Day 1, e.g., Tuesday]** or **[Proposed Day 2, e.g., Thursday]** between 11:00 AM and 4:00 PM?

Thank you for your valuable time and leadership in Karnataka's manufacturing ecosystem.

Warm regards,

**[Researcher Name]**  
Lead Research Engineer — Project MUNIM  
Phone: +91 [XXXXXXXXXX]  
Email: research@munim-app.org  
Bengaluru, Karnataka  

---

## 3. Concise WhatsApp Introduction Template

*Tone: Courteous, professional, crisp, culturally aligned with Indian MSME leadership.*

> **Namaskara [Mr./Ms. Owner Name] Sir/Madam,**  
>  
> Greetings from **Project MUNIM** (Bengaluru). We are an engineering research team developing commercial quotation and steel landed-cost management tools specifically for sheet-metal MSME fabricators in Karnataka.  
>  
> We found **[Company Name]** through the **KASSIA Directory** as an established fabrication workshop in [Industrial Area, e.g., Peenya 1st Stage].  
>  
> With current steel price fluctuations, we are conducting a short, 15-minute field research study on how workshop owners handle fast-changing supplier quotes (WhatsApp/phone) and protect their profit margins on customer RFQs.  
>  
> **We are not selling anything.** We are gathering practical MSME feedback to solve estimation leakage in job-shops.  
>  
> Would you or your estimation in-charge have 15 minutes for a brief phone call or workshop visit this week?  
>  
> Regards,  
> **[Researcher Name]** | Project MUNIM Bengaluru  
> Ph: +91 [XXXXXXXXXX]

---

## 4. Ten Structured Field Interview Questions

These 10 questions are structured around MUNIM's core workflow: **BUY SMARTER → CALCULATE ACCURATELY → QUOTE PROFITABLY**.

### Section A: Raw Material Procurement & Supplier Quotation Handling (Buy Smarter)
1. **Supplier Quote Formats & Velocity:**  
   *"When you receive an RFQ requiring sheet metal (CRCA, HRC, or SS), how many distributors do you typically consult, and how do their quotes reach you (WhatsApp text, PDF image, phone calls)?"*
2. **Landed Cost Normalization:**  
   *"When comparing quotes between two distributors—say, one offering a lower ex-godown rate and another offering a delivered rate with 30 days credit—how do you calculate the true landed cost including local freight, unloading, and GST Input Tax Credit?"*
3. **Quote Validity & Price Expiry:**  
   *"How long are supplier raw material quotes valid for in today's market? What happens if your customer takes several weeks to approve your quotation and steel prices increase in the interim?"*

### Section B: Production Costing, Scrap & BOM Complexity (Calculate Accurately)
4. **Scrap Allowance & Net Material Recovery:**  
   *"In your typical sheet-metal components, what percentage of the sheet ends up as off-fall/scrap? Do your estimators credit the recovered scrap value back to the customer quote, or is that treated as a workshop buffer?"*
5. **Machine Hour Rate & Setup Costs:**  
   *"How do you calculate machine operating costs for CNC cutting, press brake bending, and welding? Do you use a flat hourly rate, a per-meter/per-stroke rate, or a general percentage markup over material?"*
6. **Outsourced Processing Leakage:**  
   *"When a fabricated part requires external finishing (e.g., powder coating, zinc electroplating, or hot-dip galvanizing), how do you estimate those costs? Do unexpected minimum batch charges or transport rejections ever affect your margin?"*
7. **BOM Revision Tracking:**  
   *"When a customer sends a revised drawing (e.g., Revision B changing sheet thickness or hole patterns), how does your workshop ensure that old quotes aren't accidentally approved or manufactured?"*

### Section C: Commercial Approval, Margin Protection & Owner Governance (Quote Profitably)
8. **Owner Oversight & Approval Bottlenecks:**  
   *"In your business, does the owner personally approve every quotation before it goes out, or do estimators have authority up to a certain rupee limit? How much time does management spend reviewing quotes?"*
9. **Realized vs. Quoted Margin Variance:**  
   *"At the end of a completed fabrication batch, do you measure the actual realized gross margin against what was originally quoted? Where does the biggest margin variance typically occur (material price rise, labor overtime, excessive scrap, or logistics)?"*
10. **Quotation Turnaround Time vs. Win Rate:**  
    *"What is your current average turnaround time for an engineering RFQ (e.g., 4 hours, 24 hours, 3 days)? Does faster quotation turnaround directly increase your win rate with commercial clients?"*

---

## 5. Permission Request for Anonymized Historical Data

To calibrate MUNIM's calculation engine with real Karnataka fabrication benchmarks, the team will request voluntary access to 2–3 historical quotation sheets.

### Protocol for Historical Costing Examples:
```markdown
"Dear [Owner Name],

To ensure our calculation formulas match the exact realities of Karnataka fabrication job-shops, would you be open to sharing 2 or 3 completed, historical quotation sheets from past projects (6+ months old)?

You may completely redact or blackout:
- Customer names and contact details.
- Proprietary part part-numbers and drawing title blocks.
- Exact vendor supplier names.

All we require is the mathematical structure:
- Raw sheet blank dimensions, thickness, and material grade.
- Raw steel purchase price per kg and scrap recovery assumption.
- Machine process steps (cutting time, bending strokes, welding).
- Target markup percentage and final quoted price.

This data will be used exclusively inside our test suite to verify calculation accuracy."
```

---

## 6. Confidentiality, Data Protection & IP Governance

Karnataka MSME owners are rightfully protective of their supplier relationships, pricing formulas, and customer drawings. MUNIM adheres to strict data protection standards:

| Governance Principle | Protocol & Engineering Implementation |
| :--- | :--- |
| **Strict Non-Disclosure (NDA)** | Prior to any interview or data review, MUNIM provides a signed, one-page mutual Non-Disclosure Agreement covering all verbal and written disclosures. |
| **Complete Anonymization** | All company names, customer identities, and supplier IDs are immediately masked into synthetic identifiers (e.g., `FAB-BLR-01`, `STEEL-DIST-A`). |
| **Zero Git / Cloud Leakage** | No real-world company names, addresses, phone numbers, or quotation numbers will be entered into MUNIM's application source code, Docker demo seeds, or public GitHub repositories. |
| **Local-Only Processing** | MUNIM operates on an offline-capable, containerized architecture. Pilot data remains on the fabricator's premises or isolated local environments without third-party cloud data harvesting. |
| **Right to Erasure** | Any participant may request the permanent deletion of their interview notes or anonymized test vectors at any time with zero retention. |

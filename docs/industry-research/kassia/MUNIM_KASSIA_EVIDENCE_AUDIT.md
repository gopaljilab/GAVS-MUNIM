# MUNIM — KASSIA Research Evidence Audit & Claim Correction

**Scope:** Formal verification audit and claim-correction record for all KASSIA industry research deliverables.  
**Applicable Deliverables:**
- [`docs/industry-research/kassia/MUNIM_KASSIA_Pilot_Leads.xlsx`](file:///Users/gopalguptamac/Downloads/GAVS-MUNIM/docs/industry-research/kassia/MUNIM_KASSIA_Pilot_Leads.xlsx)
- [`docs/industry-research/kassia/MUNIM_KASSIA_Industry_Research_Report.md`](file:///Users/gopalguptamac/Downloads/GAVS-MUNIM/docs/industry-research/kassia/MUNIM_KASSIA_Industry_Research_Report.md)
- [`docs/industry-research/kassia/MUNIM_Pilot_Outreach_Guide.md`](file:///Users/gopalguptamac/Downloads/GAVS-MUNIM/docs/industry-research/kassia/MUNIM_Pilot_Outreach_Guide.md)

**Authoritative Sources Inspected:**
1. *Karnataka Small Scale Industries Association (KASSIA) — Members Details Report, Volume 3* (`vol-3-2026_opt.pdf`, 325 pages, 3,692 member records).
2. *Ministry of Steel, Government of India — An Overview of Steel Sector (April 2026)* (`An Overview of Steel Sector for the month of April, 2026.pdf`, 7 pages).
3. *MUNIM Application Codebase* (Backend Spring Boot services, `ProfitabilityEngine.java`, controllers, entities, and React frontend).

---

## 1. Evidence Classification Framework

Every statement, parameter, and company profile attribute across the research deliverables is strictly classified according to the following evidentiary hierarchy:

| Evidentiary Classification | Definition & Standard of Proof | Application in Deliverables |
| :--- | :--- | :--- |
| **`DIRECTORY_VERIFIED`** | Explicitly supported by printed text in the KASSIA Member Details Report (`vol-3-2026_opt.pdf`). | Company legal name, member ID, admission date, serial number, registered physical address, printed phone numbers, email, representative names, legal designations, and printed trade descriptors. |
| **`EXTERNALLY_VERIFIED`** | Supported by an external official or public source, cited with exact document, table, page number, or URL and access date. | Macroeconomic figures from the Ministry of Steel April 2026 report (JPC / WSA tables); MCA portal company registration status. |
| **`INTERVIEW_REQUIRED`** | Operational, financial, or technical information that cannot be determined from directory listings and requires direct confirmation from the enterprise. | Current operating status, actual machinery installed, monthly steel tonnage, scrap generation percentage, customer types, distributor payment terms, and profit margins. |
| **`PRODUCT_PROPOSAL`** | A proposed MUNIM software use case, scenario, or architectural validation hypothesis; **not** an observed business fact or verified company workflow. | Proposing a three-persona approval workflow for a Private Limited fabricator; proposing landed cost comparison for job-order quotation; proposing scrap recovery offsets. |
| **`UNSUPPORTED`** | Statements lacking an identifiable source or speculative extrapolations. | **Strictly removed from all deliverables.** No speculative claims permitted. |

---

## 2. Claim Corrections & Removals Summary

### 2.1 Terminology Corrections
- **Population of Matches:**  
  *Previous phrasing:* "335 eligible industrial manufacturing enterprises identified."  
  *Corrected phrasing:* **"335 potential directory keyword matches identified across Volume 3."** (Keyword relevance in directory text does not constitute verification of current operational manufacturing capabilities).
- **Curated Database:**  
  *Previous phrasing:* "48 verified pilot participant businesses."  
  *Corrected phrasing:* **"48 curated potential pilot leads."** (These are uncontacted candidate leads for voluntary research interviews; none have been contacted, vetted, or agreed to participate).

### 2.2 Removal of Unsupported Claims
The following speculative assertions have been completely excised from the top 10 candidate profiles, lead notes, and research report:
1. **Monthly Steel Consumption:** Excised claims such as *"Consumes 15–30 metric tonnes of steel monthly"*. (The directory contains no tonnage or turnover data).
2. **Employee Headcount:** Excised claims such as *"Typical 10–25 employee workshop"*. (Employee counts are not recorded in KASSIA directory blocks).
3. **Machinery Installed:** Excised claims such as *"Operates heavy shears, bending presses, and MIG/TIG welding bays"* or *"Operates power presses (20T to 150T)"*. (Machinery lists are not printed in member listings).
4. **Scrap Rates:** Excised specific claims such as *"scrap reaches 15–22%"* or *"coil scrap is 20–30%"*. (Actual scrap off-fall varies by drawing geometry and must be determined via interview).
5. **Customer Types & Segments:** Excised claims such as *"Supplies Tier-1 automotive and electrical buyers"* or *"fabricates dairy/pharma equipment"*. (Unless explicitly printed in the company name, customer segments are interview questions).
6. **Commercial Supplier Relationships:** Excised claims such as *"Directly partnered with adjacent Hegganahalli powder coaters"* or *"Sources steel from SP Road dealers"*. (Supplier relationships cannot be asserted from geographic proximity alone).
7. **Operating Margins:** Excised claims such as *"eroding expected 12% margin down to 4–5%"*. (Financial margins are confidential and unobserved).

### 2.3 Physical Address Corrections
Auditing against the raw directory text blocks identified and corrected four address inaccuracies from the initial research draft:
- **`U0029` (UNIVERSAL FABRICATORS):** Previously listed as "Peenya 1st Stage". Corrected to **Rampura Road, Avalahalli, Virgonagara Post, Bengaluru - 560049** (East Bengaluru industrial corridor per Page 253, Sl 9963).
- **`S2089` (SAM ENGINEERS & FABRICATORS):** Previously listed as "Kamakshipalya". Corrected to **Plot No. 87 Industrial Estate, Udyambag, Belagavi - 590008** (Page 91, Sl 8118).
- **`X0001` (XL ENGINEERING & FABRICATORS):** Previously listed as "Bommasandra". Corrected to **Shed No. B-188, Peenya 2nd Stage, Peenya Indl. Estate, Bengaluru - 560058** (Page 317, Sl 10711).
- **`S1532` (S.B. CNC TECHNOLOGIES):** Corrected cluster to **Dabaspet KIADB Industrial Area, Bengaluru Rural - 562111** (Page 50, Sl 7634).
- **`S1762` (SRI VINAYAKA ENGINEERING WORKS):** Corrected cluster to **Vinobanagar, Kundawadar Road, Davanagere - 577002** (Page 67, Sl 7838).
- **`S0982` (SHRI CHANANI ALLOY STEELS P. LTD):** Confirmed Member ID is `S0982` (Page 10, Sl 7193), replacing the adjacent entry `S0983` (`SRI JAYALAKSHMI ENGG. WORKS`, Page 11, Sl 7194).

---

## 3. Evidence Audit for Top 10 Pilot Candidates

Each retained candidate profile is grounded in verified directory records, with clear separation between directory facts, product proposals, and interview requirements:

> [!NOTE]
> **Top 10 Candidate Ranking Methodology:**  
> The ranking of these top 10 candidates is a qualitative heuristic based strictly on directory text completeness, trade category alignment (sheet-metal fabrication, press components, machining), and industrial cluster accessibility (primarily Peenya, Doddanna, Hegganahalli, Andrahalli, and Avalahalli). **It does NOT represent or imply verified operational performance, manufacturing capacity, creditworthiness, or financial stability.**

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        TOP 10 CANDIDATE EVIDENCE MATRIX                                │
├────┬───────────┬─────────────────────────────────┬──────┬─────────┬───────────────────┤
│ #  │ Lead ID   │ Verified Legal Business Name    │ Page │ Sl No   │ Member ID / Adm   │
├────┼───────────┼─────────────────────────────────┼──────┼─────────┼───────────────────┤
│ 1  │ MUNIM-001 │ SADBHAVA FABRICATORS(P) LTD.    │ P. 4 │ Sl 7116 │ S0877 (26-08-2008)│
│ 2  │ MUNIM-005 │ STAINLESS VESSELS FABRICATORS   │ P. 13│ Sl 7219 │ S1013 (30-12-2009)│
│ 3  │ MUNIM-003 │ SHEETAL INDUSTRIAL FABRICATORS  │ P. 10│ Sl 7190 │ S0979 (17-09-2009)│
│ 4  │ MUNIM-002 │ SRI FABRICATORS                 │ P. 10│ Sl 7183 │ S0969 (27-08-2009)│
│ 5  │ MUNIM-016 │ SRI VINAYAKA PRESS COMPONENTS   │ P.102│ Sl 8238 │ S2222 (25-10-2016)│
│ 6  │ MUNIM-007 │ S.L.V. INDUSTRIES               │ P. 17│ Sl 7273 │ S1079 (29-11-2010)│
│ 7  │ MUNIM-020 │ UNIVERSAL FABRICATORS           │ P.253│ Sl 9963 │ U0029 (19-07-2007)│
│ 8  │ MUNIM-009 │ SRI MANJUNATHA FABRICATORS      │ P. 39│ Sl 7515 │ S1379 (28-01-2013)│
│ 9  │ MUNIM-010 │ SHREE DEVI FABRICATION          │ P. 46│ Sl 7593 │ S1479 (25-02-2014)│
│ 10 │ MUNIM-008 │ SRI SREENIVASA FABRICATOR       │ P. 38│ Sl 7501 │ S1361 (28-12-2012)│
└────┴───────────┴─────────────────────────────────┴──────┴─────────┴───────────────────┘
```

### Detailed Candidate Audits:

#### Candidate 1: SADBHAVA FABRICATORS(P) LTD.
- **Directory Verified Evidence (`[DIRECTORY_VERIFIED]`):**
  - Company Name: `SADBHAVA FABRICATORS(P) LTD.`
  - Source Record: Page 4 | Sl No 7116 | Member ID: `S0877` | Date of Admission: 26-08-2008.
  - Address: `# SB-52 4TH CROSS , 1ST STAGE , PEENYA INDL ESTATE BANGALORE 560058`.
  - Phones: `41170890, 9844722925`. Email: None listed in directory.
  - Representatives: `SESHADRI K.R. (Director)`, `KAMAL SESHADRI (Director)`.
  - Legal Form: Private Limited Company ((P) LTD.).
- **Proposed MUNIM Pilot Use Case (`[PRODUCT_PROPOSAL]`):**  
  Evaluate MUNIM's three-persona approval workflow (Procurement Buyer quote entry, Estimator BOM costing, Owner final sign-off) in an established Private Limited fabricator.
- **Parameters to Confirm in Interview (`[INTERVIEW_REQUIRED]`):**  
  Current operating status; actual sheet/plate thickness ranges; installed cutting/welding machines; monthly raw steel procurement tonnage; existing customer quote turnaround time.

#### Candidate 2: STAINLESS VESSELS FABRICATORS
- **Directory Verified Evidence (`[DIRECTORY_VERIFIED]`):**
  - Company Name: `STAINLESS VESSELS FABRICATORS`
  - Source Record: Page 13 | Sl No 7219 | Member ID: `S1013` | Date of Admission: 30-12-2009.
  - Address: `NO.117/D 9TH A CROSS, GANAPATHI NAGAR, 3RD PHASE PEENYA INDL.AREA BENGALURU 560058`.
  - Phones: `28360681/2933, 9481904327/9448852571`.
  - Emails: `spalani.svf@gmail.com`, `ss.vesselsfab@gmail.com`.
  - Representatives: `S.PALANI (Partner)`, `S. KUMAR (Partner)`.
  - Legal Form: Partnership Firm.
- **Proposed MUNIM Pilot Use Case (`[PRODUCT_PROPOSAL]`):**  
  Test MUNIM's quotation validity period rules and high-value stainless raw material BOM costing where material cost represents a large fraction of total cost.
- **Parameters to Confirm in Interview (`[INTERVIEW_REQUIRED]`):**  
  Current operating status; stainless steel grades used (SS 304, 316, etc.); typical distributor quotation validity windows; actual vessel/tank fabrication capabilities (trade specialization is inferred solely from company name `STAINLESS VESSELS FABRICATORS`, not verified equipment or ASME/pressure vessel certifications).

#### Candidate 3: SHEETAL INDUSTRIAL FABRICATORS
- **Directory Verified Evidence (`[DIRECTORY_VERIFIED]`):**
  - Company Name: `SHEETAL INDUSTRIAL FABRICATORS`
  - Source Record: Page 10 | Sl No 7190 | Member ID: `S0979` | Date of Admission: 17-09-2009.
  - Address: `N0 77 SRIGANDHANAGAR,HEGGANAHALLI BANGALORE 15TH CROSS BENGALURU 560091`.
  - Phones: `28363653, 28380345, 9845007124`. Email: `sheetalindfab@gmail.com`.
  - Representative: `M.NAGOJI RAO (Proprietor)`.
- **Proposed MUNIM Pilot Use Case (`[PRODUCT_PROPOSAL]`):**  
  Calibrate sheet-metal enclosure BOM templates and test dynamic scrap credit recovery rules in an MSME fabrication setting.
- **Parameters to Confirm in Interview (`[INTERVIEW_REQUIRED]`):**  
  Current operating status; component types manufactured; actual sheet off-fall scrap percentage; scrap disposal/resale practices; secondary finishing workflows.

#### Candidate 4: SRI FABRICATORS
- **Directory Verified Evidence (`[DIRECTORY_VERIFIED]`):**
  - Company Name: `SRI FABRICATORS`
  - Source Record: Page 10 | Sl No 7183 | Member ID: `S0969` | Date of Admission: 27-08-2009.
  - Address: `NO.2 PETE CHANNAPPA INDL.ESTATE , MAGADI ROAD , KAMAKSHIPALLYA BANGALORE 560079`.
  - Phones: `23484690, 23525489, 9845498252`. Email: `srifabrications@yahoo.co.in`.
  - Representative: `G.JANARDHANA (Proprietor)`.
- **Proposed MUNIM Pilot Use Case (`[PRODUCT_PROPOSAL]`):**  
  Benchmark job-order quotation preparation speed and evaluate supplier quote comparison against spot steel distributor rates.
- **Parameters to Confirm in Interview (`[INTERVIEW_REQUIRED]`):**  
  Current operating status; types of metal fabrication undertaken; local steel distributors used; typical turnaround time to quote customer RFQs.

#### Candidate 5: SRI VINAYAKA PRESS COMPONENTS
- **Directory Verified Evidence (`[DIRECTORY_VERIFIED]`):**
  - Company Name: `SRI VINAYAKA PRESS COMPONENTS`
  - Source Record: Page 102 | Sl No 8238 | Member ID: `S2222` | Date of Admission: 25-10-2016.
  - Address: `#17/1 8TH CROSS PATEL CHANNAPPA, INDUSTRIAL ESTATE ANDRAHALLI MAIN ROAD, NEAR PEENYA 2ND STAGE BENGALURU 560091`.
  - Phone: `9448507136`. Email: `SRIVINAYAKAPRESSCOMP@GMAI` (printed truncated in source).
  - Representative: `BASAVE GOWDA (Proprietor)`.
- **Proposed MUNIM Pilot Use Case (`[PRODUCT_PROPOSAL]`):**  
  Evaluate sheet-metal stamping costing and scrap rate deduction in progressive press operations.
- **Parameters to Confirm in Interview (`[INTERVIEW_REQUIRED]`):**  
  Current operating status; press tonnages available; sheet metals stamped; coil vs. sheet feeding; scrap off-fall tracking in component pricing.

#### Candidate 6: S.L.V. INDUSTRIES
- **Directory Verified Evidence (`[DIRECTORY_VERIFIED]`):**
  - Company Name: `S.L.V. INDUSTRIES`
  - Source Record: Page 17 | Sl No 7273 | Member ID: `S1079` | Date of Admission: 29-11-2010.
  - Printed Trade Descriptor (Line 1): `MACHINING & FABRICATORS`.
  - Address: `#47 9TH CRS, DODDANNA INDL ESTATE OPP ELRKE, NR PNY 2ND STG VISHWANEEDAM PO BENGALURU 560091`.
  - Phones: `28362402, 9964624604`. Email: `slv246@gmail.com`.
  - Representative: `VENKATESH.S.T (Proprietor)`.
- **Proposed MUNIM Pilot Use Case (`[PRODUCT_PROPOSAL]`):**  
  Test multi-operation job costing combining sheet metal cutting/bending with lathe/milling machine hours in a dual-trade workshop.
- **Parameters to Confirm in Interview (`[INTERVIEW_REQUIRED]`):**  
  Current operating status; split between machining vs fabrication revenue; machine hourly rate calculation method; raw material bar vs sheet sourcing.

#### Candidate 7: UNIVERSAL FABRICATORS
- **Directory Verified Evidence (`[DIRECTORY_VERIFIED]`):**
  - Company Name: `UNIVERSAL FABRICATORS`
  - Source Record: Page 253 | Sl No 9963 | Member ID: `U0029` | Date of Admission: 19-07-2007.
  - Address: `NO.60/8 9 10.RAMPURA ROAD, AVALAHALLI, VIRGONAGARA POST BENGALURU 560049`.
  - Phones: `28473022, 23351034, 9341821034`.
  - Emails: `universal_tanks@rediffmaiL.com`, `naveen.uf@gmail.com`.
  - Representative: `NAVEEN KUMAR S. (Proprietor)`.
- **Proposed MUNIM Pilot Use Case (`[PRODUCT_PROPOSAL]`):**  
  Evaluate plate fabrication costing, freight allocation, and quotation versioning in an established East Bengaluru workshop.
- **Parameters to Confirm in Interview (`[INTERVIEW_REQUIRED]`):**  
  Current operating status; actual tank/structural fabrication capabilities; steel plate thickness handled; quotation preparation practices.

#### Candidate 8: SRI MANJUNATHA FABRICATORS
- **Directory Verified Evidence (`[DIRECTORY_VERIFIED]`):**
  - Company Name: `SRI MANJUNATHA FABRICATORS`
  - Source Record: Page 39 | Sl No 7515 | Member ID: `S1379` | Date of Admission: 28-01-2013.
  - Address: `# 6 4TH CROSS DODDANNA INDUSTRIAL, ESTATE MAIN ROAD, NEAR PEENYA 2ND STAGE BANGALORE 560091`.
  - Phones: `28362892, 9141930546, 9902065454`. Email: `csmf.2011@gmail.com`.
  - Representative: `M.CHANDRASHEKAR (Proprietor)`.
- **Proposed MUNIM Pilot Use Case (`[PRODUCT_PROPOSAL]`):**  
  Benchmark quotation turnaround speed against competitive bidding and test minimum margin protection guardrails.
- **Parameters to Confirm in Interview (`[INTERVIEW_REQUIRED]`):**  
  Current operating status; fabrication specializations; customer bid submission lead times; experiences with material price spikes on awarded contracts.

#### Candidate 9: SHREE DEVI FABRICATION
- **Directory Verified Evidence (`[DIRECTORY_VERIFIED]`):**
  - Company Name: `SHREE DEVI FABRICATION`
  - Source Record: Page 46 | Sl No 7593 | Member ID: `S1479` | Date of Admission: 25-02-2014.
  - Address: `Shed No.B-32, Khatha No.9/434/B-32, 3rd Stage of Peenya Industrial Area,, Nallakaderanahalli Village bangalore North Taluk BENGALURU 560058`.
  - Phones: `080-232846, 9731120192`. Email: `shreedevifabrication@gmail.com`.
  - Representative: `OMKARA MURTHY D.E. (Proprietor)`.
- **Proposed MUNIM Pilot Use Case (`[PRODUCT_PROPOSAL]`):**  
  Evaluate the usability of MUNIM's guided quotation wizard in a lean owner-operator fabrication shed.
- **Parameters to Confirm in Interview (`[INTERVIEW_REQUIRED]`):**  
  Current operating status; machinery on site; owner time spent on quotation estimation vs shop-floor supervision; quoting thumb-rules used.

#### Candidate 10: SRI SREENIVASA FABRICATOR
- **Directory Verified Evidence (`[DIRECTORY_VERIFIED]`):**
  - Company Name: `SRI SREENIVASA FABRICATOR`
  - Source Record: Page 38 | Sl No 7501 | Member ID: `S1361` | Date of Admission: 28-12-2012.
  - Address: `# 702,708 V.V.RAMU ESTATE, BOMMASANDRA INDL AREA, HOSUR ROAD BANGALORE 560099`.
  - Phones / Emails: None printed in directory entry block.
  - Representative: `P.KRUPAKARAN (Proprietor)`.
- **Proposed MUNIM Pilot Use Case (`[PRODUCT_PROPOSAL]`):**  
  Evaluate procurement dynamics and supplier quotation comparison in the Bommasandra / Hosur Road manufacturing corridor.
- **Parameters to Confirm in Interview (`[INTERVIEW_REQUIRED]`):**  
  Current operating status; contact phone number verification; fabrication trade specialization; raw steel procurement sources along Hosur Road.

---

## 4. Macroeconomic Figures Audit & Discrepancy Resolution

Every statistical figure cited in the research documentation was re-audited against the original Ministry of Steel April 2026 report (`An Overview of Steel Sector for the month of April, 2026.pdf`):

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        MINISTRY OF STEEL (APRIL 2026) AUDIT TABLE                      │
├───────────────────────────────┬─────────┬────────────┬───────────────┬─────────────────┤
│ Metric Description            │ Figure  │ Unit       │ Period        │ Source Location │
├───────────────────────────────┼─────────┼────────────┼───────────────┼─────────────────┤
│ Finished Steel Production     │ 161.70  │ Million T  │ FY 2025-26*   │ Page 1 Table 1  │
│ Finished Steel Consumption    │ 164.20  │ Million T  │ FY 2025-26*   │ Page 2 Table 2  │
│ Crude Steel Production        │ 169.20  │ Million T  │ FY 2025-26*   │ Page 2 Table 2  │
│ Pig Iron Production           │ 8.37    │ Million T  │ FY 2025-26*   │ Page 1 Table 1  │
│ Sponge Iron Production        │ 60.30   │ Million T  │ FY 2025-26*   │ Page 1 Table 1  │
│ Domestic Crude Steel Capacity │ 220.3   │ Million T  │ FY 2025-26    │ Page 1 Sec 1    │
│ Indian Per Capita Consumption │ 115.5   │ kg/capita  │ FY 2025-26    │ Page 1 Sec 1    │
│ World Crude Steel Production  │ 1,850.2 │ Million T  │ CY 2025       │ Page 1 Sec 1    │
│ China Crude Steel Production  │ 960.8   │ Million T  │ CY 2025       │ Page 1 Sec 1    │
│ India Finished Steel Exports  │ 6,601   │ Th. Tonnes │ FY 2025-26*   │ Page 3 Table 3  │
│ India Finished Steel Imports  │ 6,524   │ Th. Tonnes │ FY 2025-26*   │ Page 3 Table 3  │
│ Monthly Finished Production   │ 13.05   │ Million T  │ April 2026*   │ Page 1 Table 1  │
│ Monthly Finished Consumption  │ 12.99   │ Million T  │ April 2026*   │ Page 2 Table 2  │
│ Monthly Crude Production      │ 14.09   │ Million T  │ April 2026*   │ Page 2 Table 2  │
│ Monthly Finished Imports      │ 679     │ Th. Tonnes │ April 2026*   │ Page 4 Table 4  │
│ Monthly Finished Exports      │ 469     │ Th. Tonnes │ April 2026*   │ Page 4 Table 4  │
└───────────────────────────────┴─────────┴────────────┴───────────────┴─────────────────┘
*Note: JPC provisional data.
```

### Resolution of the 161.70 MT vs. 164.20 MT Discrepancy:
In the initial draft, 161.70 MT and 164.20 MT were both referenced without clear economic distinction. The audit confirms:
- **161.70 Million Tonnes** is the official **Finished Steel Production** (total alloy/stainless + non-alloy finished steel) in India during FY 2025–26 (Source: Joint Plant Committee, Table 1 & Table 2).
- **164.20 Million Tonnes** is the official **Finished Steel Consumption** in India during FY 2025–26 (Source: Joint Plant Committee, Table 2).
- **Economic Explanation:** In FY 2025–26, domestic finished steel consumption exceeded domestic finished steel production by **2.50 Million Tonnes**. This net gap was satisfied through steel inventory changes and import-export trade movements.

### Mandatory Methodological Limitation:
> [!IMPORTANT]
> **Macroeconomic Aggregate Non-Inference Rule:**  
> National production (161.70 MT) and consumption (164.20 MT) data describe macro commodity scale and industrial demand at the sovereign level. **They cannot and must not be used to infer MSME-level steel purchase prices, scrap generation percentages, factory operational costs, or profit margins.** MSME procurement operates under local distributor spot terms (retail spreads, freight surcharges, and credit premiums) that require empirical field interview confirmation.

---

## 5. MUNIM Functional Capability Alignment

To eliminate overclaiming, all product descriptions across research reports and outreach materials are strictly aligned with MUNIM's actual codebase:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        MUNIM IMPLEMENTATION STATUS AUDIT                               │
├────────────────────────────────────────┬─────────────────────┬─────────────────────────┤
│ System Capability                      │ Status              │ Architectural Evidence  │
├────────────────────────────────────────┼─────────────────────┼─────────────────────────┤
│ 1. Deterministic Java BOM Costing      │ IMPLEMENTED         │ ProfitabilityEngine.java│
│ 2. Dynamic Scrap Credit Recovery       │ IMPLEMENTED         │ ProfitabilityEngine.java│
│ 3. Conversion & Hourly Machine Costing │ IMPLEMENTED         │ ProfitabilityEngine.java│
│ 4. Overhead Multiplier, Freight & Pack │ IMPLEMENTED         │ ProfitabilityEngine.java│
│ 5. Realized Margin & Loss Category     │ IMPLEMENTED         │ ProfitabilityEngine.java│
│ 6. Cryptographic SHA-256 Snapshot Hash │ IMPLEMENTED         │ ProfitabilityEngine.java│
│ 7. Multi-Supplier Quote Ingestion      │ IMPLEMENTED         │ ProcurementService.java │
│ 8. Economic Landed Cost Normalization  │ IMPLEMENTED         │ FinancialEvaluator.java │
│ 9. AI-Assisted Quote Parser (LLM/Mock) │ IMPLEMENTED         │ QuoteIntelligenceService│
│ 10. Role Segregation (Buyer/Est/Owner) │ IMPLEMENTED         │ DemoAuthFilter, WebConf │
│ 11. Owner Minimum Margin Override Lock │ IMPLEMENTED         │ ApprovalService.java    │
│ 12. Automated 2D Sheet Nesting / CAD   │ PROPOSED FUTURE     │ NOT IN CODEBASE         │
│ 13. Live Steel Price Ticker API Feed   │ PROPOSED FUTURE     │ NOT IN CODEBASE         │
│ 14. Automatic CNC Toolpath / G-Code    │ PROPOSED FUTURE     │ NOT IN CODEBASE         │
│ 15. Automatic Customer Quotation PDF   │ PROPOSED FUTURE     │ NOT IN CODEBASE         │
│ 16. Standalone Offline Desktop SQLite  │ PROPOSED FUTURE     │ NOT IN CODEBASE         │
└────────────────────────────────────────┴─────────────────────┴─────────────────────────┘
```

### Clarified Technical Nuances:
1. **Automated Nesting vs. Input Scrap Percentage:**  
   MUNIM does **not** perform 2D CAD polygon nesting or read DXF/DWG engineering drawings. Instead, the user inputs the `netQuantity` and `scrapPercentage` (e.g. 15%), and the engine deterministically calculates `grossQuantity = netQuantity * (1 + scrapPercentage/100)` and applies the `scrapRecoveryPrice` to compute the net material cost.
2. **Real-Time Price Tickers vs. Spot Quotation Ingestion:**  
   MUNIM does **not** feature live commodity market tickers. Instead, it ingests spot quotations received from distributors (via WhatsApp text or manual entry) and computes true landed costs including credit terms, freight, and GST ITC.
3. **Quotation Generation vs. PDF Rendering:**  
   MUNIM generates structured, auditable quotation records via its REST API (JSON DTOs). It does **not** currently bundle a PDF compilation library (such as Apache PDFBox or iText) to render styled PDF documents automatically.
4. **Offline Desktop vs. Containerized Web Application:**  
   MUNIM is a client-server web application (Spring Boot 3 + PostgreSQL/H2 + React) deployable locally via Docker or localhost. It is **not** an offline-first desktop executable with local SQLite synchronization.

---

## 6. Audit of Initial Approach Recommendations

The initial outreach recommendations have been audited to ensure no research objective assumes unverified operational facts. Each approach objective is formulated strictly as an **interview hypothesis to explore**:

1. **SADBHAVA FABRICATORS(P) LTD.** (`S0877`, Page 4, Peenya 1st Stage):  
   *Interview Hypothesis:* In a multi-director Private Limited fabricator, commercial quote preparation suffers from organizational friction between procurement and final estimation; evaluate whether role separation (Buyer quote ingestion, Estimator BOM calculation, Owner margin gate) improves quotation discipline.
2. **STAINLESS VESSELS FABRICATORS** (`S1013`, Page 13, Peenya 3rd Phase):  
   *Interview Hypothesis:* Stainless steel fabrication workshops face disproportionate margin risk from short distributor quote validity windows (24–48 hours); test whether automated landed cost comparison and validity tracking prevent quoting under expired raw material prices.
3. **SHEETAL INDUSTRIAL FABRICATORS** (`S0979`, Page 10, Hegganahalli / Peenya):  
   *Interview Hypothesis:* Enclosure sheet fabricators experience margin erosion from unrecovered off-fall sheet scrap; test whether dynamic scrap credit recovery rules in BOM costing reflect actual shop-floor scrap disposal realizations.
4. **SRI MANJUNATHA FABRICATORS** (`S1379`, Page 39, Doddanna / Peenya 2nd Stage):  
   *Interview Hypothesis:* Competitive bidding pressures force estimators to compress quote turnaround times without validating landed costs; evaluate whether owner-gated minimum margin threshold locks prevent releasing loss-making bids.
5. **S.L.V. INDUSTRIES** (`S1079`, Page 17, Doddanna Indl. Estate):  
   *Interview Hypothesis:* Dual-trade workshops combining sheet metal fabrication and machine shop operations struggle to aggregate disparate hourly machine rates and material costs into a unified quotation.

---

## 7. Deliverables Consistency & Cross-Verification Summary

All four research deliverables are fully synchronized:

| Deliverable Asset | Exact Repository Path | Primary Audit Status |
| :--- | :--- | :--- |
| **Audited Excel Database** | [`docs/industry-research/kassia/MUNIM_KASSIA_Pilot_Leads.xlsx`](file:///Users/gopalguptamac/Downloads/GAVS-MUNIM/docs/industry-research/kassia/MUNIM_KASSIA_Pilot_Leads.xlsx) | 4 sheets, 48 unique leads (22 P1, 16 P2, 10 P3), zero duplicates, 43 Bengaluru / 5 Regional Karnataka distribution. |
| **Evidence Audit Document** | [`docs/industry-research/kassia/MUNIM_KASSIA_EVIDENCE_AUDIT.md`](file:///Users/gopalguptamac/Downloads/GAVS-MUNIM/docs/industry-research/kassia/MUNIM_KASSIA_EVIDENCE_AUDIT.md) | Authoritative source of verified facts, claim excisions, ranking heuristic note, and capability alignment. |
| **Executive Research Report** | [`docs/industry-research/kassia/MUNIM_KASSIA_Industry_Research_Report.md`](file:///Users/gopalguptamac/Downloads/GAVS-MUNIM/docs/industry-research/kassia/MUNIM_KASSIA_Industry_Research_Report.md) | Complete industry findings, 161.70 MT vs. 164.20 MT economic resolution, detailed geographic table, Top 10 profiles. |
| **Field Outreach Guide** | [`docs/industry-research/kassia/MUNIM_Pilot_Outreach_Guide.md`](file:///Users/gopalguptamac/Downloads/GAVS-MUNIM/docs/industry-research/kassia/MUNIM_Pilot_Outreach_Guide.md) | 10-question field interview guide, professional email/WhatsApp templates, NDA protocol, zero-unimplemented promises. |


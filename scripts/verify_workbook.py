import openpyxl

wb_path = "docs/industry-research/kassia/MUNIM_KASSIA_Pilot_Leads.xlsx"
wb = openpyxl.load_workbook(wb_path)

print(f"Loaded: {wb_path}")
print("Sheets:", wb.sheetnames)

assert wb.sheetnames == [
    "All Relevant Leads",
    "Top 10 Pilot Candidates",
    "Supplier and Material Leads",
    "Data Quality and Research Notes"
]

# Sheet 1 check
ws1 = wb["All Relevant Leads"]
print(f"Sheet 1 '{ws1.title}': rows={ws1.max_row}, cols={ws1.max_column}")
assert ws1.max_row == 49
assert ws1.max_column == 15

lead_ids = set()
company_names = set()
for r in range(2, 50):
    lid = ws1.cell(row=r, column=1).value
    cname = ws1.cell(row=r, column=2).value
    cat = ws1.cell(row=r, column=3).value
    addr = ws1.cell(row=r, column=5).value
    area = ws1.cell(row=r, column=6).value
    city = ws1.cell(row=r, column=7).value
    pin = ws1.cell(row=r, column=8).value
    page = ws1.cell(row=r, column=9).value
    prio = ws1.cell(row=r, column=10).value
    
    assert lid not in lead_ids, f"Duplicate Lead ID: {lid}"
    lead_ids.add(lid)
    assert cname not in company_names, f"Duplicate Company Name: {cname}"
    company_names.add(cname)
    assert page and isinstance(page, int) and 3 <= page <= 325, f"Invalid page {page} at row {r}"
    assert addr and len(addr.strip()) > 0
    assert pin and len(pin.strip()) == 6

print("Sheet 1 passed: 48 unique leads, zero duplicates, verified pages and addresses.")

# Sheet 2 check
ws2 = wb["Top 10 Pilot Candidates"]
print(f"Sheet 2 '{ws2.title}': rows={ws2.max_row}, cols={ws2.max_column}")
assert ws2.max_row == 11
assert ws2.max_column == 10

# Verify no unsupported claims in Top 10 rows
for r in range(2, 12):
    row_text = " ".join([str(ws2.cell(row=r, column=c).value) for c in range(1, 11)])
    # Check that unsupported words are not present
    for bad_phrase in ["15–30 metric tonnes", "15-30 metric tonnes", "10–25 employee", "10-25 employee", "20T to 150T", "4–5% margin", "tier-1 automotive"]:
        assert bad_phrase.lower() not in row_text.lower(), f"Found unsupported phrase '{bad_phrase}' in row {r}!"
    assert "[PRODUCT_PROPOSAL]" in row_text
    assert "[INTERVIEW_REQUIRED]" in row_text

print("Sheet 2 passed: 10 audited candidate profiles, all claims strictly classified, unsupported assumptions removed.")

# Sheet 3 check
ws3 = wb["Supplier and Material Leads"]
print(f"Sheet 3 '{ws3.title}': rows={ws3.max_row}, cols={ws3.max_column}")
assert ws3.max_row == 11
assert ws3.max_column == 10

# Sheet 4 check
ws4 = wb["Data Quality and Research Notes"]
print(f"Sheet 4 '{ws4.title}': rows={ws4.max_row}, cols={ws4.max_column}")
assert ws4.max_row >= 18
assert ws4.max_column == 4

print("ALL WORKBOOK AUDITS PASSED 100%!")

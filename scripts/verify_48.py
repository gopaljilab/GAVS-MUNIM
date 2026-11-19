import re
from pypdf import PdfReader

reader = PdfReader("vol-3-2026_opt.pdf")
total_pages = len(reader.pages)
header_pattern = re.compile(r"(?m)^(\d{4,5})\s+(\d{2}-\d{2}-\d{4})\s+([A-Z]\d{3,5})")
all_entries = {}

for page_idx in range(total_pages):
    page_num = page_idx + 1
    text = reader.pages[page_idx].extract_text()
    if not text:
        continue
    matches = list(header_pattern.finditer(text))
    for i, m in enumerate(matches):
        sl_no = m.group(1)
        adm_date = m.group(2)
        member_id = m.group(3)
        start_pos = m.end()
        end_pos = matches[i+1].start() if i + 1 < len(matches) else len(text)
        block = text[start_pos:end_pos].strip()
        lines = [line.strip() for line in block.split("\n") if line.strip()]
        all_entries[member_id] = {
            "page": page_num,
            "sl_no": sl_no,
            "adm_date": adm_date,
            "member_id": member_id,
            "raw": block,
            "lines": lines
        }

leads_to_check = [
    "S0877", "S0969", "S0979", "S1007", "S1013", "S1076", "S1079", "S1361", "S1379", "S1479",
    "S1637", "S1778", "S1887", "S2089", "S2206", "S2222", "S2679", "S2724", "S4153", "U0029",
    "U0109", "X0001",
    "S0884", "S0949", "S0976", "S1023", "S1190", "S1270", "S1305", "S1402", "S1454", "S1478",
    "S1503", "S1532", "S1541", "S1619", "S1762", "S1854",
    "S0983", "S2188", "S2210", "S3297", "V0333", "V0644", "S2604", "S1280", "S1372", "S0980"
]

print(f"Checking {len(leads_to_check)} leads...")
for i, lid in enumerate(leads_to_check):
    if lid not in all_entries:
        print(f"FAILED: {lid} not found!")
    else:
        e = all_entries[lid]
        name = e["lines"][0] if e["lines"] else ""
        pg = e["page"]
        sl = e["sl_no"]
        print(f"{i+1:2d}. {lid} | Page {pg:3d} | Sl {sl} | {name}")

import re
from pypdf import PdfReader

reader = PdfReader("vol-3-2026_opt.pdf")
total_pages = len(reader.pages)

header_pattern = re.compile(r"(?m)^(\d{4,5})\s+(\d{2}-\d{2}-\d{4})\s+([A-Z]\d{3,5})")
entries = []

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
        
        cname = lines[0] if lines else ""
        desc = ""
        
        if len(lines) > 1:
            line1 = lines[1]
            if re.search(r"^(?:PVT|LTD|LIMITED|ENGINEERS|FABRICATORS|INDUSTRIES|WORKS|COMPONENTS|SYSTEMS|ENTERPRISES|PRODUCTS)\b", line1, re.I):
                cname = lines[0] + " " + line1
            elif re.search(r"^(?:MACHINING|FABRICAT|MFG|MANUFACTUR)", line1, re.I):
                desc = line1

        entries.append({
            "page": page_num,
            "sl_no": sl_no,
            "adm_date": adm_date,
            "member_id": member_id,
            "cname": cname,
            "desc": desc,
            "raw": block
        })

p1_terms = re.compile(r"FABRICA|SHEET\s*METAL|PRESS\s*COMPONENT|PRESS\s*TOOL|ENCLOSURE|STAINLESS\s*VESSEL|STRUCTURAL\s*FAB|TANK\s*FAB|HEAVY\s*FAB|WELDING\s*WORKS", re.I)
p1 = [e for e in entries if p1_terms.search(e["cname"] + " " + e["raw"])]
print(f"Total P1 entries: {len(p1)}")
for i, e in enumerate(p1):
    pg = e['page']
    sl = e['sl_no']
    mid = e['member_id']
    cn = e['cname'][:40]
    print(f"{i+1:2d}. [P.{pg:3d} | Sl.{sl} | {mid}] {cn}")

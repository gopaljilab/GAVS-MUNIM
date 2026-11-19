import re
import os
from pypdf import PdfReader
import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter

# Load PDF and build index
print("Reading KASSIA directory PDF...")
reader = PdfReader("vol-3-2026_opt.pdf")
total_pages = len(reader.pages)
print(f"Total pages: {total_pages}")

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

print(f"Indexed {len(all_entries)} members from KASSIA Volume 3.")

# Test retrieval of known candidates
sample_ids = ["S0877", "S0969", "S0979", "S1013", "S1079", "S1379", "S1479", "S1637", "U0029", "S0980", "S1372", "S2188", "S2210", "S0949", "S1478"]
for sid in sample_ids:
    if sid in all_entries:
        e = all_entries[sid]
        print(f"Found {sid}: Page {e['page']}, Sl {e['sl_no']}, Name line: {e['lines'][0] if e['lines'] else 'N/A'}")
    else:
        print(f"Missing {sid}!")

import re
from pypdf import PdfReader

reader = PdfReader("vol-3-2026_opt.pdf")
total_pages = len(reader.pages)
print(f"Total pages: {total_pages}")

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
        
        company_name = lines[0] if lines else ""
        # Check if line 1 continues company name
        addr_start_idx = 1
        if len(lines) > 1:
            line1 = lines[1]
            if not re.search(r"^#|^NO|^PLOT|^SY|^SURVEY|^FLAT|^D\.NO|^C-|^B-|^A-|^[0-9]|INDL|ESTATE|ROAD|LAYOUT|STREET|CROSS|MAIN", line1, re.I) and not re.search(r"^\d{6}$", line1):
                if re.search(r"PVT|LTD|LIMITED|ENGINEERS|FABRICATORS|INDUSTRIES|WORKS|COMPONENTS|SYSTEMS|ENTERPRISES|PRODUCTS", line1, re.I):
                    company_name = line1_full = lines[0] + " " + line1
                    addr_start_idx = 2

        # Extract email
        emails = re.findall(r"[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+", block)
        email_str = emails[0] if emails else ""

        # Extract phones
        phone_matches = re.findall(r"(?:(?:\+91|0)?[6-9]\d{9})|(?:\b08\d{1,3}[-\s]?\d{6,8}\b)|(?:\b[2-4]\d{6,7}\b)", block)
        # Deduplicate phones
        phones = list(dict.fromkeys(phone_matches))
        phones_str = ", ".join(phones) if phones else ""

        # Extract PIN
        pins = re.findall(r"\b5\d{5}\b", block)
        pin_str = pins[0] if pins else ""

        # Extract representatives
        rep_matches = [line for line in lines if re.search(r"\(Proprietor\)|\(Partner\)|\(Director\)|\(Managing Director\)|\(MD\)|\(Manager\)|\(Chairman\)", line, re.I)]
        reps_str = "; ".join(rep_matches) if rep_matches else ""

        # City detection
        city = "BENGALURU"
        if re.search(r"MYSURU|MYSORE", block, re.I):
            city = "MYSURU"
        elif re.search(r"BELAGAVI|BELGAUM", block, re.I):
            city = "BELAGAVI"
        elif re.search(r"HUBLI|DHARWAD", block, re.I):
            city = "HUBLI-DHARWAD"
        elif re.search(r"MANGALORE|MANGALURU", block, re.I):
            city = "MANGALURU"
        elif re.search(r"SHIVAMOGGA|SHIMOGA", block, re.I):
            city = "SHIVAMOGGA"
        elif re.search(r"TUMKUR|TUMAKURU", block, re.I):
            city = "TUMAKURU"
        elif re.search(r"HASSAN", block, re.I):
            city = "HASSAN"
        elif re.search(r"DAVANAGERE", block, re.I):
            city = "DAVANAGERE"

        # Industrial area detection
        ind_area = "Bengaluru Urban"
        if re.search(r"PEENYA\s*(?:1ST|FIRST)?\s*(?:STAGE|PHASE)?", block, re.I):
            ind_area = "Peenya Industrial Area"
            if re.search(r"1ST\s*(?:STAGE|PHASE)", block, re.I):
                ind_area = "Peenya 1st Stage"
            elif re.search(r"2ND\s*(?:STAGE|PHASE)", block, re.I):
                ind_area = "Peenya 2nd Stage"
            elif re.search(r"3RD\s*(?:STAGE|PHASE)", block, re.I):
                ind_area = "Peenya 3rd Stage"
            elif re.search(r"4TH\s*(?:STAGE|PHASE)", block, re.I):
                ind_area = "Peenya 4th Stage"
        elif re.search(r"BOMMASANDRA", block, re.I):
            ind_area = "Bommasandra Industrial Area"
        elif re.search(r"JIGANI", block, re.I):
            ind_area = "Jigani Industrial Area"
        elif re.search(r"RAJAJINAGAR", block, re.I):
            ind_area = "Rajajinagar Industrial Estate"
        elif re.search(r"ANDRAHALLI|ANDHARAHALLI", block, re.I):
            ind_area = "Andrahalli / Peenya Extension"
        elif re.search(r"ABBIGERE", block, re.I):
            ind_area = "Abbigere Industrial Layout"
        elif re.search(r"DODDABALLAPUR", block, re.I):
            ind_area = "Doddaballapur Industrial Area"
        elif re.search(r"BIDADI", block, re.I):
            ind_area = "Bidadi Industrial Area"
        elif re.search(r"NELAMANGALA", block, re.I):
            ind_area = "Nelamangala Industrial Area"
        elif re.search(r"WHITEFIELD", block, re.I):
            ind_area = "Whitefield / EPIP Zone"
        elif re.search(r"ATTIBELE", block, re.I):
            ind_area = "Attibele Industrial Area"
        elif re.search(r"VEERASANDRA", block, re.I):
            ind_area = "Veerasandra Industrial Area"
        elif city != "BENGALURU":
            ind_area = f"{city} Industrial Cluster"

        # Clean address
        addr_lines = []
        for line in lines[addr_start_idx:]:
            if line == email_str or line in rep_matches or line == pin_str or line == city:
                continue
            if re.search(r"^[0-9/\-\s]+$", line) and len(line) < 15: # pure phone numbers line
                continue
            addr_lines.append(line)
        full_addr = ", ".join(addr_lines[:4])

        entries.append({
            "page": page_num,
            "sl_no": sl_no,
            "adm_date": adm_date,
            "member_id": member_id,
            "company_name": company_name.strip(),
            "raw_block": block,
            "full_addr": full_addr,
            "ind_area": ind_area,
            "city": city,
            "pin": pin_str,
            "email": email_str,
            "phones": phones_str,
            "reps": reps_str
        })

print(f"Total extracted: {len(entries)}")

# Classify
p1_terms = re.compile(r"FABRICA|SHEET\s*METAL|PRESS\s*COMPONENT|PRESS\s*TOOL|ENCLOSURE|STAINLESS\s*VESSEL|STRUCTURAL\s*FAB|TANK\s*FAB|HEAVY\s*FAB|WELDING", re.I)
p2_terms = re.compile(r"\bCNC\b|LASER|PRECISION|TOOL\s*(?:&|AND)?\s*DIE|MACHINE\s*(?:SHOP|WORKS|TOOL)|ENGINEERING\s*(?:WORKS|PRODUCTS|COMPONENTS|SYSTEMS)|AUTO(?:MOBILE)?\s*COMPONENTS?|DIE\s*CAST|STAMPING", re.I)
p3_terms = re.compile(r"STEEL\s*(?:TRAD|SUPPL|DEAL|CORP|CENTRE|HOUSE|ENTERPRISES|DISTRIB)|METALS?\s*(?:TRAD|SUPPL|CORP|CENTRE)|IRON\s*&?\s*STEEL|COAT(?:ING|ERS)|PLAT(?:ING|ERS)|GALVANIZ|HEAT\s*TREAT|SURFACE\s*FINISH", re.I)

p1 = [e for e in entries if p1_terms.search(e["company_name"] + " " + e["raw_block"])]
p3 = [e for e in entries if p3_terms.search(e["company_name"] + " " + e["raw_block"]) and e not in p1]
p2 = [e for e in entries if p2_terms.search(e["company_name"] + " " + e["raw_block"]) and e not in p1 and e not in p3]

print(f"P1 (Fabrication / Sheet metal / Press): {len(p1)}")
print(f"P2 (CNC / Precision / Tool & Die / Engg): {len(p2)}")
print(f"P3 (Steel & Material Suppliers / Finishing): {len(p3)}")

print("\n--- Top 15 P1 Candidates ---")
for e in p1[:15]:
    print(f"[P.{e['page']} | Sl.{e['sl_no']} | {e['member_id']}] {e['company_name']} | {e['ind_area']} | {e['city']} | Ph: {e['phones'][:25]} | Rep: {e['reps'][:30]}")

print("\n--- Top 10 P3 Candidates ---")
for e in p3[:10]:
    print(f"[P.{e['page']} | Sl.{e['sl_no']} | {e['member_id']}] {e['company_name']} | {e['ind_area']} | {e['city']} | Ph: {e['phones'][:25]}")

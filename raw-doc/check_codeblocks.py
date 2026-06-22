import os
os.chdir(r'D:\MEox\UITer\DOAN\Mosco_Megre\Mosco\raw-doc')
with open('03_chuong_3_phan_tich_thiet_ke.md', 'r', encoding='utf-8') as f:
    lines = f.readlines()
in_code = False
count = 0
for i, line in enumerate(lines):
    s = line.strip()
    if s.startswith('```'):
        in_code = not in_code
        count += 1
        label = "START" if in_code else "END"
        print(f'Line {i+1}: {label} code block')
print(f'Total: {count} toggles')

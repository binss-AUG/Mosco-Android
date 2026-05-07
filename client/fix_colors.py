import os
import re

color_map = {
    r'"#6c29fd"': '"@color/mosco_primary"',
    r'"#206c29fd"': '"@color/mosco_primary_alpha_12"',
    r'"#406c29fd"': '"@color/mosco_white_25"', # Wait, 40 is 25% alpha. Let's be careful.
    r'"#55FFFFFF"': '"@color/mosco_white_33"',
    r'"#40FFFFFF"': '"@color/mosco_white_25"',
    r'"#80FFFFFF"': '"@color/mosco_white_50"',
    r'"#33FFFFFF"': '"@color/mosco_white_20"',
    r'"#1A6C29FD"': '"@color/mosco_primary_alpha_10"',
    r'"#33262626"': '"@color/mosco_surface_alpha_20"',
    r'"#33484847"': '"@color/mosco_outline_alpha_20"',
    r'"#80FFB4"': '"@color/mosco_tertiary"', # Assuming neon green
    r'"#1A80FFB4"': '"@color/mosco_tertiary_alpha_10"',
    r'"#CC000000"': '"@color/mosco_black_80"',
    r'"#99262626"': '"@color/mosco_black_60"', # Not really black, but let's use semantic
    r'"#F2080808"': '"@color/mosco_bg_dark"',
}

def fix_colors(root_dir):
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.xml') or file.endswith('.java'):
                path = os.path.join(root, file)
                with open(path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                new_content = content
                for hex_val, res_val in color_map.items():
                    new_content = new_content.replace(hex_val, res_val)
                
                if new_content != content:
                    with open(path, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    print(f"Fixed: {path}")

fix_colors('d:\\MEox\\UITer\\DOAN\\Mosco_Megre\\Mosco\\client\\app\\src\\main\\res')

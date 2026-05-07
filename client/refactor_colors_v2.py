import os
import re

# Alpha mapping
alpha_map = {
    '00': '00', '0D': '05', '1A': '10', '26': '15', '33': '20', 
    '40': '25', '4D': '30', '55': '33', '66': '40', '80': '50', 
    '99': '60', 'B3': '70', 'CC': '80', 'D9': '85', 'E6': '90', 'F2': '95'
}

color_map = {
    r'#6C29FD': '@color/mosco_primary',
    r'#80FFB4': '@color/mosco_tertiary',
    r'#00E5FF': '@color/palette_cyan_neon',
    r'#1A1A2E': '@color/mosco_bg_deep_alt',
    r'#2A2C40': '@color/mosco_bg_galactic_start',
    r'#1E2033': '@color/mosco_bg_galactic_center',
    r'#151726': '@color/mosco_bg_galactic_end',
    r'#6B2FD4': '@color/mosco_primary',
    r'#E6E6FA': '@color/palette_pink_lavender',
    r'#B0E0E6': '@color/palette_blue_powder',
    r'#FFC0CB': '@color/palette_pink_soft_alt',
    r'#666666': '@color/palette_gray_800',
    r'#484847': '@color/mosco_outline',
    r'#262626': '@color/mosco_surface_variant',
    r'#080808': '@color/mosco_bg_dark',
}

def replace_hardcoded_colors(content, is_java=False):
    # Regex for #AARRGGBB or #RRGGBB
    def replacer(match):
        hex_val = match.group(0).upper().replace('"', '')
        
        # Exact matches first
        if hex_val in color_map:
            return f'"{color_map[hex_val]}"' if not is_java else f'R.color.{color_map[hex_val].split("/")[-1]}'

        # Handle white/black with alphas
        if hex_val.endswith('FFFFFF') and len(hex_val) == 9:
            alpha = hex_val[1:3]
            if alpha in alpha_map:
                return f'"@color/mosco_white_{alpha_map[alpha]}"'
        if hex_val.endswith('000000') and len(hex_val) == 9:
            alpha = hex_val[1:3]
            if alpha in alpha_map:
                return f'"@color/mosco_black_{alpha_map[alpha]}"'
        
        return match.group(0)

    # Simplified replacement for XML (quoted strings)
    if not is_java:
        return re.sub(r'"#[0-9A-Fa-f]{3,8}"', replacer, content)
    else:
        # For Java, we need to handle parseColor cases
        # strokePaint.setColor(Color.parseColor("#33FFFFFF")); -> strokePaint.setColor(ContextCompat.getColor(context, R.color.mosco_white_20));
        # This is more complex, let's just do literal string replacements for now.
        return re.sub(r'"#[0-9A-Fa-f]{3,8}"', replacer, content)

def process_dir(root_dir):
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.xml') or file.endswith('.java'):
                path = os.path.join(root, file)
                try:
                    with open(path, 'r', encoding='utf-8') as f:
                        content = f.read()
                    
                    new_content = replace_hardcoded_colors(content, is_java=file.endswith('.java'))
                    
                    if new_content != content:
                        with open(path, 'w', encoding='utf-8') as f:
                            f.write(new_content)
                        print(f"Refactored: {path}")
                except Exception as e:
                    print(f"Error processing {path}: {e}")

process_dir('d:\\MEox\\UITer\\DOAN\\Mosco_Megre\\Mosco\\client\\app\\src\\main')

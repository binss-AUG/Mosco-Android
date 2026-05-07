import os
import re

def fix_incorrect_require_context(root_dir):
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.java'):
                path = os.path.join(root, file)
                with open(path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                # Fix cases like parent.requireContext() -> parent.getContext()
                # We identify them as word.requireContext()
                new_content = re.sub(r'([a-zA-Z0-9_]+)\.requireContext\(\)', r'\1.getContext()', content)
                
                if new_content != content:
                    with open(path, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    print(f"Fixed Context call in: {path}")

fix_incorrect_require_context('d:\\MEox\\UITer\\DOAN\\Mosco_Megre\\Mosco\\client\\app\\src\\main\\java')

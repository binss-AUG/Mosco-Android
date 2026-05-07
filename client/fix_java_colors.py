import os
import re

def fix_java_colors(root_dir):
    # Pattern 1: Color.parseColor(R.color.name) -> ContextCompat.getColor(context, R.color.name)
    # Pattern 2: Color.parseColor("@color/name") -> ContextCompat.getColor(context, R.color.name)
    
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.java'):
                path = os.path.join(root, file)
                with open(path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                new_content = content
                
                # Fix Pattern 1: Color.parseColor(R.color.XXX)
                new_content = re.sub(
                    r'Color\.parseColor\(R\.color\.([a-zA-Z0-9_]+)\)', 
                    r'androidx.core.content.ContextCompat.getColor(getContext(), R.color.\1)', 
                    new_content
                )
                
                # Fix Pattern 2: Color.parseColor("@color/XXX")
                new_content = re.sub(
                    r'Color\.parseColor\("@color/([a-zA-Z0-9_]+)"\)', 
                    r'androidx.core.content.ContextCompat.getColor(getContext(), R.color.\1)', 
                    new_content
                )

                # Fix cases where context might be named differently or need getContext()
                # If the file is a Fragment, use requireContext()
                if "extends Fragment" in content:
                    new_content = new_content.replace('getContext()', 'requireContext()')
                
                if new_content != content:
                    with open(path, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    print(f"Fixed Java: {path}")

fix_java_colors('d:\\MEox\\UITer\\DOAN\\Mosco_Megre\\Mosco\\client\\app\\src\\main\\java')

import os
import re

# ==========================================
# MOSCO RESOURCE CLEANER TOOL (V1.0)
# Purpose: Systematize Zero-Hardcoding Mandate
# ==========================================

def scan_hardcoded_strings(root_dir):
    """Scans for hardcoded android:text=\"...\" in layouts."""
    pattern = re.compile(r'android:text=\"((?![@?]).+?)\"')
    results = []
    for root, _, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.xml'):
                path = os.path.join(root, file)
                with open(path, 'r', encoding='utf-8') as f:
                    matches = pattern.findall(f.read())
                    if matches:
                        results.append((path, matches))
    return results

def scan_hardcoded_dimensions(root_dir):
    """Scans for hardcoded dp/sp values in layouts."""
    pattern = re.compile(r'\"(\d+)(dp|sp)\"')
    results = []
    for root, _, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.xml'):
                path = os.path.join(root, file)
                with open(path, 'r', encoding='utf-8') as f:
                    matches = pattern.findall(f.read())
                    # Filter out common safe values like 0dp, 1dp, match_parent equivalents
                    filtered = [m for m in matches if m[0] not in ('0', '1')]
                    if filtered:
                        results.append((path, filtered))
    return results

def report():
    base_path = os.path.join('client', 'app', 'src', 'main', 'res', 'layout')
    if not os.path.exists(base_path):
        print("Error: Could not find layout directory.")
        return

    print("--- Hardcoded Strings Audit ---")
    strings = scan_hardcoded_strings(base_path)
    if not strings:
        print("Clean! No hardcoded strings found.")
    for path, matches in strings:
        print(f"{os.path.basename(path)}: {matches}")

    print("\n--- Hardcoded Dimensions Audit ---")
    dims = scan_hardcoded_dimensions(base_path)
    if not dims:
        print("Clean! No hardcoded dimensions found.")
    for path, matches in dims:
        print(f"{os.path.basename(path)}: {matches}")

if __name__ == "__main__":
    report()

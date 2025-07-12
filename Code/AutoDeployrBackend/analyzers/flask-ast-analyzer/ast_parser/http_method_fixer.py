#!/usr/bin/env python3
"""
HTTP Methods Fixer for Flask route decorators.
"""

import os
import re
import sys
import shutil

def fix_http_methods(file_path):
    """Fix HTTP methods in a single Python file."""
    print(f"Processing {file_path}")
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"Error reading file {file_path}: {e}")
        return False

    backup_path = f"{file_path}.bak"
    try:
        shutil.copy2(file_path, backup_path)
        print(f"Created backup at {backup_path}")
    except Exception as e:
        print(f"Error creating backup: {e}")

    original_content = content

    pattern = r'methods=\[([A-Z]+)\]'
    replacement = r"methods=['\1']"
    content = re.sub(pattern, replacement, content)

    pattern = r'methods=\[([A-Z]+), ([A-Z]+)\]'
    replacement = r"methods=['\1', '\2']"
    content = re.sub(pattern, replacement, content)

    if content != original_content:
        try:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"✅ Fixed HTTP methods in {file_path}")
            return True
        except Exception as e:
            print(f"Error writing file {file_path}: {e}")
            return False
    else:
        print(f"No changes needed for {file_path}")
        return True

def fix_directory(directory_path):
    """Fix HTTP methods in all Python files in a directory."""
    fixed_files = []
    file_count = 0

    print(f"Scanning directory: {directory_path}")

    for root, _, files in os.walk(directory_path):
        for file in files:
            if file.endswith('.py'):
                file_path = os.path.join(root, file)
                file_count += 1
                if fix_http_methods(file_path):
                    fixed_files.append(file_path)

    print(f"Processed {file_count} Python files, fixed {len(fixed_files)} files")
    return fixed_files

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage:")
        print("  python http_method_fixer.py <path_to_file>")
        print("  python http_method_fixer.py --dir <path_to_directory>")
        sys.exit(1)

    if sys.argv[1] == "--dir" and len(sys.argv) > 2:
        path = sys.argv[2]
        if os.path.isdir(path):
            fixed_files = fix_directory(path)
            print(f"Fixed HTTP methods in {len(fixed_files)} files")
        else:
            print(f"Error: {path} is not a directory")
            sys.exit(1)
    else:
        path = sys.argv[1]
        if os.path.isfile(path):
            if fix_http_methods(path):
                print(f"Fixed HTTP methods in {path}")
            else:
                print(f"No changes needed for {path}")
        else:
            print(f"Error: {path} is not a file")
            sys.exit(1)

    sys.exit(0)
#!/usr/bin/env python3
import json
import sys
import os
import argparse
import logging
from ast_parser.flask_parser import FlaskApplicationParser
from ast_parser.http_method_fixer import fix_http_methods, fix_directory

#logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def analyze_app(app_path, output_file=None, fix_methods=True):
    """Analyze Flask application and return or save result as JSON"""
    try:
        # Analysis
        parser = FlaskApplicationParser(app_path)
        result = parser.parse()

        # Convert to JSON
        result_json = json.dumps(result, indent=2)

        # Save or return
        if output_file:
            with open(output_file, 'w') as f:
                f.write(result_json)
            logger.info(f"Analysis saved to {output_file}")

            if fix_methods and os.path.exists(output_file):
                fix_http_methods(output_file)

            return 0
        else:
            print(result_json)
            return 0
    except Exception as e:
        logger.error(f"Error analyzing application: {str(e)}")
        print(json.dumps({"error": str(e)}))
        return 1

def analyze_file(file_path, output_file=None, fix_methods=True):
    """Analyze a single Python Flask file"""
    try:
        filename = os.path.basename(file_path)
        parser = FlaskApplicationParser("")
        result = parser.parse_file(file_path, filename)

        # Convert to JSON
        result_json = json.dumps(result, indent=2)

        # Save or return
        if output_file:
            with open(output_file, 'w') as f:
                f.write(result_json)
            logger.info(f"Analysis saved to {output_file}")

            # Fix HTTP methods if requested
            if fix_methods and os.path.exists(output_file):
                fix_http_methods(output_file)

            return 0
        else:
            print(result_json)
            return 0
    except Exception as e:
        logger.error(f"Error analyzing file: {str(e)}")
        print(json.dumps({"error": str(e)}))
        return 1

def fix_methods_in_directory(directory_path):
    """Fix HTTP methods in all Python files in a directory"""
    try:
        fixed_files = fix_directory(directory_path)
        if fixed_files:
            logger.info(f"Fixed HTTP methods in {len(fixed_files)} files:")
            for file in fixed_files:
                logger.info(f"  - {file}")
        else:
            logger.info("No files needed fixing.")
        return 0
    except Exception as e:
        logger.error(f"Error fixing HTTP methods: {str(e)}")
        return 1

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Analyze Flask applications using AST")
    parser.add_argument("--app-path", help="Path to Flask application directory")
    parser.add_argument("--file", help="Analyze a single Python file")
    parser.add_argument("--output", help="Output file for results (stdout if not specified)")
    parser.add_argument("--fix-methods", action="store_true", help="Fix HTTP methods in generated code")
    parser.add_argument("--fix-directory", help="Fix HTTP methods in all Python files in a directory")
    parser.add_argument("--no-fix", action="store_true", help="Don't fix HTTP methods in output files")

    args = parser.parse_args()

    # Default to fixing HTTP methods unless --no-fix is specified
    fix_methods = not args.no_fix

    if args.fix_directory:
        sys.exit(fix_methods_in_directory(args.fix_directory))
    elif args.app_path:
        sys.exit(analyze_app(args.app_path, args.output, fix_methods))
    elif args.file:
        sys.exit(analyze_file(args.file, args.output, fix_methods))
    else:
        parser.print_help()
        sys.exit(1)
#!/usr/bin/env python3
import ast

# Our example Flask app
flask_code = '''
from flask import Flask, jsonify, request
import os

app = Flask(__name__)

def validate_user(user_id):
    if user_id < 1:
        return False
    return True

@app.route('/users/<int:user_id>', methods=['GET'])
def get_user(user_id):
    if not validate_user(user_id):
        return jsonify({'error': 'Invalid user'}), 400
    
    db_url = os.getenv('DATABASE_URL')
    return jsonify({'user': user_id, 'db': db_url})

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({'status': 'ok'})
'''

print("=== PARSING FLASK APP ===")
tree = ast.parse(flask_code)

print("\n=== TOP-LEVEL AST NODES ===")
for i, node in enumerate(tree.body):
    print(f"Node {i}: {type(node).__name__}")
    if hasattr(node, 'lineno'):
        print(f"  Line: {node.lineno}")
    
    if isinstance(node, ast.ImportFrom):
        print(f"  Module: {node.module}")
        print(f"  Names: {[n.name for n in node.names]}")
    elif isinstance(node, ast.Import):
        print(f"  Names: {[n.name for n in node.names]}")
    elif isinstance(node, ast.Assign):
        if node.targets and isinstance(node.targets[0], ast.Name):
            print(f"  Variable: {node.targets[0].id}")
            if isinstance(node.value, ast.Call):
                print(f"  Assignment is a function call")
                if isinstance(node.value.func, ast.Name):
                    print(f"    Function name: {node.value.func.id}")
    elif isinstance(node, ast.FunctionDef):
        print(f"  Function: {node.name}")
        print(f"  Decorators: {len(node.decorator_list)}")
        if node.decorator_list:
            for j, dec in enumerate(node.decorator_list):
                print(f"    Decorator {j}: {type(dec).__name__}")
                if isinstance(dec, ast.Call) and isinstance(dec.func, ast.Attribute):
                    print(f"      Attribute: {dec.func.attr}")
                    if isinstance(dec.func.value, ast.Name):
                        print(f"      Object: {dec.func.value.id}")

print("\n=== DETAILED AST FOR FIRST ROUTE ===")
# Find the first route function (get_user)
for node in tree.body:
    if isinstance(node, ast.FunctionDef) and node.name == 'get_user':
        print(f"Function: {node.name}")
        print("Decorator details:")
        for dec in node.decorator_list:
            print(f"  {ast.dump(dec, indent=2)}")
        print("Function body (first few statements):")
        for i, stmt in enumerate(node.body[:2]):
            print(f"  Statement {i}: {ast.dump(stmt, indent=2)}")
        break 
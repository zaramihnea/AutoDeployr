import ast
from .models import ImportDefinition

class ImportCollector(ast.NodeVisitor):
    """Collects all imports from module with their line numbers and scope"""
    def __init__(self):
        self.imports = []
        self.import_lines = {}  # Maps ImportDefinition to line number
        self.current_function = None  # Track current function for scope

        # Track function-level imports separately
        self.function_imports = {}  # function_name -> [ImportDefinition]

        # Track variable references to detect import usage
        self.references = {}  # Maps variable name to list of line numbers where it's used

    def visit_Import(self, node):
        for name in node.names:
            alias = name.asname if name.asname else name.name
            import_def = ImportDefinition(name.name, alias)
            self.imports.append(import_def)
            self.import_lines[import_def] = node.lineno

            # If inside a function, record it as function-level import
            if self.current_function:
                if self.current_function not in self.function_imports:
                    self.function_imports[self.current_function] = []
                self.function_imports[self.current_function].append(import_def)

        self.generic_visit(node)

    def visit_ImportFrom(self, node):
        module = node.module if node.module else ''
        for name in node.names:
            alias = name.asname if name.asname else name.name
            if module:
                import_def = ImportDefinition(f"{module}.{name.name}", alias)
            else:
                import_def = ImportDefinition(name.name, alias)
            self.imports.append(import_def)
            self.import_lines[import_def] = node.lineno

            # If inside a function, record it as function-level import
            if self.current_function:
                if self.current_function not in self.function_imports:
                    self.function_imports[self.current_function] = []
                self.function_imports[self.current_function].append(import_def)

        self.generic_visit(node)

    def visit_FunctionDef(self, node):
        old_function = self.current_function
        self.current_function = node.name
        self.generic_visit(node)
        self.current_function = old_function

    def visit_Name(self, node):
        # Track variable references
        if isinstance(node.ctx, ast.Load):
            if node.id not in self.references:
                self.references[node.id] = []
            self.references[node.id].append(node.lineno)
        self.generic_visit(node)

    def visit_Attribute(self, node):
        # Track attribute references on imported modules
        if isinstance(node.value, ast.Name) and isinstance(node.ctx, ast.Load):
            if node.value.id not in self.references:
                self.references[node.value.id] = []
            self.references[node.value.id].append(node.lineno)
        self.generic_visit(node)

class FlaskAppVisitor(ast.NodeVisitor):
    """Identifies Flask application instances"""
    def __init__(self):
        self.flask_apps = set()
        self.flask_blueprints = set()

    def visit_Assign(self, node):
        for target in node.targets:
            if isinstance(target, ast.Name) and isinstance(node.value, ast.Call):
                # Detect: app = Flask(__name__)
                if isinstance(node.value.func, ast.Name) and node.value.func.id == 'Flask':
                    self.flask_apps.add(target.id)
                # Detect: api = Blueprint('api', __name__)
                elif isinstance(node.value.func, ast.Attribute):
                    if node.value.func.attr == 'Blueprint':
                        self.flask_blueprints.add(target.id)
        self.generic_visit(node)

class FunctionDefinitionVisitor(ast.NodeVisitor):
    """Collects all function definitions in a module with their complete source code"""
    def __init__(self, source_code):
        self.source_code = source_code
        self.functions = {}  # name -> {source, start_line, end_line, is_route}
        self.current_app = None  # Track current Flask app for route detection
        self.flask_apps = set()  # Set of Flask app names
        self.flask_blueprints = set()  # Set of Flask blueprint names

    def visit_FunctionDef(self, node):
        function_name = node.name
        is_route = False
        for decorator in node.decorator_list:
            if isinstance(decorator, ast.Call) and isinstance(decorator.func, ast.Attribute):
                if decorator.func.attr == 'route' and isinstance(decorator.func.value, ast.Name):
                    app_name = decorator.func.value.id
                    if app_name in self.flask_apps or app_name in self.flask_blueprints:
                        is_route = True
                        self.current_app = app_name
                        break
        function_source = ""
        try:
            if hasattr(ast, 'get_source_segment'):
                start_line = min([d.lineno for d in node.decorator_list]) if node.decorator_list else node.lineno
                start_pos = self._get_line_offset(self.source_code, start_line)
                end_pos = self._get_line_offset(self.source_code, node.end_lineno) + node.end_col_offset
                function_source = self.source_code[start_pos:end_pos]
        except Exception:
            pass
        if not function_source:
            try:
                source_lines = self.source_code.splitlines()
                end_line = getattr(node, 'end_lineno', None)
                if end_line is None:
                    def_line = source_lines[node.lineno - 1]
                    indent = len(def_line) - len(def_line.lstrip())

                    end_line = node.lineno
                    for i in range(node.lineno, len(source_lines)):
                        if i >= len(source_lines):
                            break
                        line = source_lines[i]
                        if line.strip() and len(line) - len(line.lstrip()) <= indent:
                            end_line = i
                            break
                        end_line = i + 1
                start_line = node.lineno
                if node.decorator_list:
                    start_line = min(d.lineno for d in node.decorator_list)
                function_source = "\n".join(source_lines[start_line-1:end_line])
            except Exception:
                try:
                    function_source = source_lines[node.lineno - 1]
                except:
                    function_source = f"def {node.name}():"
        self.functions[function_name] = {
            'source': function_source,
            'start_line': node.lineno,
            'end_line': getattr(node, 'end_lineno', node.lineno + 1),
            'is_route': is_route,
            'app_name': self.current_app if is_route else None
        }
        self.generic_visit(node)

    def _get_line_offset(self, source, line_number):
        """Get the character offset for a line number in the source"""
        lines = source.splitlines(True)  # Keep line breaks
        offset = 0
        for i in range(line_number - 1):
            if i < len(lines):
                offset += len(lines[i])
        return offset

    def set_flask_apps(self, apps, blueprints):
        """Set the Flask app and blueprint names to detect routes"""
        self.flask_apps = apps
        self.flask_blueprints = blueprints

class FlaskRouteVisitor(ast.NodeVisitor):
    """Identifies Flask routes with start and end line numbers"""
    def __init__(self, source_code, file_path, flask_apps=None, flask_blueprints=None):
        self.routes = []
        self.flask_apps = flask_apps or set()
        self.flask_blueprints = flask_blueprints or set()
        self.source_code = source_code
        self.file_path = file_path

    def visit_FunctionDef(self, node):
        for decorator in node.decorator_list:
            if isinstance(decorator, ast.Call) and isinstance(decorator.func, ast.Attribute):
                if decorator.func.attr == 'route' and isinstance(decorator.func.value, ast.Name):
                    app_name = decorator.func.value.id
                    if app_name in self.flask_apps or app_name in self.flask_blueprints:
                        path = None
                        methods = ['GET']

                        if decorator.args:
                            if isinstance(decorator.args[0], ast.Constant):
                                path = decorator.args[0].value
                            elif isinstance(decorator.args[0], ast.Str):
                                path = decorator.args[0].s

                        for keyword in decorator.keywords:
                            if keyword.arg == 'methods':
                                if isinstance(keyword.value, ast.List):
                                    methods = []
                                    for elt in keyword.value.elts:
                                        if isinstance(elt, ast.Constant):
                                            methods.append(elt.value)
                                        elif isinstance(elt, ast.Str):
                                            methods.append(elt.s)

                        if path:
                            function_source = self._get_source_segment(node)
                            start_line = node.lineno
                            end_line = self._get_function_end_line(node)

                            self.routes.append({
                                "name": node.name,
                                "path": path,
                                "methods": methods,
                                "source": function_source,
                                "file_path": self.file_path,
                                "app_name": app_name,
                                "line_number": node.lineno,
                                "start_line": start_line,
                                "end_line": end_line
                            })
        self.generic_visit(node)

    def _get_source_segment(self, node):
        try:
            if hasattr(ast, 'unparse'):  # Python 3.9+
                return ast.unparse(node)
            elif hasattr(ast, 'get_source_segment'):  # Python 3.8
                return ast.get_source_segment(self.source_code, node) or ""
            else:
                return self._extract_source_manually(node)
        except Exception:
            return self._extract_source_manually(node)

    def _extract_source_manually(self, node):
        """Manually extract source for older Python versions"""
        try:
            source_lines = self.source_code.splitlines()
            end_line = self._get_function_end_line(node)
            return "\n".join(source_lines[node.lineno-1:end_line])
        except Exception:
            return f"def {node.name}():"

    def _get_function_end_line(self, node):
        """Get the end line number of a function"""
        if hasattr(node, 'end_lineno'):
            return node.end_lineno
        source_lines = self.source_code.splitlines()
        start_line = node.lineno
        indent_level = None
        for line_num, line in enumerate(source_lines[start_line-1:], start_line):
            if line.strip() and not line.strip().startswith('#'):
                indent_level = len(line) - len(line.lstrip())
                break

        if indent_level is None:
            return start_line
        for line_num, line in enumerate(source_lines[start_line:], start_line + 1):
            if line.strip() and not line.strip().startswith('#'):
                curr_indent = len(line) - len(line.lstrip())
                if curr_indent <= indent_level:
                    return line_num - 1
        return len(source_lines)

class FunctionCallVisitor(ast.NodeVisitor):
    """Detects function calls within functions"""
    def __init__(self):
        self.function_calls = {}
        self.current_function = None

    def visit_FunctionDef(self, node):
        old_function = self.current_function
        self.current_function = node.name
        self.function_calls[node.name] = set()
        self.generic_visit(node)
        self.current_function = old_function

    def visit_Call(self, node):
        if self.current_function:
            if isinstance(node.func, ast.Name):
                self.function_calls[self.current_function].add(node.func.id)
            elif isinstance(node.func, ast.Attribute):
                if isinstance(node.func.value, ast.Name):
                    self.function_calls[self.current_function].add(f"{node.func.value.id}.{node.func.attr}")
        self.generic_visit(node)

class EnvVarDetector(ast.NodeVisitor):
    """Detects environment variable usage"""
    def __init__(self):
        self.env_vars = set()

    def visit_Call(self, node):
        if isinstance(node.func, ast.Attribute):
            if (node.func.attr == 'getenv' and isinstance(node.func.value, ast.Name) and
                    node.func.value.id == 'os' and node.args):
                if isinstance(node.args[0], ast.Constant) and isinstance(node.args[0].value, str):
                    self.env_vars.add(node.args[0].value)
                elif isinstance(node.args[0], ast.Str):
                    self.env_vars.add(node.args[0].s)

            elif (node.func.attr == 'get' and isinstance(node.func.value, ast.Attribute) and
                  node.func.value.attr == 'environ' and node.args):
                if isinstance(node.args[0], ast.Constant) and isinstance(node.args[0].value, str):
                    self.env_vars.add(node.args[0].value)
                elif isinstance(node.args[0], ast.Str):
                    self.env_vars.add(node.args[0].s)

        self.generic_visit(node)

    def visit_Subscript(self, node):
        if isinstance(node.value, ast.Attribute) and node.value.attr == 'environ':
            if isinstance(node.value.value, ast.Name) and node.value.value.id == 'os':
                if isinstance(node.slice, ast.Index):  # Python 3.8-
                    if isinstance(node.slice.value, ast.Str):
                        self.env_vars.add(node.slice.value.s)
                    elif isinstance(node.slice.value, ast.Constant):
                        if isinstance(node.slice.value.value, str):
                            self.env_vars.add(node.slice.value.value)
                elif isinstance(node.slice, ast.Constant):  # Python 3.9+
                    if isinstance(node.slice.value, str):
                        self.env_vars.add(node.slice.value)

        self.generic_visit(node)

class ImportUsageAnalyzer(ast.NodeVisitor):
    """Analyzes which imports are actually used within a function"""
    def __init__(self, imports):
        self.imports = imports
        self.used_imports = set()
        self.used_modules = set()

    def visit_Name(self, node):
        if isinstance(node.ctx, ast.Load):
            for imp in self.imports:
                if node.id == imp.alias:
                    self.used_imports.add(imp.alias)
                    self.used_modules.add(imp.module)
        self.generic_visit(node)

    def visit_Attribute(self, node):
        if isinstance(node.value, ast.Name) and isinstance(node.ctx, ast.Load):
            for imp in self.imports:
                if node.value.id == imp.alias:
                    self.used_imports.add(imp.alias)
                    self.used_modules.add(imp.module)
        self.generic_visit(node)

    def get_used_imports(self):
        """Return list of imports that are actually used"""
        return [imp for imp in self.imports if imp.alias in self.used_imports]

class DatabaseDetector(ast.NodeVisitor):
    """Detects database usage in code"""
    def __init__(self):
        self.uses_database = False
        self.db_modules = [
            'psycopg2', 'mysql', 'sqlite3', 'sqlalchemy', 'pymongo',
            'redis', 'elasticsearch', 'cassandra', 'neo4j', 'db'
        ]

    def visit_Import(self, node):
        for name in node.names:
            if any(db in name.name.lower() for db in self.db_modules):
                self.uses_database = True
                break
        self.generic_visit(node)

    def visit_ImportFrom(self, node):
        if node.module and any(db in node.module.lower() for db in self.db_modules):
            self.uses_database = True
        self.generic_visit(node)

    def visit_Call(self, node):
        if isinstance(node.func, ast.Attribute):
            db_methods = ['connect', 'cursor', 'execute', 'query', 'commit', 'rollback']
            if node.func.attr in db_methods:
                self.uses_database = True
        self.generic_visit(node)
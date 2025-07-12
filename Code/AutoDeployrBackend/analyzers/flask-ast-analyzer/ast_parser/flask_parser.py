import os
import ast
import logging
import re
from typing import Dict, List, Set, Optional, Any
from .models import ImportDefinition, ServerlessFunction
from .app_visitor import (ImportCollector, FlaskAppVisitor, FlaskRouteVisitor,
                          FunctionCallVisitor, EnvVarDetector, DatabaseDetector,
                          ImportUsageAnalyzer, FunctionDefinitionVisitor)
try:
    from .http_method_fixer import fix_http_methods
except ImportError:
    def fix_http_methods(file_path):
        print(f"Warning: Using fallback HTTP method fixer for {file_path}")
        return False

class FlaskApplicationParser:
    """Parser for Flask applications"""

    def __init__(self, app_path: str):
        self.app_path = app_path
        self.python_files = []
        self.flask_apps = set()
        self.flask_blueprints = set()
        self.app_name = "app"  # Default Flask app name
        self.routes = []
        self.imports_by_file = {}
        self.function_calls = {}
        self.env_vars = set()
        self.logger = logging.getLogger(__name__)

        self.import_lines_by_file = {}  # file_path -> {ImportDefinition -> line_number}
        self.function_imports_by_file = {}  # file_path -> {function_name -> [ImportDefinition]}
        self.references_by_file = {}  # file_path -> {variable_name -> [line_numbers]}
        self.function_lines_by_file = {}  # file_path -> {function_name -> (start_line, end_line)}
        self.output_files = []

        self.all_functions = {}  # function_name -> {source, is_route, file_path}
        self.all_function_calls = {}  # function_name -> set(called_function_names)

    def parse(self) -> Dict[str, Any]:
        """Analyze the complete Flask application"""
        self.find_python_files()
        self.find_flask_apps()
        self.analyze_files()
        functions = self.extract_functions()

        return {
            "language": "python",
            "framework": "flask",
            "app_name": self.app_name,
            "functions": [self._function_to_dict(f) for f in functions]
        }

    def parse_file(self, file_path: str, relative_path: str = "") -> Dict[str, Any]:
        """Analyze a single Python file"""
        self.python_files = [(file_path, relative_path)]
        self.find_flask_apps()
        self.analyze_files()
        functions = self.extract_functions()

        return {
            "language": "python",
            "framework": "flask",
            "app_name": self.app_name,
            "functions": [self._function_to_dict(f) for f in functions]
        }

    def find_python_files(self) -> None:
        """Find all Python files in the application"""
        if not self.app_path or not os.path.isdir(self.app_path):
            self.logger.warning(f"Invalid app path: {self.app_path}")
            return

        for root, _, files in os.walk(self.app_path):
            for file in files:
                if file.endswith('.py'):
                    abs_path = os.path.join(root, file)
                    rel_path = os.path.relpath(abs_path, self.app_path)
                    self.python_files.append((abs_path, rel_path))

        self.logger.info(f"Found {len(self.python_files)} Python files")

    def find_flask_apps(self) -> None:
        """Find all Flask app and blueprint instances"""
        for file_path, rel_path in self.python_files:
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    source_code = f.read()

                tree = ast.parse(source_code)

                app_visitor = FlaskAppVisitor()
                app_visitor.visit(tree)

                if app_visitor.flask_apps:
                    self.logger.info(f"Found Flask app(s) in {rel_path}: {app_visitor.flask_apps}")
                    self.flask_apps.update(app_visitor.flask_apps)

                if app_visitor.flask_blueprints:
                    self.logger.info(f"Found Flask blueprint(s) in {rel_path}: {app_visitor.flask_blueprints}")
                    self.flask_blueprints.update(app_visitor.flask_blueprints)

            except Exception as e:
                self.logger.error(f"Error analyzing file {rel_path}: {str(e)}")
        if self.flask_apps:
            self.app_name = next(iter(self.flask_apps))

    def analyze_files(self) -> None:
        """Analyze files to find routes, imports, function definitions and other information"""
        for file_path, rel_path in self.python_files:
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    source_code = f.read()

                tree = ast.parse(source_code)
                import_collector = ImportCollector()
                import_collector.visit(tree)
                self.imports_by_file[rel_path] = import_collector.imports
                self.import_lines_by_file[rel_path] = import_collector.import_lines
                self.function_imports_by_file[rel_path] = import_collector.function_imports
                self.references_by_file[rel_path] = import_collector.references
                func_def_visitor = FunctionDefinitionVisitor(source_code)
                func_def_visitor.set_flask_apps(self.flask_apps, self.flask_blueprints)
                func_def_visitor.visit(tree)
                for func_name, func_info in func_def_visitor.functions.items():
                    func_info['file_path'] = rel_path
                    self.all_functions[func_name] = func_info
                    self.logger.debug(f"Found function {func_name} in {rel_path}")
                route_visitor = FlaskRouteVisitor(
                    source_code=source_code,
                    file_path=rel_path,
                    flask_apps=self.flask_apps,
                    flask_blueprints=self.flask_blueprints
                )
                route_visitor.visit(tree)
                self.routes.extend(route_visitor.routes)
                function_lines = {}
                for route in route_visitor.routes:
                    function_lines[route["name"]] = (route["start_line"], route["end_line"])
                self.function_lines_by_file[rel_path] = function_lines
                call_visitor = FunctionCallVisitor()
                call_visitor.visit(tree)
                for func_name, calls in call_visitor.function_calls.items():
                    self.function_calls[func_name] = calls
                    self.all_function_calls[func_name] = calls
                    self.logger.debug(f"Function {func_name} calls: {calls}")
                env_detector = EnvVarDetector()
                env_detector.visit(tree)
                self.env_vars.update(env_detector.env_vars)

            except Exception as e:
                self.logger.error(f"Error in detailed analysis of {rel_path}: {str(e)}")

        self.logger.info(f"Found {len(self.routes)} routes in the application")
        self.logger.info(f"Found {len(self.all_functions)} total functions in the application")

    def extract_functions(self) -> List[ServerlessFunction]:
        """Extract the list of functions with only the imports they actually use"""
        functions = []

        for route in self.routes:
            # Create function
            function = ServerlessFunction(
                name=route["name"],
                path=route["path"],
                methods=route["methods"],
                source=route["source"],
                app_name=route["app_name"],
                file_path=route["file_path"],
                line_number=route["line_number"],
                requires_db=self._check_for_database_usage(route)
            )
            if route["file_path"] in self.imports_by_file:
                used_imports = self._get_imports_used_by_function(route)
                function.imports = used_imports
                self.logger.info(f"Function {route['name']} uses {len(used_imports)} imports")
            if route["name"] in self.all_function_calls:
                function.dependencies = self._resolve_all_dependencies(route["name"])
                self.logger.info(f"Function {route['name']} has {len(function.dependencies)} dependencies")
                dependency_sources = {}
                for dep_name in function.dependencies:
                    if dep_name in self.all_functions:
                        func_info = self.all_functions[dep_name]
                        if 'source' in func_info:
                            dependency_sources[dep_name] = func_info['source']
                function.dependency_sources = dependency_sources
                self.logger.info(f"Added source code for {len(dependency_sources)} dependencies to {route['name']}")
            function.env_vars = self.env_vars

            functions.append(function)

        return functions

    def _get_imports_used_by_function(self, route) -> List[ImportDefinition]:
        """Determine which imports are actually used by a specific function"""
        file_path = route["file_path"]
        function_name = route["name"]
        all_imports = self.imports_by_file.get(file_path, [])
        try:
            function_ast = ast.parse(route["source"])
            usage_analyzer = ImportUsageAnalyzer(all_imports)
            usage_analyzer.visit(function_ast)
            used_imports = usage_analyzer.get_used_imports()
            essential_module_prefixes = ["flask", "werkzeug", "jinja2"]
            for imp in all_imports:
                module_name = imp.module.lower()
                if any(module_name.startswith(prefix) for prefix in essential_module_prefixes) and imp not in used_imports:
                    used_imports.append(imp)
            app_name = route["app_name"]
            app_imports = [imp for imp in all_imports if app_name == imp.alias]
            for imp in app_imports:
                if imp not in used_imports:
                    used_imports.append(imp)
            common_patterns = ["request", "jsonify", "render_template", "redirect", "url_for", "session"]
            for pattern in common_patterns:
                pattern_imports = [imp for imp in all_imports if imp.alias == pattern or
                                   (imp.module.endswith("." + pattern) and imp.alias == pattern)]
                for imp in pattern_imports:
                    if imp not in used_imports and self._is_pattern_used_in_function(pattern, route["source"]):
                        used_imports.append(imp)
            if self._check_for_database_usage(route):
                db_modules = ["db", "database", "models", "sqlalchemy", "psycopg2", "pymongo", "redis"]
                db_imports = [imp for imp in all_imports if any(db in imp.module.lower() for db in db_modules)]
                for imp in db_imports:
                    if imp not in used_imports:
                        used_imports.append(imp)
            dependency_imports = self._get_imports_for_dependency_functions(function_name, all_imports)
            for imp in dependency_imports:
                if imp not in used_imports:
                    used_imports.append(imp)
                    self.logger.debug(f"Added dependency import {imp.module} (as {imp.alias}) for function {function_name}")

            return used_imports
        except Exception as e:
            self.logger.error(f"Error determining imports for function {function_name}: {str(e)}")
            return all_imports

    def _get_imports_for_dependency_functions(self, function_name: str, all_imports: List[ImportDefinition]) -> List[ImportDefinition]:
        """Analyze imports needed by dependency functions recursively"""
        dependency_imports = []
        
        try:
            dependencies = self._resolve_all_dependencies(function_name)
            
            for dep_name in dependencies:
                if dep_name in self.all_functions:
                    dep_func_info = self.all_functions[dep_name]
                    dep_source = dep_func_info.get('source', '')
                    
                    if dep_source:
                        try:
                            dep_ast = ast.parse(dep_source)
                            dep_usage_analyzer = ImportUsageAnalyzer(all_imports)
                            dep_usage_analyzer.visit(dep_ast)
                            dep_used_imports = dep_usage_analyzer.get_used_imports()
                            for imp in dep_used_imports:
                                if imp not in dependency_imports:
                                    dependency_imports.append(imp)
                                    self.logger.debug(f"Dependency {dep_name} requires import: {imp.module} (as {imp.alias})")
                                    
                        except Exception as e:
                            self.logger.warning(f"Could not analyze imports for dependency {dep_name}: {str(e)}")
                            dependency_imports.extend(self._detect_common_imports_in_source(dep_source, all_imports))
            
        except Exception as e:
            self.logger.error(f"Error analyzing dependency imports for {function_name}: {str(e)}")
        
        return dependency_imports

    def _detect_common_imports_in_source(self, source_code: str, all_imports: List[ImportDefinition]) -> List[ImportDefinition]:
        """Detect common imports by searching for patterns in source code"""
        detected_imports = []
        import_patterns = {
            'hashlib': [r'\bhashlib\.', r'hashlib\s*\.\s*\w+'],
            'os': [r'\bos\.', r'os\s*\.\s*\w+'],
            'sys': [r'\bsys\.', r'sys\s*\.\s*\w+'],
            'json': [r'\bjson\.', r'json\s*\.\s*\w+'],
            'datetime': [r'\bdatetime\.', r'datetime\s*\.\s*\w+'],
            'time': [r'\btime\.', r'time\s*\.\s*\w+'],
            'random': [r'\brandom\.', r'random\s*\.\s*\w+'],
            're': [r'\bre\.', r're\s*\.\s*\w+'],
            'base64': [r'\bbase64\.', r'base64\s*\.\s*\w+'],
            'urllib': [r'\burllib\.', r'urllib\s*\.\s*\w+'],
            'requests': [r'\brequests\.', r'requests\s*\.\s*\w+']
        }
        
        for import_name, patterns in import_patterns.items():
            for pattern in patterns:
                if re.search(pattern, source_code):
                    for imp in all_imports:
                        if (imp.module == import_name or 
                            imp.module.startswith(import_name + '.') or 
                            imp.alias == import_name):
                            if imp not in detected_imports:
                                detected_imports.append(imp)
                                self.logger.debug(f"Pattern-detected import: {imp.module} (as {imp.alias})")
                    break
        
        return detected_imports

    def _is_pattern_used_in_function(self, pattern, function_source):
        """Check if a common Flask pattern is used in the function"""
        patterns = {
            "request": [r"\brequest\.", r"\brequest\["],
            "jsonify": [r"\bjsonify\("],
            "render_template": [r"\brender_template\("],
            "redirect": [r"\bredirect\("],
            "url_for": [r"\burl_for\("],
            "session": [r"\bsession\.", r"\bsession\["]
        }

        if pattern in patterns:
            for regex in patterns[pattern]:
                if re.search(regex, function_source):
                    return True
        return bool(re.search(r'\b' + re.escape(pattern) + r'\b', function_source))

    def _check_for_database_usage(self, route) -> bool:
        """Check if a route uses a database"""
        try:
            tree = ast.parse(route["source"])
            db_detector = DatabaseDetector()
            db_detector.visit(tree)
            return db_detector.uses_database
        except Exception:
            return False

    def _resolve_all_dependencies(self, function_name, resolved=None, visited=None) -> Set[str]:
        """Recursively resolve all dependencies of a function, including utility functions"""
        if resolved is None:
            resolved = set()
        if visited is None:
            visited = set()
        if function_name in visited:
            return resolved

        visited.add(function_name)
        direct_deps = self.all_function_calls.get(function_name, set())
        self.logger.debug(f"Direct dependencies of {function_name}: {direct_deps}")

        for dep in direct_deps:
            if dep in resolved:
                continue
            if dep in {'print', 'len', 'str', 'int', 'float', 'list', 'dict', 'set', 'bool', 'type',
                       'open', 'sum', 'min', 'max', 'sorted', 'enumerate', 'zip', 'filter', 'map'}:
                continue
            if dep in self.all_functions:
                resolved.add(dep)
                self.logger.debug(f"Added dependency {dep} for {function_name}")
                self._resolve_all_dependencies(dep, resolved, visited)

        return resolved

    def _function_to_dict(self, function: ServerlessFunction) -> Dict[str, Any]:
        """Convert a ServerlessFunction to a dictionary for JSON output"""
        if not function.methods:
            function.methods = ["GET"]
        dependency_sources = {}
        for dep_name in function.dependencies:
            if dep_name in self.all_functions:
                func_info = self.all_functions[dep_name]
                if 'source' in func_info:
                    dependency_sources[dep_name] = func_info['source']
                else:
                    self.logger.warning(f"Missing source for dependency {dep_name}")
        self.logger.info(f"Function {function.name} has {len(function.dependencies)} dependencies and {len(dependency_sources)} dependency sources")

        return {
            "name": function.name,
            "path": function.path,
            "methods": function.methods,
            "source": function.source,
            "app_name": function.app_name,
            "dependencies": list(function.dependencies),
            "dependency_sources": dependency_sources,  # Include ALL dependency source code
            "imports": [{"module": i.module, "alias": i.alias} for i in function.imports],
            "env_vars": list(function.env_vars),
            "file_path": function.file_path,
            "line_number": function.line_number,
            "requires_db": function.requires_db
        }

    def get_function_source(self, function_name: str) -> Optional[str]:
        """Get the source code of a function by name"""
        if function_name in self.all_functions:
            func_info = self.all_functions[function_name]
            return func_info.get('source', '')
        return None

    def fix_http_methods_in_file(self, file_path: str) -> bool:
        """Fix HTTP methods in a generated Python file"""
        try:
            self.logger.info(f"Fixing HTTP methods in {file_path}")
            return fix_http_methods(file_path)
        except Exception as e:
            self.logger.error(f"Error fixing HTTP methods in {file_path}: {str(e)}")
            return False

    def register_output_file(self, file_path: str) -> None:
        """Register a generated output file for post-processing"""
        self.output_files.append(file_path)

    def post_process_output_files(self) -> None:
        """Post-process all registered output files"""
        fixed_count = 0
        for file_path in self.output_files:
            if self.fix_http_methods_in_file(file_path):
                fixed_count += 1

        if fixed_count > 0:
            self.logger.info(f"Fixed HTTP methods in {fixed_count} files")
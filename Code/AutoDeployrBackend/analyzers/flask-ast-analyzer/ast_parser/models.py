from dataclasses import dataclass, field
from typing import List, Set, Dict, Optional, Any

@dataclass(frozen=True)
class ImportDefinition:
    """Defines an import"""
    module: str
    alias: str

@dataclass
class ServerlessFunction:
    """Defines a serverless function extracted from a Flask application"""
    name: str
    path: str
    methods: List[str]
    source: str
    app_name: str = "app"
    dependencies: Set[str] = field(default_factory=set)
    dependency_sources: Dict[str, str] = field(default_factory=dict)
    imports: List[ImportDefinition] = field(default_factory=list)
    env_vars: Set[str] = field(default_factory=set)
    file_path: str = ""
    line_number: int = 0
    requires_db: bool = False
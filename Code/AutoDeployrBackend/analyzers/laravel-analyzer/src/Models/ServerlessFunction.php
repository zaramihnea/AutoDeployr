<?php

namespace AutoDeployr\LaravelAnalyzer\Models;

class ServerlessFunction
{
    /** @var ImportDefinition[] */
    public array $imports = [];
    
    /** @var string[] */
    public array $dependencies = [];
    
    /** @var array<string, string> */
    public array $dependencySources = [];
    
    /** @var string[] */
    public array $envVars = [];

    public function __construct(
        public readonly string $name,
        public readonly string $path,
        public readonly array $methods,
        public readonly string $source,
        public readonly string $appName = 'Laravel',
        public readonly string $controller = '',
        public readonly string $filePath = '',
        public readonly int $lineNumber = 0,
        public readonly bool $requiresDb = false
    ) {}

    public function toArray(): array
    {
        return [
            'name' => $this->name,
            'path' => $this->path,
            'methods' => $this->methods,
            'source' => $this->source,
            'app_name' => $this->appName,
            'controller' => $this->controller,
            'dependencies' => $this->dependencies,
            'dependency_sources' => $this->dependencySources,
            'imports' => array_map(fn($import) => $import->toArray(), $this->imports),
            'env_vars' => $this->envVars,
            'file_path' => $this->filePath,
            'line_number' => $this->lineNumber,
            'requires_db' => $this->requiresDb
        ];
    }

    public function addImport(ImportDefinition $import): void
    {
        $this->imports[] = $import;
    }

    public function addDependency(string $name, string $source = ''): void
    {
        $this->dependencies[] = $name;
        if (!empty($source)) {
            $this->dependencySources[$name] = $source;
        }
    }

    public function addEnvVar(string $varName): void
    {
        if (!in_array($varName, $this->envVars)) {
            $this->envVars[] = $varName;
        }
    }
} 
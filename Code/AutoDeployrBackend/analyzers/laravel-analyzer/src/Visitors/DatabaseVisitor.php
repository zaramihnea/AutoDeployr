<?php

namespace AutoDeployr\LaravelAnalyzer\Visitors;

use PhpParser\Node;
use PhpParser\NodeVisitorAbstract;

class DatabaseVisitor extends NodeVisitorAbstract
{
    private bool $databaseDetected = false;
    private array $envVars = [];

    private array $dbClasses = [
        'DB', 'Illuminate\Support\Facades\DB',
        'Illuminate\Database\Eloquent\Model',
        'Illuminate\Database\Eloquent\Builder',
        'Illuminate\Database\Query\Builder'
    ];

    private array $dbMethods = [
        'select', 'insert', 'update', 'delete', 'table', 'raw',
        'where', 'orderBy', 'groupBy', 'having', 'join',
        'create', 'save', 'destroy', 'find', 'findOrFail',
        'first', 'get', 'paginate', 'count', 'exists'
    ];

    private array $eloquentMethods = [
        'all', 'find', 'findOrFail', 'first', 'firstOrFail',
        'create', 'save', 'update', 'delete', 'destroy',
        'where', 'orderBy', 'with', 'has', 'whereHas'
    ];

    public function enterNode(Node $node)
    {
        if ($node instanceof Node\Stmt\Use_) {
            foreach ($node->uses as $use) {
                $className = $use->name->toString();
                if ($this->isDatabaseClass($className)) {
                    $this->databaseDetected = true;
                }
            }
        }
        if ($node instanceof Node\Expr\StaticCall) {
            if ($node->class instanceof Node\Name) {
                $className = $node->class->toString();
                if ($className === 'DB' || str_contains($className, 'Database')) {
                    $this->databaseDetected = true;
                }
            }
        }
        if ($node instanceof Node\Expr\MethodCall) {
            if ($node->name instanceof Node\Identifier) {
                $methodName = $node->name->name;
                if (in_array($methodName, $this->dbMethods) || 
                    in_array($methodName, $this->eloquentMethods)) {
                    $this->databaseDetected = true;
                }
            }
        }
        if ($node instanceof Node\Stmt\Class_) {
            if ($node->extends && $node->extends instanceof Node\Name) {
                $parentClass = $node->extends->toString();
                if ($parentClass === 'Model' || 
                    str_contains($parentClass, 'Eloquent') ||
                    str_contains($parentClass, 'Model')) {
                    $this->databaseDetected = true;
                }
            }
        }
        if ($node instanceof Node\Expr\FuncCall) {
            if ($node->name instanceof Node\Name && $node->name->toString() === 'env') {
                if (count($node->args) > 0) {
                    $envVar = $this->extractStringValue($node->args[0]->value);
                    if ($envVar && (str_starts_with($envVar, 'DB_') || $envVar === 'DATABASE_URL')) {
                        $this->envVars[] = $envVar;
                        $this->databaseDetected = true;
                    }
                }
            }
        }
        if ($node instanceof Node\Expr\FuncCall) {
            if ($node->name instanceof Node\Name && $node->name->toString() === 'config') {
                if (count($node->args) > 0) {
                    $configKey = $this->extractStringValue($node->args[0]->value);
                    if ($configKey && (str_starts_with($configKey, 'database.') || 
                                      str_contains($configKey, 'database'))) {
                        $this->databaseDetected = true;
                    }
                }
            }
        }
        if ($node instanceof Node\Expr\StaticCall) {
            if ($node->class instanceof Node\Name && $node->class->toString() === 'Schema') {
                $this->databaseDetected = true;
            }
        }
        if ($node instanceof Node\Stmt\Class_) {
            if ($node->name && str_contains($node->name->toString(), 'Migration')) {
                $this->databaseDetected = true;
            }
            if ($node->extends && $node->extends instanceof Node\Name) {
                $parentClass = $node->extends->toString();
                if ($parentClass === 'Migration') {
                    $this->databaseDetected = true;
                }
            }
        }
    }

    private function isDatabaseClass(string $className): bool
    {
        foreach ($this->dbClasses as $dbClass) {
            if ($className === $dbClass || str_contains($className, 'Database') || 
                str_contains($className, 'Eloquent') || str_contains($className, 'Model')) {
                return true;
            }
        }
        return false;
    }

    private function extractStringValue(Node $node): ?string
    {
        if ($node instanceof Node\Scalar\String_) {
            return $node->value;
        }
        return null;
    }

    public function isDatabaseDetected(): bool
    {
        return $this->databaseDetected;
    }

    public function getEnvVars(): array
    {
        return array_unique($this->envVars);
    }
} 
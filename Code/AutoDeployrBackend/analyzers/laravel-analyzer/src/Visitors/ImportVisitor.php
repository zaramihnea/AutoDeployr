<?php

namespace AutoDeployr\LaravelAnalyzer\Visitors;

use AutoDeployr\LaravelAnalyzer\Models\ImportDefinition;
use PhpParser\Node;
use PhpParser\NodeVisitorAbstract;

class ImportVisitor extends NodeVisitorAbstract
{
    private array $imports = [];
    private string $filePath;

    public function __construct(string $filePath)
    {
        $this->filePath = $filePath;
    }

    public function enterNode(Node $node)
    {
        if ($node instanceof Node\Stmt\Use_) {
            foreach ($node->uses as $use) {
                $this->handleUseStatement($use);
            }
        }
        if ($node instanceof Node\Stmt\GroupUse) {
            $prefix = $node->prefix->toString();
            foreach ($node->uses as $use) {
                $fullName = $prefix . '\\' . $use->name->toString();
                $alias = $use->alias ? $use->alias->toString() : $use->name->toString();
                
                $this->imports[] = new ImportDefinition($fullName, $alias);
            }
        }
    }

    private function handleUseStatement(Node\Stmt\UseUse $use): void
    {
        $fullName = $use->name->toString();
        $alias = $use->alias ? $use->alias->toString() : $this->getLastPartOfNamespace($fullName);
        
        $this->imports[] = new ImportDefinition($fullName, $alias);
    }

    private function getLastPartOfNamespace(string $namespace): string
    {
        $parts = explode('\\', $namespace);
        return end($parts);
    }

    public function getImports(): array
    {
        return $this->imports;
    }
} 
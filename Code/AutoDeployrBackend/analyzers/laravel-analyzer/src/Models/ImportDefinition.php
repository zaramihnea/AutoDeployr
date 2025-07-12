<?php

namespace AutoDeployr\LaravelAnalyzer\Models;

class ImportDefinition
{
    public function __construct(
        public readonly string $namespace,
        public readonly string $alias
    ) {}

    public function toArray(): array
    {
        return [
            'namespace' => $this->namespace,
            'alias' => $this->alias
        ];
    }

    public static function fromArray(array $data): self
    {
        return new self(
            $data['namespace'] ?? '',
            $data['alias'] ?? ''
        );
    }
} 
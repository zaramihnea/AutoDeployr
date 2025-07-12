<?php

namespace AutoDeployr\LaravelAnalyzer\Visitors;

use PhpParser\Node;
use PhpParser\NodeVisitorAbstract;

class ControllerVisitor extends NodeVisitorAbstract
{
    private array $controllerMethods = [];
    private string $sourceCode;
    private string $filePath;
    private ?string $currentController = null;
    private ?string $currentNamespace = null;

    public function __construct(string $sourceCode, string $filePath)
    {
        $this->sourceCode = $sourceCode;
        $this->filePath = $filePath;
    }

    public function enterNode(Node $node)
    {
        // Track namespace
        if ($node instanceof Node\Stmt\Namespace_) {
            $this->currentNamespace = $node->name ? $node->name->toString() : null;
        }

        // Track class definitions
        if ($node instanceof Node\Stmt\Class_) {
            if ($node->name) {
                $this->currentController = $node->name->toString();
                
                // Add namespace if available
                if ($this->currentNamespace) {
                    $this->currentController = $this->currentNamespace . '\\' . $this->currentController;
                }
            }
        }

        // Track public methods in controllers
        if ($node instanceof Node\Stmt\ClassMethod && $this->currentController) {
            if ($this->isPublicMethod($node) && $this->isRouteMethod($node)) {
                $this->extractControllerMethod($node);
            }
        }
    }

    public function leaveNode(Node $node)
    {
        // Reset controller when leaving class
        if ($node instanceof Node\Stmt\Class_) {
            $this->currentController = null;
        }

        // Reset namespace when leaving namespace
        if ($node instanceof Node\Stmt\Namespace_) {
            $this->currentNamespace = null;
        }
    }

    private function isPublicMethod(Node\Stmt\ClassMethod $method): bool
    {
        return $method->isPublic();
    }

    private function isRouteMethod(Node\Stmt\ClassMethod $method): bool
    {
        $methodName = $method->name->toString();

        // Skip constructor and Laravel built-in methods
        $skipMethods = [
            '__construct', '__destruct', '__call', '__callStatic',
            '__get', '__set', '__isset', '__unset', '__toString',
            'middleware', 'authorize', 'validate', 'validateWith'
        ];

        if (in_array($methodName, $skipMethods)) {
            return false;
        }

        // Skip methods starting with underscore (typically private helpers)
        if (str_starts_with($methodName, '_')) {
            return false;
        }

        return true;
    }

    private function extractControllerMethod(Node\Stmt\ClassMethod $method): void
    {
        $methodName = $method->name->toString();
        $source = $this->extractMethodSource($method);
        
        $this->controllerMethods[] = [
            'controller' => $this->getControllerShortName(),
            'method' => $methodName,
            'source' => $source,
            'file' => $this->filePath,
            'line' => $method->getStartLine(),
            'parameters' => $this->extractMethodParameters($method),
            'returnType' => $this->extractReturnType($method)
        ];
    }

    private function extractMethodSource(Node\Stmt\ClassMethod $method): string
    {
        $startLine = $method->getStartLine();
        $endLine = $method->getEndLine();

        if ($startLine === -1 || $endLine === -1) {
            return "// Method source extraction failed for {$method->name}";
        }

        $lines = explode("\n", $this->sourceCode);
        $sourceLines = array_slice($lines, $startLine - 1, $endLine - $startLine + 1);

        return implode("\n", $sourceLines);
    }

    private function extractMethodParameters(Node\Stmt\ClassMethod $method): array
    {
        $parameters = [];

        foreach ($method->params as $param) {
            $paramInfo = [
                'name' => $param->var->name ?? 'unknown',
                'type' => null,
                'default' => null,
                'nullable' => false
            ];

            // Extract type
            if ($param->type) {
                if ($param->type instanceof Node\Name) {
                    $paramInfo['type'] = $param->type->toString();
                } elseif ($param->type instanceof Node\Identifier) {
                    $paramInfo['type'] = $param->type->name;
                } elseif ($param->type instanceof Node\NullableType) {
                    $paramInfo['nullable'] = true;
                    if ($param->type->type instanceof Node\Name) {
                        $paramInfo['type'] = $param->type->type->toString();
                    } elseif ($param->type->type instanceof Node\Identifier) {
                        $paramInfo['type'] = $param->type->type->name;
                    }
                }
            }

            // Extract default value
            if ($param->default) {
                $paramInfo['default'] = $this->extractDefaultValue($param->default);
            }

            $parameters[] = $paramInfo;
        }

        return $parameters;
    }

    private function extractDefaultValue(Node $node): mixed
    {
        if ($node instanceof Node\Scalar\String_) {
            return $node->value;
        } elseif ($node instanceof Node\Scalar\LNumber) {
            return $node->value;
        } elseif ($node instanceof Node\Scalar\DNumber) {
            return $node->value;
        } elseif ($node instanceof Node\Expr\ConstFetch) {
            $name = $node->name->toString();
            return match (strtolower($name)) {
                'true' => true,
                'false' => false,
                'null' => null,
                default => $name
            };
        } elseif ($node instanceof Node\Expr\Array_) {
            return [];
        }

        return null;
    }

    private function extractReturnType(Node\Stmt\ClassMethod $method): ?string
    {
        if (!$method->returnType) {
            return null;
        }

        if ($method->returnType instanceof Node\Name) {
            return $method->returnType->toString();
        } elseif ($method->returnType instanceof Node\Identifier) {
            return $method->returnType->name;
        } elseif ($method->returnType instanceof Node\NullableType) {
            if ($method->returnType->type instanceof Node\Name) {
                return '?' . $method->returnType->type->toString();
            } elseif ($method->returnType->type instanceof Node\Identifier) {
                return '?' . $method->returnType->type->name;
            }
        }

        return null;
    }

    private function getControllerShortName(): string
    {
        if (!$this->currentController) {
            return 'UnknownController';
        }
        $parts = explode('\\', $this->currentController);
        return end($parts);
    }

    public function getControllerMethods(): array
    {
        return $this->controllerMethods;
    }
} 
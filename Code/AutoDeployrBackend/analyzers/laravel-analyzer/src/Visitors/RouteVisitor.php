<?php

namespace AutoDeployr\LaravelAnalyzer\Visitors;

use PhpParser\Node;
use PhpParser\NodeVisitorAbstract;

class RouteVisitor extends NodeVisitorAbstract
{
    private array $routes = [];
    private string $sourceCode;
    private string $filePath;

    public function __construct(string $sourceCode, string $filePath)
    {
        $this->sourceCode = $sourceCode;
        $this->filePath = $filePath;
    }

    public function enterNode(Node $node)
    {
        // Handle Route::get(), Route::post(), etc.
        if ($node instanceof Node\Expr\StaticCall) {
            if ($node->class instanceof Node\Name && $node->class->toString() === 'Route') {
                $this->handleRouteCall($node);
            }
        }

        // Handle Route::resource() calls
        if ($node instanceof Node\Expr\StaticCall) {
            if ($node->class instanceof Node\Name && 
                $node->class->toString() === 'Route' && 
                $node->name instanceof Node\Identifier &&
                $node->name->name === 'resource') {
                $this->handleResourceRoute($node);
            }
        }
    }

    private function handleRouteCall(Node\Expr\StaticCall $node): void
    {
        if (!($node->name instanceof Node\Identifier)) {
            return;
        }

        $method = strtoupper($node->name->name);
        $httpMethods = $this->getHttpMethodsForRoute($method);

        if (empty($httpMethods)) {
            return;
        }

        if (count($node->args) < 1) {
            return;
        }

        $pathArg = $node->args[0];
        $path = $this->extractStringValue($pathArg->value);

        if ($path === null) {
            return;
        }

        if (count($node->args) >= 2) {
            $actionArg = $node->args[1];
            
            if ($actionArg->value instanceof Node\Expr\Closure) {
                $this->handleRouteClosure($path, $httpMethods, $actionArg->value, $node);
            } elseif ($actionArg->value instanceof Node\Expr\Array_) {
                $this->handleControllerArray($path, $httpMethods, $actionArg->value, $node);
            } elseif ($actionArg->value instanceof Node\Scalar\String_) {
                $this->handleControllerString($path, $httpMethods, $actionArg->value, $node);
            }
        }
    }

    private function handleResourceRoute(Node\Expr\StaticCall $node): void
    {
        if (count($node->args) < 2) {
            return;
        }

        $resourceName = $this->extractStringValue($node->args[0]->value);
        $controllerArg = $node->args[1]->value;

        if ($resourceName === null) {
            return;
        }

        $controllerName = null;
        if ($controllerArg instanceof Node\Expr\ClassConstFetch) {
            $controllerName = $controllerArg->class->toString();
        } elseif ($controllerArg instanceof Node\Scalar\String_) {
            $controllerName = $controllerArg->value;
        }

        if ($controllerName === null) {
            return;
        }
        $resourceRoutes = [
            ['GET', "/{$resourceName}", 'index'],
            ['POST', "/{$resourceName}", 'store'],
            ['GET', "/{$resourceName}/{{id}}", 'show'],
            ['PUT', "/{$resourceName}/{{id}}", 'update'],
            ['PATCH', "/{$resourceName}/{{id}}", 'update'],
            ['DELETE', "/{$resourceName}/{{id}}", 'destroy'],
        ];

        foreach ($resourceRoutes as [$method, $path, $action]) {
            $this->routes[] = [
                'name' => "{$controllerName}@{$action}",
                'path' => $path,
                'methods' => [$method],
                'type' => 'controller',
                'controller' => $controllerName,
                'method' => $action,
                'source' => "// Resource route: {$method} {$path}",
                'file' => $this->filePath,
                'line' => $node->getStartLine()
            ];
        }
    }

    private function handleRouteClosure(string $path, array $methods, Node\Expr\Closure $closure, Node $node): void
    {
        $source = $this->extractNodeSource($closure);
        $functionName = $this->generateClosureName($path, $methods);

        $this->routes[] = [
            'name' => $functionName,
            'path' => $path,
            'methods' => $methods,
            'type' => 'closure',
            'source' => $source,
            'file' => $this->filePath,
            'line' => $node->getStartLine()
        ];
    }

    private function handleControllerArray(string $path, array $methods, Node\Expr\Array_ $array, Node $node): void
    {
        if (count($array->items) < 2) {
            return;
        }

        $controllerItem = $array->items[0];
        $methodItem = $array->items[1];

        if ($controllerItem === null || $methodItem === null) {
            return;
        }

        $controller = null;
        if ($controllerItem->value instanceof Node\Expr\ClassConstFetch) {
            $controller = $controllerItem->value->class->toString();
        }

        $method = $this->extractStringValue($methodItem->value);

        if ($controller && $method) {
            $this->routes[] = [
                'name' => "{$controller}@{$method}",
                'path' => $path,
                'methods' => $methods,
                'type' => 'controller',
                'controller' => $controller,
                'method' => $method,
                'source' => "// Controller method: {$controller}@{$method}",
                'file' => $this->filePath,
                'line' => $node->getStartLine()
            ];
        }
    }

    private function handleControllerString(string $path, array $methods, Node\Scalar\String_ $string, Node $node): void
    {
        $controllerString = $string->value;
        
        if (strpos($controllerString, '@') !== false) {
            [$controller, $method] = explode('@', $controllerString, 2);
            
            $this->routes[] = [
                'name' => $controllerString,
                'path' => $path,
                'methods' => $methods,
                'type' => 'controller',
                'controller' => $controller,
                'method' => $method,
                'source' => "// Controller method: {$controllerString}",
                'file' => $this->filePath,
                'line' => $node->getStartLine()
            ];
        }
    }

    private function getHttpMethodsForRoute(string $routeMethod): array
    {
        return match ($routeMethod) {
            'GET' => ['GET'],
            'POST' => ['POST'],
            'PUT' => ['PUT'],
            'PATCH' => ['PATCH'],
            'DELETE' => ['DELETE'],
            'OPTIONS' => ['OPTIONS'],
            'ANY' => ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'],
            'MATCH' => ['GET', 'POST'],
            default => []
        };
    }

    private function extractStringValue(Node $node): ?string
    {
        if ($node instanceof Node\Scalar\String_) {
            return $node->value;
        }
        return null;
    }

    private function extractNodeSource(Node $node): string
    {
        if ($node instanceof Node\Expr\Closure) {
            $prettyPrinter = new \PhpParser\PrettyPrinter\Standard();
            return $prettyPrinter->prettyPrint([$node]);
        }
        $startLine = $node->getStartLine();
        $endLine = $node->getEndLine();

        if ($startLine === -1 || $endLine === -1) {
            return '// Source extraction failed';
        }

        $lines = explode("\n", $this->sourceCode);
        $sourceLines = array_slice($lines, $startLine - 1, $endLine - $startLine + 1);

        return implode("\n", $sourceLines);
    }

    private function generateClosureName(string $path, array $methods): string
    {
        $method = strtolower($methods[0] ?? 'get');
        $cleanPath = preg_replace('/[^a-zA-Z0-9]/', '_', $path);
        $cleanPath = preg_replace('/_+/', '_', $cleanPath);
        $cleanPath = trim($cleanPath, '_');
        
        return "closure_{$method}_{$cleanPath}";
    }

    public function getRoutes(): array
    {
        return $this->routes;
    }
} 
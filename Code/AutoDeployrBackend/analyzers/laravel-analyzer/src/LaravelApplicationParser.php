<?php

namespace AutoDeployr\LaravelAnalyzer;

use AutoDeployr\LaravelAnalyzer\Models\ImportDefinition;
use AutoDeployr\LaravelAnalyzer\Models\ServerlessFunction;
use AutoDeployr\LaravelAnalyzer\Visitors\RouteVisitor;
use AutoDeployr\LaravelAnalyzer\Visitors\ControllerVisitor;
use AutoDeployr\LaravelAnalyzer\Visitors\ImportVisitor;
use AutoDeployr\LaravelAnalyzer\Visitors\DatabaseVisitor;
use PhpParser\NodeTraverser;
use PhpParser\ParserFactory;
use PhpParser\Error;
use Symfony\Component\Finder\Finder;

class LaravelApplicationParser
{
    private string $appPath;
    private array $phpFiles = [];
    private array $routes = [];
    private array $controllers = [];
    private array $imports = [];
    private array $envVars = [];
    private bool $databaseDetected = false;

    public function __construct(string $appPath)
    {
        $this->appPath = $appPath;
    }

    public function parse(): array
    {
        $this->findPhpFiles();
        $this->analyzeFiles();
        $functions = $this->extractFunctions();

        return [
            'language' => 'php',
            'framework' => 'laravel',
            'app_name' => 'Laravel',
            'functions' => array_map(fn($f) => $f->toArray(), $functions)
        ];
    }

    public function parseFile(string $filePath, string $relativePath = ''): array
    {
        $this->phpFiles = [['path' => $filePath, 'relative' => $relativePath]];
        $this->analyzeFiles();
        $functions = $this->extractFunctions();

        return [
            'language' => 'php',
            'framework' => 'laravel',
            'app_name' => 'Laravel',
            'functions' => array_map(fn($f) => $f->toArray(), $functions)
        ];
    }

    private function findPhpFiles(): void
    {
        if (!is_dir($this->appPath)) {
            error_log("Invalid app path: {$this->appPath}");
            return;
        }

        $finder = new Finder();
        $finder->files()
            ->in($this->appPath)
            ->name('*.php')
            ->exclude(['vendor', 'node_modules', 'storage/framework', 'bootstrap/cache']);

        foreach ($finder as $file) {
            $this->phpFiles[] = [
                'path' => $file->getRealPath(),
                'relative' => $file->getRelativePathname()
            ];
        }
    }

    private function analyzeFiles(): void
    {
        $parser = (new ParserFactory)->create(ParserFactory::PREFER_PHP7);

        foreach ($this->phpFiles as $fileInfo) {
            $filePath = $fileInfo['path'];
            $relativePath = $fileInfo['relative'];

            try {
                $code = file_get_contents($filePath);
                $ast = $parser->parse($code);

                if ($ast === null) {
                    continue;
                }

                $this->analyzeFile($ast, $code, $relativePath);

            } catch (Error $error) {
                error_log("Parse error in {$filePath}: " . $error->getMessage());
            } catch (\Exception $e) {
                error_log("Error analyzing {$filePath}: " . $e->getMessage());
            }
        }
    }

    private function analyzeFile(array $ast, string $code, string $relativePath): void
    {
        $traverser = new NodeTraverser();

        // Analyze routes (for routes/web.php, routes/api.php files)
        if (str_contains($relativePath, 'routes/')) {
            $routeVisitor = new RouteVisitor($code, $relativePath);
            $traverser->addVisitor($routeVisitor);
        }

        // Analyze controllers
        if (str_contains($relativePath, 'app/Http/Controllers/')) {
            $controllerVisitor = new ControllerVisitor($code, $relativePath);
            $traverser->addVisitor($controllerVisitor);
        }

        // Analyze imports for all files
        $importVisitor = new ImportVisitor($relativePath);
        $traverser->addVisitor($importVisitor);

        // Analyze database usage
        $databaseVisitor = new DatabaseVisitor();
        $traverser->addVisitor($databaseVisitor);

        $traverser->traverse($ast);

        // Collect results
        if (isset($routeVisitor)) {
            $this->routes = array_merge($this->routes, $routeVisitor->getRoutes());
        }

        if (isset($controllerVisitor)) {
            $this->controllers = array_merge($this->controllers, $controllerVisitor->getControllerMethods());
        }

        $this->imports[$relativePath] = $importVisitor->getImports();
        
        if ($databaseVisitor->isDatabaseDetected()) {
            $this->databaseDetected = true;
        }

        $this->envVars = array_merge($this->envVars, $databaseVisitor->getEnvVars());
    }

    private function extractFunctions(): array
    {
        $functions = [];

        // Process route closures
        foreach ($this->routes as $route) {
            if ($route['type'] === 'closure') {
                $function = new ServerlessFunction(
                    name: $route['name'],
                    path: $route['path'],
                    methods: $route['methods'],
                    source: $route['source'],
                    appName: 'Laravel',
                    filePath: $route['file'],
                    lineNumber: $route['line'],
                    requiresDb: $this->databaseDetected
                );

                $this->addImportsToFunction($function, $route['file']);
                $this->addEnvVarsToFunction($function);
                
                $functions[] = $function;
            }
        }

        // Process controller methods
        foreach ($this->controllers as $controller) {
            $matchingRoutes = array_filter($this->routes, function($route) use ($controller) {
                return $route['type'] === 'controller' && 
                       $route['controller'] === $controller['controller'] &&
                       $route['method'] === $controller['method'];
            });

            foreach ($matchingRoutes as $route) {
                $functionName = $controller['controller'] . '@' . $controller['method'];
                
                $function = new ServerlessFunction(
                    name: $functionName,
                    path: $route['path'],
                    methods: $route['methods'],
                    source: $controller['source'],
                    appName: 'Laravel',
                    controller: $controller['controller'],
                    filePath: $controller['file'],
                    lineNumber: $controller['line'],
                    requiresDb: $this->databaseDetected
                );

                $this->addImportsToFunction($function, $controller['file']);
                $this->addEnvVarsToFunction($function);
                
                $functions[] = $function;
            }
        }

        return $functions;
    }

    private function addImportsToFunction(ServerlessFunction $function, string $filePath): void
    {
        if (isset($this->imports[$filePath])) {
            foreach ($this->imports[$filePath] as $import) {
                $function->addImport($import);
            }
        }
        $commonImports = [
            new ImportDefinition('Illuminate\Http\Request', 'Request'),
            new ImportDefinition('Illuminate\Http\Response', 'Response'),
            new ImportDefinition('Illuminate\Support\Facades\Log', 'Log'),
        ];

        foreach ($commonImports as $import) {
            $function->addImport($import);
        }
    }

    private function addEnvVarsToFunction(ServerlessFunction $function): void
    {
        foreach ($this->envVars as $envVar) {
            $function->addEnvVar($envVar);
        }
        $commonEnvVars = ['APP_ENV', 'APP_DEBUG', 'APP_KEY'];
        foreach ($commonEnvVars as $envVar) {
            $function->addEnvVar($envVar);
        }

        if ($this->databaseDetected) {
            $dbEnvVars = ['DB_CONNECTION', 'DB_HOST', 'DB_PORT', 'DB_DATABASE', 'DB_USERNAME', 'DB_PASSWORD'];
            foreach ($dbEnvVars as $envVar) {
                $function->addEnvVar($envVar);
            }
        }
    }
} 
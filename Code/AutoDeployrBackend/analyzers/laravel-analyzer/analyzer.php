#!/usr/bin/env php
<?php

require_once __DIR__ . '/vendor/autoload.php';

use AutoDeployr\LaravelAnalyzer\LaravelApplicationParser;
use Symfony\Component\Console\Application;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputArgument;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Input\InputOption;
use Symfony\Component\Console\Output\OutputInterface;

class AnalyzeCommand extends Command
{
    protected static $defaultName = 'analyze';

    protected function configure()
    {
        $this
            ->setDescription('Analyze Laravel applications for serverless deployment')
            ->addOption('app-path', null, InputOption::VALUE_REQUIRED, 'Path to Laravel application directory')
            ->addOption('file', null, InputOption::VALUE_REQUIRED, 'Analyze a single PHP file')
            ->addOption('output', null, InputOption::VALUE_REQUIRED, 'Output file for results (stdout if not specified)')
            ->addOption('no-fix', null, InputOption::VALUE_NONE, 'Don\'t fix HTTP methods in output files');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        try {
            $appPath = $input->getOption('app-path');
            $file = $input->getOption('file');
            $outputFile = $input->getOption('output');
            $noFix = $input->getOption('no-fix');

            if ($appPath) {
                return $this->analyzeApp($appPath, $outputFile, !$noFix, $output);
            } elseif ($file) {
                return $this->analyzeFile($file, $outputFile, !$noFix, $output);
            } else {
                $output->writeln('<error>Either --app-path or --file must be specified</error>');
                return Command::FAILURE;
            }
        } catch (Exception $e) {
            $output->writeln('<error>Error: ' . $e->getMessage() . '</error>');
            return Command::FAILURE;
        }
    }

    private function analyzeApp(string $appPath, ?string $outputFile, bool $fixMethods, OutputInterface $output): int
    {
        try {
            $parser = new LaravelApplicationParser($appPath);
            $result = $parser->parse();

            $resultJson = json_encode($result, JSON_PRETTY_PRINT);

            if ($outputFile) {
                file_put_contents($outputFile, $resultJson);
                $output->writeln("Analysis saved to $outputFile");
            } else {
                echo $resultJson;
            }

            return Command::SUCCESS;
        } catch (Exception $e) {
            $output->writeln('<error>Error analyzing application: ' . $e->getMessage() . '</error>');
            echo json_encode(['error' => $e->getMessage()]);
            return Command::FAILURE;
        }
    }

    private function analyzeFile(string $filePath, ?string $outputFile, bool $fixMethods, OutputInterface $output): int
    {
        try {
            $filename = basename($filePath);
            $parser = new LaravelApplicationParser('');
            $result = $parser->parseFile($filePath, $filename);

            $resultJson = json_encode($result, JSON_PRETTY_PRINT);

            if ($outputFile) {
                file_put_contents($outputFile, $resultJson);
                $output->writeln("Analysis saved to $outputFile");
            } else {
                echo $resultJson;
            }

            return Command::SUCCESS;
        } catch (Exception $e) {
            $output->writeln('<error>Error analyzing file: ' . $e->getMessage() . '</error>');
            echo json_encode(['error' => $e->getMessage()]);
            return Command::FAILURE;
        }
    }
}

if (php_sapi_name() === 'cli' && isset($argv)) {
    $options = getopt('', ['app-path:', 'file:', 'output:', 'no-fix']);
    
    if (!empty($options)) {
        try {
            $appPath = $options['app-path'] ?? null;
            $file = $options['file'] ?? null;
            $outputFile = $options['output'] ?? null;
            $noFix = isset($options['no-fix']);

            if ($appPath) {
                $parser = new LaravelApplicationParser($appPath);
                $result = $parser->parse();
                $resultJson = json_encode($result, JSON_PRETTY_PRINT);
                
                if ($outputFile) {
                    file_put_contents($outputFile, $resultJson);
                    error_log("Analysis saved to $outputFile");
                } else {
                    echo $resultJson;
                }
                exit(0);
            } elseif ($file) {
                $parser = new LaravelApplicationParser('');
                $result = $parser->parseFile($file, basename($file));
                $resultJson = json_encode($result, JSON_PRETTY_PRINT);
                
                if ($outputFile) {
                    file_put_contents($outputFile, $resultJson);
                    error_log("Analysis saved to $outputFile");
                } else {
                    echo $resultJson;
                }
                exit(0);
            }
        } catch (Exception $e) {
            error_log("Error: " . $e->getMessage());
            echo json_encode(['error' => $e->getMessage()]);
            exit(1);
        }
    }
    $application = new Application('Laravel Analyzer', '1.0.0');
    $application->add(new AnalyzeCommand());
    $application->run();
} 
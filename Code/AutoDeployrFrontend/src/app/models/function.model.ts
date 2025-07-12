export interface Function {
    id?: string;
    name: string;
    path: string;
    methods: string[];
    source: string;
    appName: string;
    language: string;
    framework: string;
    targetFramework?: string;
    filePath?: string;
    requiresDb?: boolean;
    invocationCount?: number;
    totalExecutionTimeMs?: number;
    lastExecutionTimeMs?: number;
    lastInvoked?: string;
    // Security fields
    isPrivate?: boolean;
    apiKey?: string;
    apiKeyGeneratedAt?: string;
  }
  
  export interface FunctionSummary {
    id: string;
    name: string;
    path: string;
    methods: string[];
    appName: string;
    language: string;
    framework: string;
    targetFramework?: string;
    invocationCount: number;
    averageExecutionTimeMs?: number;
    totalExecutionTimeMs?: number;
    lastExecutionTimeMs?: number;
    lastInvoked?: string;
    projectId?: string;
    userId?: string;
    status?: string;
    successRate?: number;
    // Security fields - handle backend field name mismatch
    isPrivate?: boolean;
    private?: boolean; // Backend sends this field name
    apiKey?: string;
    apiKeyGeneratedAt?: string;
  }
  
  export interface FunctionMetrics {
    id: string;
    functionId: string;
    functionName: string;
    appName: string;
    invocationCount: number;
    successCount: number;
    failureCount: number;
    totalExecutionTimeMs: number;
    minExecutionTimeMs: number;
    maxExecutionTimeMs: number;
    lastInvoked: string;
    averageExecutionTimeMs: number;
    successRate: number;
  }
  
  export interface InvokeFunctionRequest {
    method?: string;
    path?: string;
    headers?: Record<string, string>;
    payload: Record<string, any>;
  }
  
  export interface FunctionResponse {
    statusCode: number;
    headers: Record<string, string>;
    body: any;
  }

  // Language options for code generation
  export enum CodeLanguage {
    Python = 'python',
    Java = 'java',
    PHP = 'php'
  }

  // Framework options for code generation
  export enum TargetFramework {
    Flask = 'flask',
    JavaSpring = 'spring',
    Laravel = 'laravel'
  }

  // Available language and framework combinations
  export const supportedLanguageFrameworks = [
    { language: CodeLanguage.Python, framework: TargetFramework.Flask, label: 'Python (Flask)' },
    { language: CodeLanguage.Java, framework: TargetFramework.JavaSpring, label: 'Java (Spring)' },
    { language: CodeLanguage.PHP, framework: TargetFramework.Laravel, label: 'PHP (Laravel)' }
  ];
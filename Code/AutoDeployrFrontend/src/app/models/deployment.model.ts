export interface DeployApplicationRequest {
  appPath: string;
  environmentVariables?: Record<string, string>;
  isPrivate?: boolean;
}

export interface DirectFunctionDeployRequest {
  appName: string;
  language: string;
  functionCode: string;
  environmentVariables?: Record<string, string>;
  isPrivate?: boolean;
}

export interface GitHubDeployRequest {
  repositoryUrl: string;
  branch?: string;
  customAppName?: string;
  username?: string;
  token?: string;
  environmentVariables?: Record<string, string>;
  isPrivate?: boolean;
}


export interface DeploymentResponse {
  status: string;
  message: string;
  deployedFunctions: DeployedFunction[];
  failedFunctions?: string[];
  error?: string;
  deploymentId?: string;
  timestamp?: string;
  deployedFunctionDetails?: DeployedFunctionInfo[];
}

export interface DeployedFunctionInfo {
  functionName: string;
  appName: string;
  functionUrl: string;
  isPrivate: boolean;
  apiKey?: string; // Only included if function is private
  supportedMethods: string[];
}

export interface DeployedFunction {
  id?: string;
  name: string;
  path?: string;
  methods?: string[];
  appName?: string;
  language?: string;
  framework?: string;
  metrics?: any;
  endpointUrl?: string;
}

export interface ApplicationAnalysis {
  language: string;
  framework: string;
  appName: string;
  routes: Route[];
  dbDetected: boolean;
  envVars: string[];
}

export interface Route {
  name: string;
  path: string;
  methods: string[];
  source: string;
  appName: string;
  filePath?: string;
}

export interface PlatformStatus {
  status: string;
  version: string;
  uptime: number;
  activeFunctions: number;
  activeContainers: number;
  totalInvocations: number;
  availableRuntimes: string[];
  resourceUsage: ResourceUsage;
}

export interface ResourceUsage {
  cpuUsage: number;
  memoryUsage: number;
  diskUsage: number;
}
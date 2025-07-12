import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
  FormArray,
  FormsModule,
} from '@angular/forms';
import { DeploymentService } from '../../../services/deployment.service';
import { FunctionService, CodeGenerationRequest, CodeGenerationResponse } from '../../../services/function.service';
import { AuthService } from '../../../services/auth.service';
import { Router, ActivatedRoute } from '@angular/router';
import {
  DeploymentResponse,
  DeployedFunction,
  DeployedFunctionInfo,
  DirectFunctionDeployRequest,
  GitHubDeployRequest,
} from '../../../models/deployment.model';
import { finalize } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import {
  trigger,
  state,
  style,
  transition,
  animate,
  keyframes,
} from '@angular/animations';
import { CodeLanguage, TargetFramework, supportedLanguageFrameworks } from '../../../models/function.model';
import { FunctionSummary } from '../../../models/function.model';

@Component({
  selector: 'app-deploy-application',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './deploy-application.component.html',
  styleUrls: ['./deploy-application.component.css'],
  animations: [
    trigger('promptCodeSwitch', [
      state(
        'prompt',
        style({
          opacity: 1,
          transform: 'translateY(0)',
        })
      ),
      state(
        'code',
        style({
          opacity: 1,
          transform: 'translateY(0)',
        })
      ),
      transition('prompt => code', [
        style({ opacity: 1, transform: 'translateY(0)' }),
        animate(
          '300ms ease-out',
          style({ opacity: 0, transform: 'translateY(-20px)' })
        ),
        animate(
          '500ms ease-in',
          style({ opacity: 1, transform: 'translateY(0)' })
        ),
      ]),
      transition('code => prompt', [
        style({ opacity: 1, transform: 'translateY(0)' }),
        animate(
          '300ms ease-out',
          style({ opacity: 0, transform: 'translateY(20px)' })
        ),
        animate(
          '500ms ease-in',
          style({ opacity: 1, transform: 'translateY(0)' })
        ),
      ]),
    ]),
  ],
})
export class DeployApplicationComponent implements OnInit {
  // Common properties
  currentStep = 1;
  deployForm: FormGroup;
  isLoading = false;
  errorMessage = '';
  deploymentResult: DeploymentResponse | null = null;
  deploymentInitiated = false; // flag to prevent duplicate calls
  copiedFunctionId: string | null = null;
  showToast = false;
  deployedFunctionMetrics: { [functionName: string]: any } = {};

  // API key visibility tracking for deployment results
  visibleApiKeys: Set<string> = new Set();
  
  // Get current user
  get currentUser() {
    return this.authService.getCurrentUser();
  }

  // Tab selection
  activeTab: 'file' | 'direct' | 'github' | 'ai' = 'file'; // Default to file upload

  // Privacy setting for all deployment types
  isPrivate = false;

  // File upload specific properties
  customAppName: string = '';
  appPath = '';
  uploadedFileName = '';
  selectedFile: File | null = null;
  dragOver = false;

  // Direct function properties
  directFunctionForm: FormGroup;
  supportedLanguages = [
    { value: 'java', name: 'Java' },
    { value: 'python', name: 'Python' },
    { value: 'php', name: 'PHP' }
  ];
  
  // Language and framework options from the model
    languageFrameworkOptions = supportedLanguageFrameworks;
  defaultCodeTemplates: { [key: string]: string } = {
    java: `import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExampleController {
    
    @GetMapping("/hello")
    public String hello() {
        return "Hello from serverless function!";
    }
}`,
    python: `# Flask function
from flask import Flask, jsonify, request

app = Flask(__name__)

@app.route('/hello', methods=['GET'])
def hello():
    return jsonify(message='Hello from serverless function!')`,
    php: `<?php
function hello($event, $context) {
    return [
        'statusCode' => 200,
        'headers' => [
            'Content-Type' => 'application/json'
        ],
        'body' => json_encode(['message' => 'Hello from serverless function!'])
    ];
}
`
  };

  // GitHub deployment specific properties
  githubForm: FormGroup;
  supportedBranches = ['main', 'master', 'develop'];
  isPrivateRepo = false;

  // AI generation properties
  aiGenerateForm: FormGroup;
  generatingCode = false;
  generatedCode: string | null = null;
  showPrompt = true; // Controls which view is shown in the animation
  animationState = 'prompt'; // For the animation trigger
  typingAnimation = false; // Controls when the typing animation should play
  showRainbowAnimation = false; // Controls the rainbow animation
  showCodeContent = false; // Controls when code becomes visible

  // Add new properties for app detection and language filtering
  existingApps: FunctionSummary[] = [];
  selectedAppInfo: { name: string; language: string; framework: string } | null = null;
  selectedAiAppInfo: { name: string; language: string; framework: string } | null = null;
  selectedGitHubAppInfo: { name: string; language: string; framework: string } | null = null;
  selectedFileAppInfo: { name: string; language: string; framework: string } | null = null;
  isLanguageDisabled = false;
  filteredLanguages: { value: string; name: string }[] = [];

  constructor(
    private formBuilder: FormBuilder,
    private deploymentService: DeploymentService,
    private router: Router,
    private functionService: FunctionService,
    private authService: AuthService,
    private http: HttpClient,
    private route: ActivatedRoute
  ) {
    // Common env vars form
    this.deployForm = this.formBuilder.group({
      envVars: this.formBuilder.array([]),
    });

    // Direct function form
    this.directFunctionForm = this.formBuilder.group({
      appName: [
        'my-app',
        [Validators.required, Validators.pattern(/^[a-zA-Z0-9_-]+$/)],
      ],
      language: ['python', Validators.required],
      functionCode: ['', Validators.required],
    });

    // GitHub deployment form
    this.githubForm = this.formBuilder.group({
      repositoryUrl: [
        '',
        [
          Validators.required,
          Validators.pattern(/^https?:\/\/github\.com\/[^\/]+\/[^\/]+/),
        ],
      ],
      branch: ['main', Validators.required],
      customAppName: ['', Validators.pattern(/^[a-zA-Z0-9_-]*$/)],
      isPrivateRepo: [false],
      username: [''],
      token: [''],
    });

    // AI Generate form
    this.aiGenerateForm = this.formBuilder.group({
      appName: [
        'my-app',
        [Validators.required, Validators.pattern(/^[a-zA-Z0-9_-]+$/)],
      ],
      language: ['python', Validators.required],
      targetFramework: ['flask', Validators.required],
      functionDescription: [
        '',
        [Validators.required, Validators.minLength(10)],
      ],
    });

    // Initialize filtered languages with all supported languages
    this.filteredLanguages = [...this.supportedLanguages];
  }

  ngOnInit(): void {
    this.addEnvVar(); // Add one empty environment variable row by default

    // Check for query parameters to auto-select deployment method and pre-fill app name
    this.route.queryParams.subscribe(params => {
      if (params['method'] === 'direct') {
        this.activeTab = 'direct';
        
        if (params['appName']) {
          this.directFunctionForm.get('appName')?.setValue(params['appName']);
        }
      }
    });

    // Set default code template when language changes
    this.directFunctionForm
      .get('language')
      ?.valueChanges.subscribe((language) => {
        this.directFunctionForm
          .get('functionCode')
          ?.setValue(this.defaultCodeTemplates[language] || '');
      });

    // Initialize with default code
    this.directFunctionForm
      .get('functionCode')
      ?.setValue(
        this.defaultCodeTemplates[
          this.directFunctionForm.get('language')?.value || 'python'
        ]
      );

    // Add validation for private repo credentials
    this.githubForm
      .get('isPrivateRepo')
      ?.valueChanges.subscribe((isPrivate) => {
        this.isPrivateRepo = isPrivate ?? false;
        if (this.isPrivateRepo) {
          this.githubForm.get('username')?.setValidators([Validators.required]);
          this.githubForm.get('token')?.setValidators([Validators.required]);
        } else {
          this.githubForm.get('username')?.clearValidators();
          this.githubForm.get('token')?.clearValidators();
        }
      });
      
    // Handle language and framework synchronization for AI form
    this.aiGenerateForm
      .get('language')
      ?.valueChanges.subscribe((language) => {
        // Find the matching framework for the selected language
        const matchingOption = this.languageFrameworkOptions.find(
          option => option.language === language
        );
        
        if (matchingOption) {
          // Update the framework dropdown
          this.aiGenerateForm.get('targetFramework')?.setValue(matchingOption.framework);
        }
      });

    // Load existing functions for app detection
    this.loadExistingApps();

    // Check for route parameters (when coming from "deploy to app" button)
    this.route.queryParams.subscribe(params => {
      if (params['method'] === 'direct') {
        this.setActiveTab('direct');
        
        if (params['appName'] && params['language'] && params['framework']) {
          // Full app info provided - pre-select everything
          this.preSelectAppInfo({
            name: params['appName'],
            language: params['language'],
            framework: params['framework']
          });
        } else if (params['appName']) {
          // Only app name provided - set it and let detection handle language
          this.directFunctionForm.patchValue({
            appName: params['appName']
          });
          this.handleAppNameChange(params['appName']);
        }
      }
    });

    // Watch for app name changes in direct function form
    this.directFunctionForm.get('appName')?.valueChanges.subscribe(appName => {
      this.handleAppNameChange(appName);
    });

    // Watch for app name changes in AI generation form
    this.aiGenerateForm.get('appName')?.valueChanges.subscribe(appName => {
      this.handleAiAppNameChange(appName);
    });

    // Watch for app name changes in GitHub form (customAppName field)
    this.githubForm.get('customAppName')?.valueChanges.subscribe(appName => {
      this.handleGitHubAppNameChange(appName);
    });
  }

  // Tab handling
  setActiveTab(tab: 'file' | 'direct' | 'github' | 'ai') {
    this.activeTab = tab;
  }

  // Toggle private repository checkbox
  togglePrivateRepo(event: Event) {
    const isChecked = (event.target as HTMLInputElement).checked;
    this.githubForm.get('isPrivateRepo')?.setValue(isChecked);
  }

  // Drag and drop methods
  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = true;
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = false;
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = false;

    if (event.dataTransfer && event.dataTransfer.files.length > 0) {
      const files = event.dataTransfer.files;
      // Only accept the first file
      if (files[0].name.toLowerCase().endsWith('.zip')) {
        this.selectedFile = files[0];
        this.uploadedFileName = this.selectedFile.name;
        this.appPath = '';
      } else {
        this.errorMessage = 'Only ZIP files are supported.';
      }
    }
  }

  get envVarsControls() {
    return this.deployForm.get('envVars') as FormArray;
  }

  addEnvVar() {
    this.envVarsControls.push(
      this.formBuilder.group({
        key: ['', Validators.required],
        value: [''],
      })
    );
  }

  removeEnvVar(index: number) {
    this.envVarsControls.removeAt(index);
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      if (file.name.toLowerCase().endsWith('.zip')) {
        this.selectedFile = file;
        this.uploadedFileName = this.selectedFile.name;
        // Clear any manually provided appPath when a file is selected
        this.appPath = '';
        // Clear any previous error message
        this.errorMessage = '';
      } else {
        this.errorMessage = 'Only ZIP files are supported.';
        // Clear the file input
        input.value = '';
        this.selectedFile = null;
        this.uploadedFileName = '';
      }
    }
  }

  isStep1Valid(): boolean {
    switch (this.activeTab) {
      case 'file':
        return !!this.selectedFile || !!this.appPath;

      case 'direct':
        return this.directFunctionForm.valid;

      case 'github':
        return this.githubForm.valid;

      case 'ai':
        // For AI tab, we need both valid form and generated code
        return (
          this.aiGenerateForm.valid && !!this.generatedCode && !this.showPrompt
        );

      default:
        return false;
    }
  }

  goToStep(step: number) {
    this.currentStep = step;
  }

  /**
   * Process the deployment result to ensure all functions have the required properties
   */
  processDeploymentResult(result: DeploymentResponse): DeploymentResponse {
    if (
      result &&
      result.deployedFunctions &&
      Array.isArray(result.deployedFunctions)
    ) {
      // Cast the deployedFunctions to string[] since that's what the backend returns
      const fnNames = result.deployedFunctions as unknown as string[];
      result.deployedFunctions = fnNames.map((fnName: string) => {
        let appName = '';

        if (this.activeTab === 'file') {
          appName = this.customAppName || 'default-app';
        } else if (this.activeTab === 'direct') {
          appName =
            this.directFunctionForm.get('appName')?.value || 'default-app';
        } else if (this.activeTab === 'github') {
          appName =
            this.githubForm.get('customAppName')?.value ||
            this.extractAppNameFromUrl(
              this.githubForm.get('repositoryUrl')?.value
            ) ||
            'default-app';
        } else if (this.activeTab === 'ai') {
          appName = this.aiGenerateForm.get('appName')?.value || 'default-app';
        }

        return {
          id: fnName, // using function name as ID
          name: fnName,
          appName: appName,
        } as DeployedFunction;
      });
    }
    return result;
  }

  // Extract app name from GitHub repository URL
  extractAppNameFromUrl(url: string): string {
    if (!url) return '';

    // Extract the repository name from the URL
    const matches = url.match(/github\.com\/[^\/]+\/([^\/\.]+)/);
    if (matches && matches[1]) {
      return matches[1].toLowerCase();
    }
    return '';
  }

  copyFunctionUrl(func: DeployedFunction | any) {
    // Handle both DeployedFunction and DeployedFunctionInfo types
    let url: string;
    let functionName: string;

    if ('functionUrl' in func && func.functionUrl) {
      // This is a DeployedFunctionInfo with full URL
      url = `http://localhost:8080${func.functionUrl}`;
      functionName = func.functionName;
    } else {
      // This is a DeployedFunction, construct URL
      let appName = func.appName || '';

      if (!appName) {
        if (this.activeTab === 'file') {
          appName = this.customAppName;
        } else if (this.activeTab === 'direct') {
          appName = this.directFunctionForm.get('appName')?.value;
        } else if (this.activeTab === 'github') {
          appName =
            this.githubForm.get('customAppName')?.value ||
            this.extractAppNameFromUrl(
              this.githubForm.get('repositoryUrl')?.value
            );
        } else if (this.activeTab === 'ai') {
          appName = this.aiGenerateForm.get('appName')?.value;
        }
      }

      const name = func.name || '';
      functionName = name;

      // Get the current user's username for the URL
      const currentUser = this.authService.getCurrentUser();
      if (!currentUser || !currentUser.username) {
        console.error('User must be logged in to copy function URL');
        return;
      }

      // For the path, use the default path structure
      let path = func.path || '';
      // Since we removed route fields, use a default path structure
      if (!path) {
        path = '';
      }

      if (path && !path.startsWith('/')) {
        path = '/' + path;
      }

      // Updated URL format to include username
      url = `http://localhost:8080/api/v1/${currentUser.username}/functions/${appName}/${name}${path}`;
    }

    // Create a temporary textarea element to copy the text
    const textArea = document.createElement('textarea');
    textArea.value = url;

    // Make the textarea out of viewport
    textArea.style.position = 'fixed';
    textArea.style.left = '-999999px';
    textArea.style.top = '-999999px';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();

    try {
      // Execute the copy command
      document.execCommand('copy');

      // Set the copied function ID to show the confirmation message
      this.copiedFunctionId = functionName;

      // Show the toast notification
      this.showToast = true;

      // Reset after 2 seconds
      setTimeout(() => {
        this.showToast = false;
        this.copiedFunctionId = null;
      }, 2000);
    } catch (err) {
      console.error('Could not copy text: ', err);
    }

    document.body.removeChild(textArea);
  }

  deployApplication() {
    if (this.isLoading || this.deploymentInitiated) {
      return; // Prevent multiple deploy clicks
    }

    // Build environment variables from the form array
    const environmentVariables: Record<string, string> = {};

    for (const control of this.envVarsControls.controls) {
      const key = control.get('key')?.value;
      const value = control.get('value')?.value;

      if (key && value) {
        environmentVariables[key] = value;
      }
    }

    this.isLoading = true;
    this.deploymentInitiated = true;
    this.errorMessage = '';

    // Different deployment methods based on the active tab
    try {
      switch (this.activeTab) {
        case 'file':
          if (this.appPath || this.selectedFile) {
            this.uploadAndDeploy(environmentVariables);
          } else {
            this.errorMessage = 'Please select a ZIP file to deploy';
            this.isLoading = false;
            this.deploymentInitiated = false;
          }
          break;

        case 'direct':
          if (this.directFunctionForm.valid) {
            this.deployDirectFunction(environmentVariables);
          } else {
            this.errorMessage = 'Please fill out all required fields';
            // Mark all fields as touched to show validation errors
            Object.keys(this.directFunctionForm.controls).forEach((key) => {
              this.directFunctionForm.get(key)?.markAsTouched();
            });
            this.isLoading = false;
            this.deploymentInitiated = false;
          }
          break;

        case 'github':
          if (this.githubForm.valid) {
            this.deployFromGitHub(environmentVariables);
          } else {
            this.errorMessage = 'Please fill out all required fields correctly';
            // Mark all fields as touched to show validation errors
            Object.keys(this.githubForm.controls).forEach((key) => {
              this.githubForm.get(key)?.markAsTouched();
            });
            this.isLoading = false;
            this.deploymentInitiated = false;
          }
          break;

        case 'ai':
          if (this.generatedCode) {
            this.deployAiFunction(environmentVariables);
          } else {
            this.errorMessage = 'Please generate code first';
            this.isLoading = false;
            this.deploymentInitiated = false;
          }
          break;

        default:
          this.errorMessage = 'Invalid deployment method';
          this.isLoading = false;
          this.deploymentInitiated = false;
          break;
      }
    } catch (error) {
      console.error('Error in deployApplication:', error);
      this.errorMessage = `Deployment error: ${
        error instanceof Error ? error.message : String(error)
      }`;
      this.isLoading = false;
      this.deploymentInitiated = false;
    }
  }

  uploadAndDeploy(environmentVariables: Record<string, string>) {
    if (!this.selectedFile) {
      this.errorMessage = 'Please select a file to upload.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    // Prepare FormData to include file, appName, environment variables, and privacy setting
    const formData = new FormData();
    formData.append('file', this.selectedFile);

    if (this.customAppName && this.customAppName.trim().length > 0) {
      formData.append('appName', this.customAppName.trim());
    }

    // Add isPrivate parameter
    formData.append('isPrivate', this.isPrivate.toString());

    // Convert environment variables to the expected format
    const envList: string[] = [];
    Object.entries(environmentVariables).forEach(([key, value]) => {
      if (key && key.trim().length > 0) {
        envList.push(`${key}=${value}`);
      }
    });
    envList.forEach((env) => formData.append('env', env));

    this.deploymentService
      .uploadAndDeployZip(formData)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: (response) => {
          if (response.success && response.data) {
            this.deploymentResult = this.processDeploymentResult(response.data);
            this.goToStep(3);
            this.loadMetricsForDeployedFunctions();
          } else {
            this.errorMessage = response.message || 'Deployment failed';
          }
        },
        error: (error) => {
          console.error('Deployment error:', error);
          this.errorMessage =
            error.error?.message || 'An unexpected error occurred';
        },
      });
  }

  deploy(appPath: string, environmentVariables: Record<string, string>) {
    this.isLoading = true;
    this.errorMessage = '';

    this.deploymentService
      .deployApplication({
        appPath,
        environmentVariables,
        isPrivate: this.isPrivate,
      })
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: (response) => {
          if (response.success && response.data) {
            this.deploymentResult = this.processDeploymentResult(response.data);
            this.goToStep(3);
            this.loadMetricsForDeployedFunctions();
          } else {
            this.errorMessage = response.message || 'Deployment failed';
          }
        },
        error: (error) => {
          console.error('Deployment error:', error);
          this.errorMessage =
            error.error?.message || 'An unexpected error occurred';
        },
      });
  }

  deployDirectFunction(environmentVariables: Record<string, string>) {
    if (this.directFunctionForm.invalid) {
      this.errorMessage = 'Please fill in all required fields correctly';
      this.isLoading = false;
      this.deploymentInitiated = false;
      return;
    }

    const directRequest: DirectFunctionDeployRequest = {
      appName: this.directFunctionForm.get('appName')?.value,
      language: this.directFunctionForm.get('language')?.value,
      functionCode: this.directFunctionForm.get('functionCode')?.value,
      environmentVariables: environmentVariables,
      isPrivate: this.isPrivate,
    };

    this.deploymentService
      .deployDirectFunction(directRequest)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: (response) => {
          if (response.success && response.data) {
            this.deploymentResult = this.processDeploymentResult(response.data);
            this.goToStep(3);
            // Load metrics for each deployed function
            this.loadMetricsForDeployedFunctions();
          } else {
            this.errorMessage = response.message || 'Deployment failed';
            this.deploymentInitiated = false;
          }
        },
        error: (error) => {
          console.error('Direct function deployment error:', error);
          this.errorMessage =
            error.error?.message ||
            'An unexpected error occurred during function deployment';
          this.deploymentInitiated = false;
        },
      });
  }

  deployFromGitHub(environmentVariables: Record<string, string>) {
    if (this.githubForm.invalid) {
      this.errorMessage = 'Please fill in all required fields correctly';
      this.isLoading = false;
      this.deploymentInitiated = false;
      return;
    }

    // Note: User authentication is handled automatically by the auth interceptor via Bearer token
    // The backend extracts user ID from the JWT token, so no need to manually add it
    const githubRequest: GitHubDeployRequest = {
      repositoryUrl: this.githubForm.get('repositoryUrl')?.value ?? '',
      branch: this.githubForm.get('branch')?.value ?? 'main',
      customAppName: this.githubForm.get('customAppName')?.value || '',
      environmentVariables: environmentVariables,
      isPrivate: this.isPrivate,
    };

    // Add credentials for private repositories
    if (this.isPrivateRepo) {
      githubRequest.username = this.githubForm.get('username')?.value ?? '';
      githubRequest.token = this.githubForm.get('token')?.value ?? '';
    }

    this.deploymentService
      .deployFromGitHub(githubRequest)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: (response) => {
          if (response.success && response.data) {
            this.deploymentResult = this.processDeploymentResult(response.data);
            this.goToStep(3);
            // Load metrics for each deployed function
            this.loadMetricsForDeployedFunctions();
          } else {
            this.errorMessage = response.message || 'GitHub deployment failed';
            this.deploymentInitiated = false;
          }
        },
        error: (error) => {
          console.error('GitHub deployment error:', error);
          this.errorMessage =
            error.error?.message ||
            'An unexpected error occurred during GitHub deployment';
          this.deploymentInitiated = false;
        },
      });
  }

  // AI code generation with improved animation sequence
  generateCode() {
    if (this.aiGenerateForm.invalid) {
      return;
    }

    // Reset all animation states
    this.generatingCode = true;
    this.generatedCode = null;
    this.errorMessage = '';
    this.typingAnimation = false;
    this.showRainbowAnimation = false;
    this.showCodeContent = false; // Important: ensure code doesn't appear too early

    const description = this.aiGenerateForm.get('functionDescription')?.value;
    const language = this.aiGenerateForm.get('language')?.value;
    
    // Set the targetFramework based on the selected language
    const framework = this.getFrameworkForLanguage(language);
    this.aiGenerateForm.get('targetFramework')?.setValue(framework);
    
    const targetFramework = this.aiGenerateForm.get('targetFramework')?.value;

    console.log('Sending AI request:', {
      prompt: description,
      language: language,
      targetFramework: targetFramework
    });

    const request: CodeGenerationRequest = {
      prompt: description,
      language: language,
      targetFramework: targetFramework
    };

    // Use our FunctionService instead of direct HTTP call
    this.functionService.generateCode(request)
      .pipe(
        finalize(() => {
          // Keep animation running a bit longer for dramatic effect
          setTimeout(() => {
            this.generatingCode = false;

            if (this.generatedCode) {
              this.animateCodeGeneration();
            }
          }, 1800); // Slightly longer to make the animation feel more complete
        })
      )
      .subscribe({
        next: (response: CodeGenerationResponse) => {
          console.log('AI response:', response);

          this.generatedCode = response.code;

          if (!this.generatedCode) {
            console.error('No code found in response:', response);
            this.errorMessage = 'The AI service did not return any code. Please try again.';
          } else {
            console.log('Found code in standard property:', this.generatedCode);
          }
        },
        error: (error) => {
          console.error('Error generating code:', error);
          this.errorMessage =
            error.error?.message ||
            'Failed to generate code. Please try again.';
        },
      });
  }

  // Animation sequence that ensures code only appears after rainbow animation
  animateCodeGeneration() {
    // Make code visible and start animations simultaneously
    this.showPrompt = false;
    this.showCodeContent = true; // Show code immediately at the start
    this.typingAnimation = true; // Start typing animation right away
    this.animationState = 'code'; // Trigger the main container animation
    
    // Add rainbow animation shortly after
    setTimeout(() => {
      this.showRainbowAnimation = true;
      
      // Turn off rainbow animation after it completes
      setTimeout(() => {
        this.showRainbowAnimation = false;
      }, 1500);
    }, 400);
  }

  // Improved regeneration sequence
  regenerateCode() {
    if (this.showPrompt) {
      this.generateCode();
    } else {
      // Reset animation states
      this.showRainbowAnimation = false;
      this.typingAnimation = false;
      this.showCodeContent = false;

      // First animate back to prompt
      this.animationState = 'prompt';

      // After the view transition completes
      setTimeout(() => {
        this.showPrompt = true;

        // Wait for the view to settle before generating new code
        setTimeout(() => {
          this.generateCode();
        }, 300);
      }, 300);
    }
  }

  copyGeneratedCode() {
    if (this.generatedCode) {
      // Get the actual content of the code element (in case it's been edited)
      let codeContent = this.generatedCode;
      const codeElement = document.querySelector(
        '.siri-intelligence-container code'
      );
      if (
        codeElement &&
        codeElement.textContent &&
        codeElement.textContent.trim() !== ''
      ) {
        codeContent = codeElement.textContent;
      }

      navigator.clipboard.writeText(codeContent);

      // Show toast
      this.showToast = true;
      setTimeout(() => {
        this.showToast = false;
      }, 2000);
    }
  }

  // Method to switch back to prompt view without regenerating code
  modifyPrompt() {
    // Reset animation states but preserve the generated code
    this.showRainbowAnimation = false;
    this.typingAnimation = false;
    this.showCodeContent = false;

    // Animate back to prompt view
    this.animationState = 'prompt';

    // After the transition animation completes, show the prompt
    setTimeout(() => {
      this.showPrompt = true;
    }, 300);
  }

  // Deploy the AI-generated function
  deployAiFunction(environmentVariables: Record<string, string>) {
    if (!this.generatedCode) {
      this.errorMessage = 'No code has been generated yet.';
      this.isLoading = false;
      this.deploymentInitiated = false;
      return;
    }

    // Get the content of the code element (in case it's been edited)
    let codeContent = this.generatedCode;
    const codeElement = document.querySelector(
      '.siri-intelligence-container code'
    );
    if (
      codeElement &&
      codeElement.textContent &&
      codeElement.textContent.trim() !== ''
    ) {
      codeContent = codeElement.textContent;
    }

    // Create a deployment request using the AI form values
    const deployRequest: DirectFunctionDeployRequest = {
      appName: this.aiGenerateForm.get('appName')?.value,
      language: this.aiGenerateForm.get('language')?.value,
      functionCode: codeContent,
      environmentVariables: environmentVariables,
      isPrivate: this.isPrivate,
    };

    console.log('Deploying AI function with code:', codeContent);

    this.deploymentService
      .deployDirectFunction(deployRequest)
      .pipe(
        finalize(() => {
          this.isLoading = false;
        })
      )
      .subscribe({
        next: (response) => {
          if (response.success && response.data) {
            this.deploymentResult = this.processDeploymentResult(response.data);
            this.goToStep(3);

            // Load metrics for deployed functions
            setTimeout(() => {
              this.loadMetricsForDeployedFunctions();
            }, 1000);
          } else {
            this.errorMessage = response.message || 'Deployment failed';
            this.deploymentInitiated = false;
          }
        },
        error: (error) => {
          console.error('Deployment error:', error);
          this.errorMessage =
            error.error?.message ||
            'Failed to deploy function. Please try again.';
          this.deploymentInitiated = false;
        },
      });
  }

  loadMetricsForDeployedFunctions() {
    if (this.deploymentResult && this.deploymentResult.deployedFunctions) {
      // Get the current user's username for the URL
      const currentUser = this.authService.getCurrentUser();
      if (!currentUser || !currentUser.username) {
        console.error('User must be logged in to generate function URLs');
        return;
      }

      this.deploymentResult.deployedFunctions.forEach(
        (fn: DeployedFunction) => {
          this.functionService.getFunctionMetrics(fn.id!).subscribe({
            next: (response) => {
              if (response.success && response.data) {
                fn.metrics = response.data;
                // Construct the endpoint URL using username, appName and function name with the new format
                fn.endpointUrl = `http://localhost:8080/api/v1/${currentUser.username}/functions/${fn.appName}/${fn.name}`;
                this.deployedFunctionMetrics[fn.id!] = response.data;
                console.log(`Metrics for ${fn.id}:`, response.data);
              } else {
                console.error(
                  `Failed loading metrics for ${fn.id}:`,
                  response.message
                );
              }
            },
            error: (error) => {
              console.error('Error loading metrics for function', fn.id, error);
            },
          });
        }
      );
    }
  }

  resetForm() {
    this.currentStep = 1;
    this.deployForm.reset();
    this.envVarsControls.clear();
    this.addEnvVar();
    this.appPath = '';
    this.uploadedFileName = '';
    this.selectedFile = null;
    this.deploymentResult = null;
    this.errorMessage = '';
    this.deploymentInitiated = false;
    this.customAppName = '';
    this.dragOver = false;

    // Reset direct function form
    this.directFunctionForm.reset({
      appName: 'my-app',
      language: 'python',
      functionCode: this.defaultCodeTemplates['python'],
    });

    // Reset GitHub form
    this.githubForm.reset({
      repositoryUrl: '',
      branch: 'main',
      customAppName: '',
      isPrivateRepo: false,
      username: '',
      token: '',
    });

    // Reset AI form
    this.aiGenerateForm.reset({
      appName: 'my-app',
      language: 'python',
      targetFramework: 'flask',
      functionDescription: '',
    });

    // Reset AI generation state
    this.generatingCode = false;
    this.generatedCode = null;
    this.showPrompt = true;
    this.animationState = 'prompt';
    this.typingAnimation = false;
    this.showRainbowAnimation = false;
    this.showCodeContent = false;

    // Reset app detection state
    this.selectedAppInfo = null;
    this.selectedAiAppInfo = null;
    this.selectedGitHubAppInfo = null;
    this.selectedFileAppInfo = null;
    this.isLanguageDisabled = false;
    this.filteredLanguages = [...this.supportedLanguages];
  }

  viewFunctions() {
    this.router.navigate(['/functions']);
  }

  // Helper method to get the framework for the selected language
  getFrameworkForLanguage(language: string): string {
    const matchingOption = this.languageFrameworkOptions.find(
      option => option.language === language
    );
    return matchingOption?.framework || 'flask'; // Default to Flask if not found
  }

  /**
   * Load existing apps from functions to enable app detection
   */
  loadExistingApps() {
    this.functionService.getUserFunctions().subscribe({
      next: (response) => {
        if (response.success && response.data) {
          this.existingApps = response.data;
        }
      },
      error: (error) => {
        console.error('Error loading existing apps:', error);
      }
    });
  }

  /**
   * Pre-select app information when coming from "deploy to app" button
   */
  preSelectAppInfo(appInfo: { name: string; language: string; framework: string }) {
    this.selectedAppInfo = appInfo;
    this.directFunctionForm.patchValue({
      appName: appInfo.name,
      language: appInfo.language
    });
    // Allow all languages
    this.isLanguageDisabled = false;
    this.filteredLanguages = [...this.supportedLanguages];
  }

  /**
   * Handle app name changes and detect existing apps
   */
  handleAppNameChange(appName: string) {
    // Always allow all languages
    this.isLanguageDisabled = false;
    this.filteredLanguages = [...this.supportedLanguages];
    
    // Clear selected app info if user changes the name
    if (!appName || appName.trim() === '') {
      this.selectedAppInfo = null;
      return;
    }

    // Check if this is the pre-selected app
    if (this.selectedAppInfo && appName === this.selectedAppInfo.name) {
      return;
    }

    // Check if app name matches an existing app for informational purposes
    const existingApp = this.findExistingApp(appName.trim());
    
    if (existingApp) {
      // Set selected app info for display purposes only
      this.selectedAppInfo = {
        name: existingApp.appName,
        language: existingApp.language || 'unknown',
        framework: existingApp.framework || 'unknown'
      };
    } else {
      // Clear selected app info for new apps
      this.selectedAppInfo = null;
    }
  }

  /**
   * Find existing app by name
   */
  findExistingApp(appName: string): FunctionSummary | null {
    return this.existingApps.find(func => 
      func.appName.toLowerCase() === appName.toLowerCase()
    ) || null;
  }

  /**
   * Get unique app names from existing functions
   */
  getUniqueAppNames(): string[] {
    const appNames = this.existingApps.map(func => func.appName);
    return [...new Set(appNames)];
  }

  /**
   * Check if an app name already exists
   */
  isExistingApp(appName: string): boolean {
    return this.getUniqueAppNames().some(name => 
      name.toLowerCase() === appName.toLowerCase()
    );
  }

  /**
   * Clear app selection and reset form
   */
  clearAppSelection() {
    this.selectedAppInfo = null;
    this.isLanguageDisabled = false;
    this.filteredLanguages = [...this.supportedLanguages];
    this.directFunctionForm.patchValue({
      appName: '',
      language: this.supportedLanguages[0] // Reset to first language
    });
  }

  /**
   * Handle AI form app name changes and detect existing apps
   */
  handleAiAppNameChange(appName: string) {
    // Clear selected app info if user changes the name
    if (!appName || appName.trim() === '') {
      this.selectedAiAppInfo = null;
      return;
    }

    // Check if app name matches an existing app for informational purposes
    const existingApp = this.findExistingApp(appName.trim());
    
    if (existingApp) {
      // Set selected app info for display purposes only
      this.selectedAiAppInfo = {
        name: existingApp.appName,
        language: existingApp.language || 'unknown',
        framework: existingApp.framework || 'unknown'
      };
    } else {
      // Clear selected app info for new apps
      this.selectedAiAppInfo = null;
    }
  }

  /**
   * Handle GitHub form app name changes and detect existing apps
   */
  handleGitHubAppNameChange(appName: string) {
    // Clear selected app info if user changes the name
    if (!appName || appName.trim() === '') {
      this.selectedGitHubAppInfo = null;
      return;
    }

    // Check if app name matches an existing app for informational purposes
    const existingApp = this.findExistingApp(appName.trim());
    
    if (existingApp) {
      // Set selected app info for display purposes only
      this.selectedGitHubAppInfo = {
        name: existingApp.appName,
        language: existingApp.language || 'unknown',
        framework: existingApp.framework || 'unknown'
      };
    } else {
      // Clear selected app info for new apps
      this.selectedGitHubAppInfo = null;
    }
  }

  /**
   * Handle file upload custom app name changes and detect existing apps
   */
  handleFileAppNameChange(appName: string) {
    // Update the customAppName property
    this.customAppName = appName || '';
    
    // Clear selected app info if user changes the name
    if (!appName || appName.trim() === '') {
      this.selectedFileAppInfo = null;
      return;
    }

    // Check if app name matches an existing app for informational purposes
    const existingApp = this.findExistingApp(appName.trim());
    
    if (existingApp) {
      // Set selected app info for display purposes only
      this.selectedFileAppInfo = {
        name: existingApp.appName,
        language: existingApp.language || 'unknown',
        framework: existingApp.framework || 'unknown'
      };
    } else {
      // Clear selected app info for new apps
      this.selectedFileAppInfo = null;
    }
  }

  /**
   * API Key visibility and display methods for deployment results
   */
  
  isApiKeyVisible(functionName: string): boolean {
    return this.visibleApiKeys.has(functionName);
  }

  toggleApiKeyVisibility(functionName: string): void {
    if (this.visibleApiKeys.has(functionName)) {
      this.visibleApiKeys.delete(functionName);
    } else {
      this.visibleApiKeys.add(functionName);
    }
  }

  getApiKeyDisplay(functionName: string, apiKey: string): string {
    if (this.isApiKeyVisible(functionName)) {
      return apiKey;
    } else {
      return '••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••';
    }
  }

  copyApiKey(functionName: string, apiKey: string): void {
    if (!apiKey) {
      console.error('No API key provided to copy');
      return;
    }

    // Create a temporary textarea element to copy the text
    const textArea = document.createElement('textarea');
    textArea.value = apiKey;

    // Make the textarea out of viewport
    textArea.style.position = 'fixed';
    textArea.style.left = '-999999px';
    textArea.style.top = '-999999px';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();

    try {
      // Execute the copy command
      document.execCommand('copy');

      // Set the copied function ID to show the confirmation message
      this.copiedFunctionId = functionName + '_apikey';

      // Reset after 2 seconds
      setTimeout(() => {
        this.copiedFunctionId = null;
      }, 2000);
    } catch (err) {
      console.error('Could not copy API key: ', err);
    }

    document.body.removeChild(textArea);
  }

  /**
   * Track by function for detailed function info
   */
  trackFunctionDetailBy(index: number, item: any): any {
    return item ? item.functionName : index;
  }
}
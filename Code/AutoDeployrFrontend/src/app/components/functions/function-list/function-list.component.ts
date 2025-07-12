import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { FunctionService } from '../../../services/function.service';
import { AuthService } from '../../../services/auth.service';
import { FunctionSummary } from '../../../models/function.model';
import { FunctionMetrics } from '../../../models/function.model';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-function-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: 'function-list.component.html',
  styles: [],
})
export class FunctionListComponent implements OnInit {
  functionId: string = '';
  public metricsMap: { [id: string]: FunctionMetrics } = {};
  functions: FunctionSummary[] = [];
  isLoading = false;
  errorMessage = '';

  copiedFunctionId: string | null = null;
  showToast: boolean = false;
  
  // New properties for privacy management
  visibleApiKeys: Set<string> = new Set();
  togglingPrivacy: Set<string> = new Set();

  constructor(
    private functionService: FunctionService,
    private authService: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadFunctions();
  }

  loadFunctions() {
    this.isLoading = true;
    this.errorMessage = '';

    this.functionService
      .getUserFunctions()
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: (response) => {
          if (response.success) {
            this.functions = response.data || [];
            
            // Fix field name mismatch: map 'private' to 'isPrivate'
            this.functions = this.functions.map(func => this.mapFunctionSecurityFields(func));
            
            // Now iterate over functions after they're loaded
            this.functions.forEach((func) => {
              this.loadFunctionMetricsForFunction(func.id);
            });
          } else {
            this.errorMessage = response.message || 'Failed to load functions';
          }
        },
        error: (error) => {
          console.error('Error loading functions:', error);
          this.errorMessage =
            error.error?.message || 'An unexpected error occurred';
        },
      });
  }

  loadFunctionMetricsForFunction(functionId: string) {
    this.functionService
      .getFunctionMetrics(functionId)
      .pipe(finalize(() => {}))
      .subscribe({
        next: (response) => {
          if (response.success && response.data) {
            // Store metrics per function id in metricsMap
            this.metricsMap[functionId] = response.data;
            console.log(`Metrics for ${functionId}:`, response.data);
          } else {
            console.error(
              `Failed loading metrics for ${functionId}:`,
              response.message
            );
          }
        },
        error: (error) => {
          console.error('Error loading function metrics:', error);
        },
      });
  }

  undeployFunction(appName: string, functionName: string) {
    if (
      !confirm(`Are you sure you want to undeploy function "${functionName}"?`)
    ) {
      return;
    }

    this.isLoading = true;
    this.functionService
      .undeployFunction(appName, functionName)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: (response) => {
          if (response.success) {
            // Remove the function from the list
            this.functions = this.functions.filter(
              (f) => !(f.appName === appName && f.name === functionName)
            );
          } else {
            this.errorMessage =
              response.message || 'Failed to undeploy function';
          }
        },
        error: (error) => {
          console.error('Error undeploying function:', error);
          this.errorMessage =
            error.error?.message || 'An unexpected error occurred';
        },
      });
  }

  getUniqueApps(): string[] {
    return [...new Set(this.functions.map((f) => f.appName))];
  }

  getFunctionsByApp(appName: string): FunctionSummary[] {
    return this.functions.filter((f) => f.appName === appName);
  }

  getDisplayUrl(func: FunctionSummary): string {
    // Get the current user's username for the URL
    const currentUser = this.authService.getCurrentUser();
    if (!currentUser || !currentUser.username) {
      return 'User not logged in';
    }

    const appName = func.appName || '';
    const name = func.name || '';
    
    // Return the display URL with username
    return `http://localhost:8080/api/v1/${currentUser.username}/functions/${appName}/${name}`;
  }

  deployFunctionToApp(appName: string) {
    // Get a function from this app to determine language and framework
    const appFunctions = this.getFunctionsByApp(appName);
    if (appFunctions.length === 0) {
      console.error('No functions found for app:', appName);
      return;
    }

    // Use the first function to get language and framework info
    const referenceFunction = appFunctions[0];
    
    // Navigate to deploy page with query parameters to pre-select direct function and app info
    this.router.navigate(['/deploy'], {
      queryParams: {
        method: 'direct',
        appName: appName,
        language: referenceFunction.language || '',
        framework: referenceFunction.framework || ''
      }
    });
  }

  copyFunctionUrl(func: FunctionSummary) {
    const appName = func.appName || '';
    const name = func.name || '';
    let path = (func as any).path || ''; // Adjust typing if needed
    if (path && !path.startsWith('/')) {
      path = '/' + path;
    }

    // Get the current user's username for the URL
    const currentUser = this.authService.getCurrentUser();
    if (!currentUser || !currentUser.username) {
      console.error('User must be logged in to copy function URL');
      return;
    }

    // Updated URL format to include username
    const url = `http://localhost:8080/api/v1/${currentUser.username}/functions/${appName}/${name}`;
    
    const textArea = document.createElement('textarea');
    textArea.value = url;
    textArea.style.position = 'fixed';
    textArea.style.left = '-9999px';
    textArea.style.top = '-9999px';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    try {
      document.execCommand('copy');
      this.copiedFunctionId = func.name;
      this.showToast = true;
      setTimeout(() => {
        this.showToast = false;
        this.copiedFunctionId = null;
      }, 2000);
    } catch (err) {
      console.error('Failed to copy URL: ', err);
    }
    document.body.removeChild(textArea);
  }

  /**
   * Toggle function privacy between public and private
   */
  toggleFunctionPrivacy(func: FunctionSummary): void {
    if (this.togglingPrivacy.has(func.id)) {
      return; // Already toggling
    }

    this.togglingPrivacy.add(func.id);
    const currentPrivacyState = func.isPrivate === true;
    const newPrivacyState = !currentPrivacyState;

    this.functionService.toggleFunctionSecurity(func.id, newPrivacyState)
      .pipe(finalize(() => this.togglingPrivacy.delete(func.id)))
      .subscribe({
        next: (response) => {
          if (response.success && response.data) {
            // Update the function with new security information
            const updatedIndex = this.functions.findIndex(f => f.id === func.id);
            
            if (updatedIndex !== -1) {
              // Directly update with the response data since backend sends the correct format
              this.functions[updatedIndex] = {
                ...this.functions[updatedIndex],
                isPrivate: response.data.isPrivate,
                apiKey: response.data.apiKey,
                apiKeyGeneratedAt: response.data.apiKeyGeneratedAt
              };
              
              // Clear any error messages
              this.errorMessage = '';
              
              // Trigger change detection to update the UI immediately
              this.cdr.detectChanges();
            }
          } else {
            this.errorMessage = response.message || 'Failed to toggle function privacy';
          }
        },
        error: (error) => {
          console.error('Error toggling function privacy:', error);
          this.errorMessage = error.error?.message || 'An unexpected error occurred';
        }
      });
  }

  /**
   * Toggle API key visibility for a function
   */
  toggleApiKeyVisibility(functionId: string): void {
    if (this.visibleApiKeys.has(functionId)) {
      this.visibleApiKeys.delete(functionId);
    } else {
      this.visibleApiKeys.add(functionId);
    }
  }

  /**
   * Check if API key is visible for a function
   */
  isApiKeyVisible(functionId: string): boolean {
    return this.visibleApiKeys.has(functionId);
  }

  /**
   * Get masked/unmasked API key display
   */
  getApiKeyDisplay(func: FunctionSummary): string {
    if (!func.apiKey) return '';
    
    if (this.isApiKeyVisible(func.id)) {
      return func.apiKey;
    } else {
      return '••••••••••••••••••••••••••••••••';
    }
  }

  /**
   * Copy API key to clipboard
   */
  copyApiKey(func: FunctionSummary): void {
    if (!func.apiKey) return;

    const textArea = document.createElement('textarea');
    textArea.value = func.apiKey;
    textArea.style.position = 'fixed';
    textArea.style.left = '-9999px';
    textArea.style.top = '-9999px';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    
    try {
      document.execCommand('copy');
      this.copiedFunctionId = func.id + '_apikey';
      this.showToast = true;
      setTimeout(() => {
        this.showToast = false;
        this.copiedFunctionId = null;
      }, 2000);
    } catch (err) {
      console.error('Failed to copy API key: ', err);
    }
    document.body.removeChild(textArea);
  }

  /**
   * Check if function privacy is being toggled
   */
  isTogglingPrivacy(functionId: string): boolean {
    return this.togglingPrivacy.has(functionId);
  }

  /**
   * Get invocation count for a function safely
   */
  getInvocationCount(functionId: string): number {
    return this.metricsMap[functionId]?.invocationCount || 0;
  }

  /**
   * Map backend security fields to frontend format
   * Backend sends 'private' but frontend expects 'isPrivate'
   */
  private mapFunctionSecurityFields(func: FunctionSummary): FunctionSummary {
    const backendPrivate = (func as any).private;
    return {
      ...func,
      isPrivate: backendPrivate === true, // Map 'private' to 'isPrivate'
      apiKey: func.apiKey,
      apiKeyGeneratedAt: func.apiKeyGeneratedAt
    };
  }
}

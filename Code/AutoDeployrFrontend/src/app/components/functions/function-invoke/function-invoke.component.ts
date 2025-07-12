import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import {
  FormBuilder,
  FormGroup,
  FormArray,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { FunctionService } from '../../../services/function.service';
import { finalize } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { FunctionSummary } from '../../../models/function.model';

@Component({
  selector: 'app-function-invoke',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  template: `
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
      <div class="md:flex md:items-center md:justify-between">
        <div class="flex-1 min-w-0">
          <h1
            class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate"
          >
            Invoke Function
          </h1>
          <p class="mt-1 text-sm text-gray-500">
            {{ appName }}/{{ functionName }}
          </p>
        </div>
        <div class="mt-4 flex md:mt-0 md:ml-4">
          <a
            routerLink="/functions"
            class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
          >
            Back to Functions
          </a>
        </div>
      </div>

      <!-- Error Message -->
      <div *ngIf="errorMessage" class="mt-4 rounded-md bg-red-50 p-4">
        <div class="flex">
          <div class="flex-shrink-0">
            <svg
              class="h-5 w-5 text-red-400"
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 20 20"
              fill="currentColor"
              aria-hidden="true"
            >
              <path
                fill-rule="evenodd"
                d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
                clip-rule="evenodd"
              />
            </svg>
          </div>
          <div class="ml-3">
            <h3 class="text-sm font-medium text-red-800">{{ errorMessage }}</h3>
          </div>
        </div>
      </div>

      <div class="mt-6 grid grid-cols-1 gap-y-6 gap-x-4 sm:grid-cols-6">
        <!-- Function Invocation Form -->
        <div class="sm:col-span-3">
          <div class="bg-white shadow px-4 py-5 sm:rounded-lg sm:p-6">
            <div class="md:grid md:grid-cols-1 md:gap-6">
              <div class="md:col-span-1">
                <h3 class="text-lg font-medium leading-6 text-gray-900">
                  Request
                </h3>
                <p class="mt-1 text-sm text-gray-500">
                  Configure the request to invoke this function.
                </p>
              </div>

              <div class="mt-5 md:mt-0 md:col-span-1">
                <form [formGroup]="invokeForm" (ngSubmit)="onSubmit()">
                  <!-- HTTP Method -->
                  <div class="mb-4">
                    <label class="block text-sm font-medium text-gray-700"
                      >HTTP Method</label
                    >
                    <select
                      formControlName="method"
                      class="mt-1 block w-full py-2 px-3 border border-gray-300 bg-white rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                    >
                      <option *ngFor="let method of availableMethods" [value]="method">{{ method }}</option>
                    </select>
                    <p *ngIf="hasAvailableMethods()" class="mt-1 text-xs text-gray-500">
                      Available methods: {{ getAvailableMethodsString() }}
                    </p>
                  </div>

                  <!-- Path -->
                  <div class="mb-4">
                    <label class="block text-sm font-medium text-gray-700"
                      >Path</label
                    >
                    <input
                      type="text"
                      formControlName="path"
                      class="mt-1 focus:ring-indigo-500 focus:border-indigo-500 block w-full shadow-sm sm:text-sm border-gray-300 rounded-md"
                      placeholder="?id=123 for query params"
                    />
                  </div>

                  <!-- Headers -->
                  <div class="mb-4">
                    <label class="block text-sm font-medium text-gray-700"
                      >Headers</label
                    >

                    <div formArrayName="headers">
                      <div
                        *ngFor="
                          let header of headersControls.controls;
                          let i = index
                        "
                        [formGroupName]="i"
                        class="flex space-x-2 mt-2 items-start"
                      >
                        <div class="flex-1">
                          <input
                            formControlName="key"
                            type="text"
                            placeholder="Header Name"
                            class="focus:ring-indigo-500 focus:border-indigo-500 block w-full shadow-sm sm:text-sm border-gray-300 rounded-md"
                          />
                        </div>
                        <div class="flex-1">
                          <input
                            formControlName="value"
                            type="text"
                            placeholder="Value"
                            class="focus:ring-indigo-500 focus:border-indigo-500 block w-full shadow-sm sm:text-sm border-gray-300 rounded-md"
                          />
                        </div>
                        <button
                          type="button"
                          (click)="removeHeader(i)"
                          class="inline-flex items-center p-1 border border-transparent rounded-full shadow-sm text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500"
                        >
                          <svg
                            class="h-4 w-4"
                            xmlns="http://www.w3.org/2000/svg"
                            fill="none"
                            viewBox="0 0 24 24"
                            stroke="currentColor"
                          >
                            <path
                              stroke-linecap="round"
                              stroke-linejoin="round"
                              stroke-width="2"
                              d="M6 18L18 6M6 6l12 12"
                            />
                          </svg>
                        </button>
                      </div>
                    </div>

                    <button
                      type="button"
                      (click)="addHeader()"
                      class="mt-2 inline-flex items-center px-2.5 py-1.5 border border-gray-300 shadow-sm text-xs font-medium rounded text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                    >
                      <svg
                        class="mr-1 h-4 w-4"
                        xmlns="http://www.w3.org/2000/svg"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                      >
                        <path
                          stroke-linecap="round"
                          stroke-linejoin="round"
                          stroke-width="2"
                          d="M12 6v6m0 0v6m0-6h6m-6 0H6"
                        />
                      </svg>
                      Add Header
                    </button>
                  </div>

                  <!-- Request Body -->
                  <div class="mb-4">
                    <label class="block text-sm font-medium text-gray-700"
                      >Request Body (JSON)</label
                    >
                    <textarea
                      formControlName="body"
                      rows="8"
                      class="mt-1 focus:ring-indigo-500 focus:border-indigo-500 block w-full shadow-sm sm:text-sm border-gray-300 rounded-md font-mono"
                    ></textarea>
                    <div *ngIf="bodyHasError" class="mt-2 text-sm text-red-600">
                      Invalid JSON format
                    </div>
                    <p *ngIf="!bodySupported()" class="mt-1 text-xs text-gray-500">
                      Note: Body is typically not used for {{ invokeForm.get('method')?.value }} requests
                    </p>
                  </div>

                  <!-- Submit Button -->
                  <div class="flex justify-end">
                    <button
                      type="submit"
                      [disabled]="isLoading || bodyHasError"
                      class="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      <span *ngIf="isLoading" class="mr-2">
                        <!-- Loading spinner -->
                        <svg
                          class="animate-spin h-5 w-5 text-white"
                          xmlns="http://www.w3.org/2000/svg"
                          fill="none"
                          viewBox="0 0 24 24"
                        >
                          <circle
                            class="opacity-25"
                            cx="12"
                            cy="12"
                            r="10"
                            stroke="currentColor"
                            stroke-width="4"
                          ></circle>
                          <path
                            class="opacity-75"
                            fill="currentColor"
                            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                          ></path>
                        </svg>
                      </span>
                      Invoke Function
                    </button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        </div>

        <!-- Response Panel -->
        <div class="sm:col-span-3">
          <div class="bg-white shadow px-4 py-5 sm:rounded-lg sm:p-6">
            <div>
              <h3 class="text-lg font-medium leading-6 text-gray-900">
                Response
              </h3>
              <p class="mt-1 text-sm text-gray-500">
                Function execution result.
              </p>
            </div>

            <div
              *ngIf="!response && !isLoading"
              class="mt-4 p-4 bg-gray-50 rounded-md"
            >
              <p class="text-sm text-gray-500 text-center">
                Invoke the function to see the response
              </p>
            </div>

            <div *ngIf="isLoading" class="mt-4 p-4 flex justify-center">
              <svg
                class="animate-spin h-8 w-8 text-indigo-600"
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
              >
                <circle
                  class="opacity-25"
                  cx="12"
                  cy="12"
                  r="10"
                  stroke="currentColor"
                  stroke-width="4"
                ></circle>
                <path
                  class="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                ></path>
              </svg>
            </div>

            <div *ngIf="response && !isLoading" class="mt-4">
              <!-- Status Code -->
              <div class="mb-3">
                <span class="text-sm font-medium text-gray-700">Status: </span>
                <span
                  class="ml-1 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
                  [ngClass]="{
                    'bg-green-100 text-green-800':
                      response.statusCode >= 200 && response.statusCode < 300,
                    'bg-yellow-100 text-yellow-800':
                      response.statusCode >= 300 && response.statusCode < 400,
                    'bg-red-100 text-red-800': response.statusCode >= 400
                  }"
                >
                  {{ response.statusCode }}
                </span>
              </div>

              <!-- Response Headers -->
              <div
                class="mb-3"
                *ngIf="
                  response.headers && getObjectKeys(response.headers).length > 0
                "
              >
                <h4 class="text-sm font-medium text-gray-700 mb-1">Headers:</h4>
                <div
                  class="bg-gray-50 p-2 rounded-md text-xs font-mono overflow-auto max-h-32"
                >
                  <div *ngFor="let key of getHeaderKeys()">
                    <span class="font-semibold">{{ key }}:</span>
                    {{ response.headers[key] }}
                  </div>
                </div>
              </div>

              <!-- Response Body -->
              <div>
                <h4 class="text-sm font-medium text-gray-700 mb-1">Body:</h4>
                <div
                  class="bg-gray-50 p-3 rounded-md text-sm font-mono overflow-auto max-h-96 whitespace-pre shadow-inner border border-gray-200"
                >
                  <pre>{{ formattedResponseBody }}</pre>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [],
})
export class FunctionInvokeComponent implements OnInit {
  appName: string = '';
  functionName: string = '';
  invokeForm: FormGroup;
  isLoading = false;
  errorMessage = '';
  response: any = null;
  formattedResponseBody = '';
  bodyHasError = false;
  functionDetails: FunctionSummary | null = null;
  availableMethods: string[] = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'];
  
  // Method priority order for auto-selection (if multiple methods available)
  private methodPriority = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'];
  
  // Example JSON templates for different HTTP methods with proper type signature
  private jsonExamples: Record<string, string> = {
    'GET': '{}',
    'DELETE': '{}',
    'POST': `{
  "name": "example",
  "description": "A sample payload",
  "values": [1, 2, 3],
  "options": {
    "enabled": true,
    "priority": "high"
  }
}`,
    'PUT': `{
  "id": 1,
  "name": "updated item",
  "description": "This is an updated item",
  "properties": {
    "status": "active",
    "lastModified": "2025-04-14"
  }
}`,
    'PATCH': `{
  "name": "updated name",
  "properties": {
    "status": "inactive"
  }
}`
  };

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private formBuilder: FormBuilder,
    private http: HttpClient,
    private functionService: FunctionService
  ) {
    this.invokeForm = this.formBuilder.group({
      method: ['GET'],
      path: [''],
      headers: this.formBuilder.array([]),
      body: ['{}'],
    });
  }

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      this.appName = params['appName'];
      this.functionName = params['functionName'];
      
      if (this.appName && this.functionName) {
        this.loadFunctionDetails();
      }
    });

    // Add Content-Type header by default
    this.addHeader();
    const headerControl = this.headersControls.at(0);
    headerControl.get('key')?.setValue('Content-Type');
    headerControl.get('value')?.setValue('application/json');

    // Listen for changes in body to validate JSON
    this.invokeForm.get('body')?.valueChanges.subscribe((value) => {
      this.validateJsonBody(value);
    });
    
    // Listen for method changes to adjust UI accordingly
    this.invokeForm.get('method')?.valueChanges.subscribe((method) => {
      this.onMethodChange(method);
    });
  }
  
  hasAvailableMethods(): boolean {
    return this.functionDetails?.methods !== undefined && 
           Array.isArray(this.functionDetails.methods) && 
           this.functionDetails.methods.length > 0;
  }
  
  getAvailableMethodsString(): string {
    return this.functionDetails?.methods?.join(', ') || '';
  }
  
  hasParameters(): boolean {
    return this.functionDetails?.path?.includes('{') || false;
  }
  
  bodySupported(): boolean {
    const method = this.invokeForm.get('method')?.value;
    return method !== 'GET' && method !== 'DELETE';
  }
  
  loadFunctionDetails() {
    this.isLoading = true;
    this.errorMessage = '';
    
    // Get all functions and find the one matching our appName and functionName
    this.functionService.getUserFunctions()
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: (response) => {
          if (response.success && response.data) {
            // Find the matching function
            this.functionDetails = response.data.find(f => 
              f.appName === this.appName && f.name === this.functionName
            ) || null;
            
            if (this.functionDetails) {
              // Map backend 'private' field to frontend 'isPrivate' field
              const backendPrivate = (this.functionDetails as any).private;
              this.functionDetails = {
                ...this.functionDetails,
                isPrivate: backendPrivate === true
              };
              
              // Update available methods if the function has specific methods
              if (this.functionDetails.methods && this.functionDetails.methods.length > 0) {
                this.availableMethods = this.functionDetails.methods;
                
                // Auto-select the appropriate method based on priority
                this.autoSelectMethod(this.functionDetails.methods);
              }
              
              // Automatically add API key header for private functions
              if (this.functionDetails.isPrivate && this.functionDetails.apiKey) {
                this.addApiKeyHeader(this.functionDetails.apiKey);
              }
              
              // Keep path field empty - user will manually enter path parameters
              // Removed auto-population: this.invokeForm.get('path')?.setValue(this.functionDetails.path);
            }
          } else {
            this.errorMessage = response.message || 'Failed to load function details';
          }
        },
        error: (error) => {
          console.error('Error loading function details:', error);
          this.errorMessage = error.error?.message || 'An unexpected error occurred';
        }
      });
  }
  
  autoSelectMethod(methods: string[]) {
    if (!methods || methods.length === 0) return;
    
    // If only one method is available, use it
    if (methods.length === 1) {
      this.invokeForm.get('method')?.setValue(methods[0]);
      return;
    }
    
    // Try to find the highest priority method from the available methods
    for (const priorityMethod of this.methodPriority) {
      if (methods.includes(priorityMethod)) {
        this.invokeForm.get('method')?.setValue(priorityMethod);
        return;
      }
    }
    
    // If none of the priority methods match, just use the first available method
    this.invokeForm.get('method')?.setValue(methods[0]);
  }
  
  onMethodChange(method: string) {
    // Check if the method is in our known examples
    if (method && this.jsonExamples[method]) {
      this.invokeForm.get('body')?.setValue(this.jsonExamples[method]);
    } else {
      // Fallback for unknown methods
      this.invokeForm.get('body')?.setValue('{}');
    }
  }

  get headersControls() {
    return this.invokeForm.get('headers') as FormArray;
  }

  addHeader() {
    this.headersControls.push(
      this.formBuilder.group({
        key: ['', Validators.required],
        value: [''],
      })
    );
  }

  removeHeader(index: number) {
    this.headersControls.removeAt(index);
  }

  validateJsonBody(value: string) {
    if (!value || value.trim() === '') {
      this.bodyHasError = false;
      return;
    }

    try {
      JSON.parse(value);
      this.bodyHasError = false;
    } catch (e) {
      this.bodyHasError = true;
    }
  }

  onSubmit() {
    if (this.bodyHasError) {
      return;
    }

    this.errorMessage = '';
    this.isLoading = true;
    this.response = null;

    // Parse JSON body directly
    let requestBody = {};
    try {
      const bodyValue = this.invokeForm.get('body')?.value;
      requestBody = bodyValue && bodyValue.trim() !== '' ? JSON.parse(bodyValue) : {};
    } catch (e) {
      this.errorMessage = 'Invalid JSON format in request body';
      this.isLoading = false;
      return;
    }

    // Build complete request payload with path and headers
    const method = this.invokeForm.get('method')?.value;
    const customPath = this.invokeForm.get('path')?.value;
    
    // Build headers object from form array
    const headers: { [key: string]: string } = {};
    this.headersControls.controls.forEach((headerControl: any) => {
      const key = headerControl.get('key')?.value;
      const value = headerControl.get('value')?.value;
      if (key && key.trim() !== '') {
        headers[key] = value || '';
      }
    });

    // Build the complete request payload
    const requestPayload = {
      path: customPath && customPath.trim() !== '' ? customPath : '/',
      headers: headers,
      body: requestBody,
      method: method
    };

    console.log('Sending request payload:', requestPayload);

    this.functionService
      .invokeFunction(method, this.appName, this.functionName, requestPayload)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: (res: any) => {
          console.log('Raw response from API:', JSON.stringify(res));
          
          // Create the response object structure for UI display
          this.response = {
            statusCode: res.statusCode,
            headers: res.headers,
            body: res.body // The actual response from the API
          };
          console.log('Response object:', this.response);

          // Only format the actual API response data for the body section
          this.formattedResponseBody = typeof res.body === 'object' 
            ? JSON.stringify(res.body, null, 2) 
            : String(res.body || '');
        },
        error: (error) => {
          console.error('Error invoking function:', error);
          this.errorMessage = error.error?.message || 'An unexpected error occurred';

          // Build error response with status code
          this.response = {
            statusCode: error.status || 500,
            headers: {},
            body: error.error // The error response from the API
          };

          // Only format the actual error data for the body section
          this.formattedResponseBody = typeof error.error === 'object'
            ? JSON.stringify(error.error, null, 2)
            : String(error.error || '');
        },
      });
  }

  getHeaderKeys(): string[] {
    return this.response && this.response.headers
      ? Object.keys(this.response.headers)
      : [];
  }

  getObjectKeys(obj: any): string[] {
    return Object.keys(obj);
  }

  // Add a helper method to check if a string is valid JSON
  private isJsonString(str: string): boolean {
    try {
      JSON.parse(str);
      return true;
    } catch (e) {
      return false;
    }
  }

  addApiKeyHeader(apiKey: string) {
    // Add function-specific API key header for private functions
    // This is separate from the JWT token which is automatically added by the auth interceptor
    // JWT: Authorization: Bearer <jwt> (handled by auth interceptor for all API calls)
    // Function API Key: X-Function-Key: <api-key> (manually added for private function access)
    this.headersControls.push(
      this.formBuilder.group({
        key: ['X-Function-Key'],
        value: [apiKey],
      })
    );
  }
}
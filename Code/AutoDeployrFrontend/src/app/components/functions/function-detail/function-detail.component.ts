import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FunctionService } from '../../../services/function.service';
import { FunctionMetrics } from '../../../models/function.model';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-function-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
      <div class="md:flex md:items-center md:justify-between">
        <div class="flex-1 min-w-0">
          <h1 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
            Function Details
          </h1>
          <p *ngIf="metrics" class="mt-1 text-sm text-gray-500">
            {{ metrics.appName }}/{{ metrics.functionName }}
          </p>
        </div>
        <div class="mt-4 flex md:mt-0 md:ml-4 space-x-3">
          <a routerLink="/functions" class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500">
            Back to Functions
          </a>
          <a *ngIf="metrics" [routerLink]="['/functions', metrics.appName, metrics.functionName, 'invoke']" class="inline-flex items-center px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500">
            Invoke Function
          </a>
        </div>
      </div>

      <!-- Loading spinner -->
      <div *ngIf="isLoading" class="flex justify-center my-8">
        <svg class="animate-spin h-10 w-10 text-indigo-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
      </div>

      <!-- Error Message -->
      <div *ngIf="errorMessage" class="mt-4 rounded-md bg-red-50 p-4">
        <div class="flex">
          <div class="flex-shrink-0">
            <svg class="h-5 w-5 text-red-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
            </svg>
          </div>
          <div class="ml-3">
            <h3 class="text-sm font-medium text-red-800">{{ errorMessage }}</h3>
          </div>
        </div>
      </div>

      <!-- Function Details -->
      <div *ngIf="metrics && !isLoading" class="mt-6 bg-white shadow overflow-hidden sm:rounded-lg">
        <div class="px-4 py-5 sm:px-6">
          <h3 class="text-lg leading-6 font-medium text-gray-900">Function Information</h3>
          <p class="mt-1 max-w-2xl text-sm text-gray-500">Details about the serverless function.</p>
        </div>
        <div class="border-t border-gray-200 px-4 py-5 sm:px-6">
          <dl class="grid grid-cols-1 gap-x-4 gap-y-8 sm:grid-cols-2">
            <div class="sm:col-span-1">
              <dt class="text-sm font-medium text-gray-500">Function Name</dt>
              <dd class="mt-1 text-sm text-gray-900">{{ metrics.functionName }}</dd>
            </div>
            <div class="sm:col-span-1">
              <dt class="text-sm font-medium text-gray-500">Application</dt>
              <dd class="mt-1 text-sm text-gray-900">{{ metrics.appName }}</dd>
            </div>
            <div class="sm:col-span-1">
              <dt class="text-sm font-medium text-gray-500">Total Invocations</dt>
              <dd class="mt-1 text-sm text-gray-900">{{ metrics.invocationCount }}</dd>
            </div>
            <div class="sm:col-span-1">
              <dt class="text-sm font-medium text-gray-500">Success Rate</dt>
              <dd class="mt-1 text-sm">
                <span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                      [ngClass]="{
                        'bg-green-100 text-green-800': metrics.successRate >= 90,
                        'bg-yellow-100 text-yellow-800': metrics.successRate >= 70 && metrics.successRate < 90,
                        'bg-red-100 text-red-800': metrics.successRate < 70
                      }">
                  {{ metrics.successRate.toFixed(2) }}%
                </span>
              </dd>
            </div>
            <div class="sm:col-span-1">
              <dt class="text-sm font-medium text-gray-500">Successful Invocations</dt>
              <dd class="mt-1 text-sm text-gray-900">{{ metrics.successCount }}</dd>
            </div>
            <div class="sm:col-span-1">
              <dt class="text-sm font-medium text-gray-500">Failed Invocations</dt>
              <dd class="mt-1 text-sm text-gray-900">{{ metrics.failureCount }}</dd>
            </div>
            <div class="sm:col-span-1">
              <dt class="text-sm font-medium text-gray-500">Average Execution Time</dt>
              <dd class="mt-1 text-sm text-gray-900">{{ metrics.averageExecutionTimeMs }} ms</dd>
            </div>
            <div class="sm:col-span-1">
              <dt class="text-sm font-medium text-gray-500">Min/Max Execution Time</dt>
              <dd class="mt-1 text-sm text-gray-900">{{ metrics.minExecutionTimeMs }} / {{ metrics.maxExecutionTimeMs }} ms</dd>
            </div>
            <div class="sm:col-span-2">
              <dt class="text-sm font-medium text-gray-500">Last Invoked</dt>
              <dd class="mt-1 text-sm text-gray-900">
                {{ metrics.lastInvoked ? (metrics.lastInvoked | date:'medium') : 'Never' }}
              </dd>
            </div>
          </dl>
        </div>
      </div>

      <!-- Performance Metrics -->
      <div *ngIf="metrics && !isLoading" class="mt-6 bg-white shadow overflow-hidden sm:rounded-lg">
        <div class="px-4 py-5 sm:px-6">
          <h3 class="text-lg leading-6 font-medium text-gray-900">Performance Metrics</h3>
          <p class="mt-1 max-w-2xl text-sm text-gray-500">Statistical data about function performance.</p>
        </div>
        <div class="border-t border-gray-200">
          <div class="px-4 py-5 sm:px-6">
            <div class="grid grid-cols-1 gap-5 sm:grid-cols-3">
              <div class="bg-indigo-50 overflow-hidden shadow rounded-lg">
                <div class="px-4 py-5 sm:p-6">
                  <dt class="text-sm font-medium text-indigo-800 truncate">Average Response Time</dt>
                  <dd class="mt-1 text-3xl font-semibold text-indigo-900">{{ metrics.averageExecutionTimeMs }} ms</dd>
                </div>
              </div>

              <div class="bg-green-50 overflow-hidden shadow rounded-lg">
                <div class="px-4 py-5 sm:p-6">
                  <dt class="text-sm font-medium text-green-800 truncate">Success Rate</dt>
                  <dd class="mt-1 text-3xl font-semibold text-green-900">{{ metrics.successRate.toFixed(2) }}%</dd>
                </div>
              </div>

              <div class="bg-yellow-50 overflow-hidden shadow rounded-lg">
                <div class="px-4 py-5 sm:p-6">
                  <dt class="text-sm font-medium text-yellow-800 truncate">Total Invocations</dt>
                  <dd class="mt-1 text-3xl font-semibold text-yellow-900">{{ metrics.invocationCount }}</dd>
                </div>
              </div>
            </div>


          </div>
        </div>
      </div>

      <!-- Quick Actions -->
      <div *ngIf="metrics && !isLoading" class="mt-6 bg-white shadow sm:rounded-lg">
        <div class="px-4 py-5 sm:p-6">
          <h3 class="text-lg leading-6 font-medium text-gray-900">Actions</h3>
          <div class="mt-5 space-y-2 sm:space-y-0 sm:space-x-3 sm:flex">
            <a [routerLink]="['/functions', metrics.appName, metrics.functionName, 'invoke']" class="inline-flex items-center px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500">
              <svg class="-ml-1 mr-2 h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              Invoke Function
            </a>
            <button (click)="undeployFunction()" class="inline-flex items-center px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500">
              <svg class="-ml-1 mr-2 h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
              Undeploy Function
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: []
})
export class FunctionDetailComponent implements OnInit {
  functionId: string = '';
  metrics: FunctionMetrics | null | undefined = null;
  isLoading = false;
  errorMessage = '';
  functionUrl: string = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private functionService: FunctionService
  ) {}

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.functionId = params['id'];
      if (this.functionId) {
        this.loadFunctionMetrics();
      }
    });
  }

  loadFunctionMetrics() {
    this.isLoading = true;
    this.errorMessage = '';
    
    // Make sure we're using the function ID, not the name
    this.functionService.getFunctionMetrics(this.functionId)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: (response) => {
          if (response.success) {
            this.metrics = response.data;
            
            // Store the function URL for easy access
            if (this.metrics) {
              this.functionUrl = `http://localhost:8080/api/v1/functions/${this.metrics.appName}/${this.metrics.functionName}`;
            }
          } else {
            this.errorMessage = response.message || 'Failed to load function metrics';
          }
        },
        error: (error) => {
          console.error('Error loading function metrics:', error);
          this.errorMessage = error.error?.message || 'An unexpected error occurred';
        }
      });
  }

  undeployFunction() {
    if (!this.metrics) return;

    if (!confirm(`Are you sure you want to undeploy function "${this.metrics.functionName}"?`)) {
      return;
    }

    this.isLoading = true;
    this.functionService.undeployFunction(this.metrics.appName, this.metrics.functionName)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: (response) => {
          if (response.success) {
            this.router.navigate(['/functions']);
          } else {
            this.errorMessage = response.message || 'Failed to undeploy function';
          }
        },
        error: (error) => {
          console.error('Error undeploying function:', error);
          this.errorMessage = error.error?.message || 'An unexpected error occurred';
        }
      });
  }
}
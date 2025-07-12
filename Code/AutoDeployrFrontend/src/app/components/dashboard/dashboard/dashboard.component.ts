import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FunctionService } from '../../../services/function.service';
import { DeploymentService } from '../../../services/deployment.service';
import { FunctionSummary, FunctionMetrics } from '../../../models/function.model';
import { finalize, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  providers: [FunctionService, DeploymentService],
  template: `
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
      <div class="md:flex md:items-center md:justify-between">
        <div class="flex-1 min-w-0">
          <h1 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
            Dashboard
          </h1>
          <p class="mt-1 text-sm text-gray-500">
            Welcome to your serverless platform
          </p>
        </div>
        <div class="mt-4 flex md:mt-0 md:ml-4 space-x-3">
          <a routerLink="/functions" class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500">
            View Functions
          </a>
          <a routerLink="/deploy" class="inline-flex items-center px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500">
            Deploy Application
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

      <!-- Main Dashboard Content -->
      <div class="mt-8">
        <!-- Stats Overview -->
        <div>
          <h3 class="text-lg leading-6 font-medium text-gray-900">Overview</h3>
          <dl class="mt-5 grid grid-cols-1 gap-5 sm:grid-cols-3">
            <div class="bg-white overflow-hidden shadow rounded-lg">
              <div class="px-4 py-5 sm:p-6">
                <dt class="text-sm font-medium text-gray-500 truncate">Total Functions</dt>
                <dd class="mt-1 text-3xl font-semibold text-gray-900">{{ functions.length }}</dd>
              </div>
              <div class="bg-gray-50 px-4 py-4 sm:px-6">
                <div class="text-sm">
                  <a routerLink="/functions" class="font-medium text-indigo-600 hover:text-indigo-500">
                    View all<span class="sr-only"> functions</span>
                  </a>
                </div>
              </div>
            </div>

            <div class="bg-white overflow-hidden shadow rounded-lg">
              <div class="px-4 py-5 sm:p-6">
                <dt class="text-sm font-medium text-gray-500 truncate">Total Invocations</dt>
                <dd class="mt-1 text-3xl font-semibold text-gray-900">{{ getTotalInvocations() }}</dd>
              </div>
              <div class="bg-gray-50 px-4 py-4 sm:px-6">
                <div class="text-sm">
                  <a href="#recent-invocations" class="font-medium text-indigo-600 hover:text-indigo-500">
                    View recent<span class="sr-only"> invocations</span>
                  </a>
                </div>
              </div>
            </div>

            <div class="bg-white overflow-hidden shadow rounded-lg">
              <div class="px-4 py-5 sm:p-6">
                <dt class="text-sm font-medium text-gray-500 truncate">Applications</dt>
                <dd class="mt-1 text-3xl font-semibold text-gray-900">{{ getUniqueAppCount() }}</dd>
              </div>
              <div class="bg-gray-50 px-4 py-4 sm:px-6">
                <div class="text-sm">
                  <a routerLink="/deploy" class="font-medium text-indigo-600 hover:text-indigo-500">
                    Deploy new<span class="sr-only"> application</span>
                  </a>
                </div>
              </div>
            </div>
          </dl>
        </div>

        <!-- Recent Applications -->
        <div class="mt-8">
          <div class="flex items-center">
            <h3 class="text-lg leading-6 font-medium text-gray-900">Recent Applications</h3>
          </div>
          
          <div *ngIf="!isLoading && functions.length === 0" class="mt-4 bg-white shadow overflow-hidden sm:rounded-md p-6 text-center">
            <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 14l-7 7m0 0l-7-7m7 7V3"></path>
            </svg>
            <h3 class="mt-2 text-sm font-medium text-gray-900">No applications deployed yet</h3>
            <p class="mt-1 text-sm text-gray-500">
              Get started by deploying your first application.
            </p>
            <div class="mt-6">
              <a routerLink="/deploy" class="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500">
                <svg class="-ml-1 mr-2 h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                </svg>
                Deploy Application
              </a>
            </div>
          </div>

          <div *ngIf="!isLoading && functions.length > 0" class="mt-4 bg-white shadow overflow-hidden sm:rounded-md">
            <ul role="list" class="divide-y divide-gray-200">
              <li *ngFor="let app of getRecentApps()">
                <div class="px-4 py-4 sm:px-6">
                  <div class="flex items-center justify-between">
                    <div class="flex flex-col text-sm font-medium text-indigo-600 truncate">
                      <p>{{ app }}</p>
                      <p class="text-gray-500 text-xs mt-1">{{ getFunctionsByApp(app).length }} functions</p>
                    </div>
                    <div class="ml-2 flex-shrink-0 flex">
                      <a [routerLink]="['/functions']" [queryParams]="{app: app}" class="font-medium text-indigo-600 hover:text-indigo-500">
                        View Details
                      </a>
                    </div>
                  </div>
                  <div class="mt-4">
                    <div class="flex space-x-2 overflow-x-auto pb-1">
                      <span *ngFor="let func of getRecentFunctionsByApp(app, 5)" 
                            class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800">
                        {{ func.name }}
                      </span>
                      <span *ngIf="getFunctionsByApp(app).length > 5" 
                            class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800">
                        +{{ getFunctionsByApp(app).length - 5 }} more
                      </span>
                    </div>
                  </div>
                </div>
              </li>
            </ul>
          </div>
        </div>

        <!-- Most Used Functions -->
        <div class="mt-8" *ngIf="!isLoading && getInvokedFunctions().length > 0">
          <div class="flex items-center">
            <h3 class="text-lg leading-6 font-medium text-gray-900">Most Used Functions</h3>
          </div>
          <div class="mt-4 bg-white shadow overflow-hidden sm:rounded-md">
            <ul role="list" class="divide-y divide-gray-200">
              <li *ngFor="let func of getMostUsedFunctions(5)">
                <div class="px-4 py-4 sm:px-6">
                  <div class="flex items-center justify-between">
                    <div class="text-sm font-medium text-indigo-600 truncate">
                      {{ func.appName }}/{{ func.name }}
                    </div>
                    <div class="ml-2 flex-shrink-0 flex">
                      <span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-100 text-green-800">
                        {{ getFunctionInvocationCount(func.id) }} invocations
                      </span>
                    </div>
                  </div>
                  <div class="mt-2 sm:flex sm:justify-between">
                    <div class="sm:flex">
                      <p class="flex items-center text-sm text-gray-500">
                        <svg class="flex-shrink-0 mr-1.5 h-5 w-5 text-gray-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                          <path fill-rule="evenodd" d="M3 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z" clip-rule="evenodd" />
                        </svg>
                        {{ func.methods.join(', ') || 'No methods' }}
                      </p>
                    </div>
                    <div class="mt-2 flex items-center text-sm text-gray-500 sm:mt-0">
                      <a [routerLink]="['/functions', func.id]" 
                         class="font-medium text-indigo-600 hover:text-indigo-500 mr-4">
                        Details
                      </a>
                      <a [routerLink]="['/functions', func.appName, func.name, 'invoke']" 
                         class="font-medium text-indigo-600 hover:text-indigo-500">
                        Invoke
                      </a>
                    </div>
                  </div>
                </div>
              </li>
            </ul>
          </div>
        </div>

        <!-- Quick Actions -->
        <div class="mt-8 bg-white shadow sm:rounded-lg">
          <div class="px-4 py-5 sm:p-6">
            <h3 class="text-lg leading-6 font-medium text-gray-900">Quick Actions</h3>
            <div class="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <a routerLink="/deploy" class="bg-gray-50 hover:bg-gray-100 p-4 rounded-md flex items-center">
                <svg class="h-6 w-6 text-indigo-600 mr-3" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4" />
                </svg>
                <span class="text-sm font-medium text-gray-900">Deploy New Application</span>
              </a>
              <a routerLink="/functions" class="bg-gray-50 hover:bg-gray-100 p-4 rounded-md flex items-center">
                <svg class="h-6 w-6 text-indigo-600 mr-3" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 12l3-3 3 3 4-4M8 21l4-4 4 4M3 4h18M4 4h16v12a1 1 0 01-1 1H5a1 1 0 01-1-1V4z" />
                </svg>
                <span class="text-sm font-medium text-gray-900">Manage Functions</span>
              </a>
              <a routerLink="/profile" class="bg-gray-50 hover:bg-gray-100 p-4 rounded-md flex items-center">
                <svg class="h-6 w-6 text-indigo-600 mr-3" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5.121 17.804A13.937 13.937 0 0112 16c2.5 0 4.847.655 6.879 1.804M15 10a3 3 0 11-6 0 3 3 0 016 0zm6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span class="text-sm font-medium text-gray-900">Update Profile</span>
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: []
})
export class DashboardComponent implements OnInit {
  functions: FunctionSummary[] = [];
  public metricsMap: { [id: string]: FunctionMetrics } = {};
  isLoading = false;
  errorMessage = '';

  constructor(
    private functionService: FunctionService,
    private deploymentService: DeploymentService
  ) {}

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData() {
    this.isLoading = true;
    this.errorMessage = '';
    
    // Load functions first, then load metrics for each function
    this.functionService.getUserFunctions().pipe(
      catchError(error => {
        console.error('Error loading functions:', error);
        this.errorMessage = 'Failed to load functions data.';
        return of({ success: false, data: [] });
      }),
      finalize(() => this.isLoading = false)
    ).subscribe(results => {
      if (results.success) {
        this.functions = results.data || [];
        // Load metrics for each function
        this.functions.forEach((func) => {
          this.loadFunctionMetricsForFunction(func.id);
        });
      }
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

  getUniqueAppCount(): number {
    return new Set(this.functions.map(f => f.appName)).size;
  }

  getTotalInvocations(): number {
    // Sum up invocation counts from metrics data (more accurate than basic function data)
    return Object.values(this.metricsMap).reduce((total, metrics) => total + (metrics?.invocationCount || 0), 0);
  }

  getRecentApps(count: number = 3): string[] {
    const appNames = [...new Set(this.functions.map(f => f.appName))];
    return appNames.slice(0, count);
  }

  getFunctionsByApp(appName: string): FunctionSummary[] {
    return this.functions.filter(f => f.appName === appName);
  }

  getRecentFunctionsByApp(appName: string, count: number): FunctionSummary[] {
    return this.getFunctionsByApp(appName).slice(0, count);
  }

  getInvokedFunctions(): FunctionSummary[] {
    return this.functions.filter(f => this.getFunctionInvocationCount(f.id) > 0);
  }

  getMostUsedFunctions(count: number): FunctionSummary[] {
    return [...this.functions]
      .sort((a, b) => {
        const aInvocations = this.getFunctionInvocationCount(a.id);
        const bInvocations = this.getFunctionInvocationCount(b.id);
        return bInvocations - aInvocations;
      })
      .slice(0, count);
  }

  getFunctionInvocationCount(functionId: string): number {
    return this.metricsMap[functionId]?.invocationCount || 0;
  }
}
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { DeploymentService } from '../../../services/deployment.service';
import { PlatformStatus } from '../../../models/deployment.model';
import { finalize, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';

@Component({
  selector: 'app-platform-status',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
      <div class="md:flex md:items-center md:justify-between">
        <div class="flex-1 min-w-0">
          <h1 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
            Platform Status
          </h1>
          <p class="mt-1 text-sm text-gray-500">
            Monitoring dashboard for the serverless platform
          </p>
        </div>
        <div class="mt-4 flex md:mt-0 md:ml-4 space-x-3">
          <button (click)="refreshStatus()" 
                  [disabled]="isLoading"
                  class="inline-flex items-center px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed">
            <svg *ngIf="isLoading" class="animate-spin -ml-1 mr-2 h-5 w-5 text-gray-700" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <svg *ngIf="!isLoading" class="-ml-1 mr-2 h-5 w-5 text-gray-700" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Refresh
          </button>
          <button (click)="toggleAutoRefresh()" 
                  class="inline-flex items-center px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                  [ngClass]="{'bg-indigo-600 hover:bg-indigo-700': !autoRefreshEnabled, 'bg-red-600 hover:bg-red-700': autoRefreshEnabled}">
            {{ autoRefreshEnabled ? 'Disable Auto-Refresh' : 'Enable Auto-Refresh' }}
          </button>
        </div>
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

      <!-- Loading message -->
      <div *ngIf="isInitialLoading" class="flex justify-center my-12">
        <svg class="animate-spin h-12 w-12 text-indigo-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
      </div>

      <!-- Main status content -->
      <div *ngIf="platformStatus && !isInitialLoading" class="mt-8">
        <!-- Status Overview Card -->
        <div class="bg-white shadow overflow-hidden sm:rounded-lg mb-6">
          <div class="px-4 py-5 sm:px-6 flex justify-between items-center">
            <div>
              <h3 class="text-lg leading-6 font-medium text-gray-900">System Status</h3>
              <p class="mt-1 max-w-2xl text-sm text-gray-500">
                Overall platform health and metrics
              </p>
            </div>
            <div>
              <span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                   [ngClass]="{'bg-green-100 text-green-800': platformStatus.status === 'operational', 
                              'bg-yellow-100 text-yellow-800': platformStatus.status === 'degraded',
                              'bg-red-100 text-red-800': platformStatus.status === 'outage'}">
                {{ platformStatus.status | titlecase }}
              </span>
            </div>
          </div>
          <div class="border-t border-gray-200 px-4 py-5 sm:p-0">
            <dl class="sm:divide-y sm:divide-gray-200">
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                <dt class="text-sm font-medium text-gray-500">Version</dt>
                <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ platformStatus.version }}</dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                <dt class="text-sm font-medium text-gray-500">Uptime</dt>
                <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ formatUptime(platformStatus.uptime) }}</dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                <dt class="text-sm font-medium text-gray-500">Available Runtimes</dt>
                <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">
                  <div class="flex flex-wrap gap-2">
                    <span *ngFor="let runtime of platformStatus.availableRuntimes" 
                          class="inline-flex items-center px-2.5 py-0.5 rounded-md text-sm font-medium bg-indigo-100 text-indigo-800">
                      {{ runtime }}
                    </span>
                  </div>
                </dd>
              </div>
              <div class="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
                <dt class="text-sm font-medium text-gray-500">Last Updated</dt>
                <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ lastUpdated | date:'medium' }}</dd>
              </div>
            </dl>
          </div>
        </div>

        <!-- Stats Overview -->
        <div>
          <h3 class="text-lg leading-6 font-medium text-gray-900 mb-4">Key Metrics</h3>
          <dl class="grid grid-cols-1 gap-5 sm:grid-cols-4">
            <div class="bg-white overflow-hidden shadow rounded-lg">
              <div class="px-4 py-5 sm:p-6">
                <dt class="text-sm font-medium text-gray-500 truncate">Active Functions</dt>
                <dd class="mt-1 text-3xl font-semibold text-gray-900">{{ platformStatus.activeFunctions }}</dd>
              </div>
            </div>

            <div class="bg-white overflow-hidden shadow rounded-lg">
              <div class="px-4 py-5 sm:p-6">
                <dt class="text-sm font-medium text-gray-500 truncate">Active Containers</dt>
                <dd class="mt-1 text-3xl font-semibold text-gray-900">{{ platformStatus.activeContainers }}</dd>
              </div>
            </div>

            <div class="bg-white overflow-hidden shadow rounded-lg">
              <div class="px-4 py-5 sm:p-6">
                <dt class="text-sm font-medium text-gray-500 truncate">Total Invocations</dt>
                <dd class="mt-1 text-3xl font-semibold text-gray-900">{{ platformStatus.totalInvocations }}</dd>
              </div>
            </div>

            <div class="bg-white overflow-hidden shadow rounded-lg">
              <div class="px-4 py-5 sm:p-6">
                <dt class="text-sm font-medium text-gray-500 truncate">Containers per Function</dt>
                <dd class="mt-1 text-3xl font-semibold text-gray-900">
                  {{ platformStatus.activeFunctions > 0 ? (platformStatus.activeContainers / platformStatus.activeFunctions).toFixed(2) : 0 }}
                </dd>
              </div>
            </div>
          </dl>
        </div>

        <!-- Resource Usage -->
        <div class="mt-6 bg-white shadow overflow-hidden sm:rounded-lg">
          <div class="px-4 py-5 sm:px-6">
            <h3 class="text-lg leading-6 font-medium text-gray-900">Resource Usage</h3>
            <p class="mt-1 max-w-2xl text-sm text-gray-500">Current platform resource consumption</p>
          </div>
          <div class="border-t border-gray-200 px-4 py-5 sm:p-6">
            <div class="space-y-6">
              <!-- CPU Usage -->
              <div>
                <div class="flex justify-between">
                  <label class="text-sm font-medium text-gray-700">CPU Usage</label>
                  <span class="text-sm text-gray-700">{{ platformStatus.resourceUsage.cpuUsage.toFixed(2) }}%</span>
                </div>
                <div class="mt-2 w-full bg-gray-200 rounded-full h-2.5">
                  <div class="bg-blue-600 h-2.5 rounded-full" 
                       [style.width.%]="platformStatus.resourceUsage.cpuUsage"></div>
                </div>
              </div>

              <!-- Memory Usage -->
              <div>
                <div class="flex justify-between">
                  <label class="text-sm font-medium text-gray-700">Memory Usage</label>
                  <span class="text-sm text-gray-700">{{ platformStatus.resourceUsage.memoryUsage.toFixed(2) }}%</span>
                </div>
                <div class="mt-2 w-full bg-gray-200 rounded-full h-2.5">
                  <div class="bg-purple-600 h-2.5 rounded-full" 
                       [style.width.%]="platformStatus.resourceUsage.memoryUsage"></div>
                </div>
              </div>

              <!-- Disk Usage -->
              <div>
                <div class="flex justify-between">
                  <label class="text-sm font-medium text-gray-700">Disk Usage</label>
                  <span class="text-sm text-gray-700">{{ platformStatus.resourceUsage.diskUsage.toFixed(2) }}%</span>
                </div>
                <div class="mt-2 w-full bg-gray-200 rounded-full h-2.5">
                  <div class="bg-green-600 h-2.5 rounded-full" 
                       [style.width.%]="platformStatus.resourceUsage.diskUsage"></div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Active Functions -->
        <div *ngIf="platformStatus.activeFunctions > 0" class="mt-6 bg-white shadow overflow-hidden sm:rounded-lg">
          <div class="px-4 py-5 sm:px-6">
            <h3 class="text-lg leading-6 font-medium text-gray-900">Active Functions</h3>
            <p class="mt-1 max-w-2xl text-sm text-gray-500">Currently deployed functions on the platform</p>
          </div>
          <div class="border-t border-gray-200">
            <div class="px-4 py-5 sm:p-6">
              <p class="text-sm text-gray-500">View all active functions in the admin panel.</p>
              <div class="mt-4">
                <a routerLink="/admin/functions" class="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500">
                  View All Functions
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: []
})
export class PlatformStatusComponent implements OnInit {
  platformStatus: PlatformStatus | null | undefined = null;
  isLoading = false;
  isInitialLoading = true;
  errorMessage = '';
  lastUpdated = new Date();
  autoRefreshEnabled = false;
  autoRefreshSubscription: any;

  constructor(private deploymentService: DeploymentService) {}

  ngOnInit(): void {
    this.loadPlatformStatus();
  }

  ngOnDestroy(): void {
    this.stopAutoRefresh();
  }

  loadPlatformStatus() {
    this.isLoading = true;
    this.errorMessage = '';
    
    this.deploymentService.getPlatformStatus()
      .pipe(finalize(() => {
        this.isLoading = false;
        this.isInitialLoading = false;
      }))
      .subscribe({
        next: (response) => {
          if (response.success) {
            this.platformStatus = response.data;
            this.lastUpdated = new Date();
          } else {
            this.errorMessage = response.message || 'Failed to load platform status';
          }
        },
        error: (error) => {
          console.error('Error loading platform status:', error);
          this.errorMessage = error.error?.message || 'An unexpected error occurred';
        }
      });
  }

  refreshStatus() {
    this.loadPlatformStatus();
  }

  toggleAutoRefresh() {
    if (this.autoRefreshEnabled) {
      this.stopAutoRefresh();
    } else {
      this.startAutoRefresh();
    }
    
    this.autoRefreshEnabled = !this.autoRefreshEnabled;
  }

  startAutoRefresh() {
    // Refresh every 30 seconds
    this.autoRefreshSubscription = timer(0, 30000)
      .pipe(
        switchMap(() => this.deploymentService.getPlatformStatus())
      )
      .subscribe({
        next: (response) => {
          if (response.success) {
            this.platformStatus = response.data;
            this.lastUpdated = new Date();
            this.errorMessage = '';
          } else {
            this.errorMessage = response.message || 'Failed to load platform status';
          }
        },
        error: (error) => {
          console.error('Error in auto-refresh:', error);
          this.errorMessage = error.error?.message || 'Auto-refresh error';
        }
      });
  }

  stopAutoRefresh() {
    if (this.autoRefreshSubscription) {
      this.autoRefreshSubscription.unsubscribe();
      this.autoRefreshSubscription = null;
    }
  }

  formatUptime(seconds: number): string {
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);

    let result = '';
    if (days > 0) result += `${days}d `;
    if (hours > 0 || days > 0) result += `${hours}h `;
    if (minutes > 0 || hours > 0 || days > 0) result += `${minutes}m `;
    result += `${secs}s`;

    return result;
  }
}
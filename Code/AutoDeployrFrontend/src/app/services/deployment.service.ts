import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  DeployApplicationRequest,
  DeploymentResponse,
  PlatformStatus,
  DirectFunctionDeployRequest,
  GitHubDeployRequest,
} from '../models/deployment.model';
import { ApiResponse } from '../models/api-response.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class DeploymentService {
  private readonly API_URL = environment.apiUrl;

  constructor(private http: HttpClient) {}

  /**
   * Deploy an application to the serverless platform
   */
  deployApplication(
    request: DeployApplicationRequest
  ): Observable<ApiResponse<DeploymentResponse>> {
    return this.http.post<ApiResponse<DeploymentResponse>>(
      `${this.API_URL}/functions/deploy`,
      request
    );
  }

  /**
   * Combined method to upload and deploy a ZIP file with environment variables
   * @param formData FormData containing file, optional appName, and optional environmentVariables
   */
  uploadAndDeployZip(
    formData: FormData
  ): Observable<ApiResponse<DeploymentResponse>> {
    return this.http.post<ApiResponse<DeploymentResponse>>(
      `${this.API_URL}/functions/deploy-zip`,
      formData
    );
  }

  /**
   * Deploy a function created directly in the frontend
   * @param request Direct function deployment data
   */
  deployDirectFunction(
    request: DirectFunctionDeployRequest
  ): Observable<ApiResponse<DeploymentResponse>> {
    return this.http.post<ApiResponse<DeploymentResponse>>(
      `${this.API_URL}/functions/deploy-direct`,
      request
    );
  }

  /**
   * Deploy an application from a GitHub repository
   * @param request GitHub repository deployment request
   */
  deployFromGitHub(
    request: GitHubDeployRequest
  ): Observable<ApiResponse<DeploymentResponse>> {
    return this.http.post<ApiResponse<DeploymentResponse>>(
      `${this.API_URL}/functions/deploy-github`,
      request
    );
  }

  /**
   * Get platform status (admin only)
   */
  getPlatformStatus(): Observable<ApiResponse<PlatformStatus>> {
    return this.http.get<ApiResponse<PlatformStatus>>(
      `${this.API_URL}/admin/status`
    );
  }

  /**
   * Upload application files
   * @param files Array of files to upload
   * @param appName Optional custom application name
   */
  uploadApplicationFiles(
    files: File[],
    appName?: string
  ): Observable<ApiResponse<{ appPath: string }>> {
    const formData = new FormData();
    for (let i = 0; i < files.length; i++) {
      formData.append('files', files[i], files[i].name);
    }
    if (appName && appName.trim().length > 0) {
      formData.append('appName', appName.trim());
    }
    return this.http.post<ApiResponse<{ appPath: string }>>(
      `${this.API_URL}/functions/upload`,
      formData
    );
  }

  /**
   * Upload a single application file
   * @param file File to upload
   * @param appName Optional custom application name
   */
  uploadApplicationFile(
    file: File,
    appName?: string
  ): Observable<ApiResponse<{ appPath: string }>> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    if (appName && appName.trim().length > 0) {
      formData.append('appName', appName.trim());
    }
    return this.http.post<ApiResponse<{ appPath: string }>>(
      `${this.API_URL}/functions/upload`,
      formData
    );
  }

  /**
   * Analyze application structure before deployment
   * @param appPath Path to application files
   */
  analyzeApplication(appPath: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(
      `${this.API_URL}/functions/analyze`,
      { appPath }
    );
  }
}

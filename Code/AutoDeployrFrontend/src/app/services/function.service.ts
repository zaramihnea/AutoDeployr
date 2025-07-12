import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { 
  FunctionSummary, 
  FunctionMetrics, 
  InvokeFunctionRequest, 
  FunctionResponse 
} from '../models/function.model';
import { ApiResponse } from '../models/api-response.model';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

// Interface for code generation request
export interface CodeGenerationRequest {
  prompt: string;
  language: string;
  targetFramework?: string;
}

// Interface for code generation response
export interface CodeGenerationResponse {
  code: string;
  language: string;
  targetFramework?: string;
  success: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class FunctionService {
  private readonly API_URL = environment.apiUrl;
  private readonly AI_SERVICE_URL = 'http://localhost:5200/api';

  constructor(private http: HttpClient, private authService: AuthService) {}

  /**
   * Generate code using the AI service
   */
  generateCode(request: CodeGenerationRequest): Observable<CodeGenerationResponse> {
    return this.http.post<CodeGenerationResponse>(`${this.AI_SERVICE_URL}/CodeGeneration/generate`, {
      prompt: request.prompt,
      language: request.language,
      targetFramework: request.targetFramework
    });
  }

  /**
   * Get all functions for the current user
   */
  getUserFunctions(): Observable<ApiResponse<FunctionSummary[]>> {
    return this.http.get<ApiResponse<FunctionSummary[]>>(`${this.API_URL}/functions/my-functions`);
  }

  /**
   * Get metrics for a specific function
   */
  getFunctionMetrics(functionId: string): Observable<ApiResponse<FunctionMetrics>> {
    return this.http.get<ApiResponse<FunctionMetrics>>(`${this.API_URL}/functions/metrics/${functionId}`);
  }

  /**
   * Invoke a serverless function with the specified HTTP method
   * The authentication is handled automatically by the auth interceptor via Bearer token
   * @param method HTTP method to use
   * @param appName Application name
   * @param functionName Function name
   * @param payload The request payload (either a direct payload or an InvokeFunctionRequest)
   */
  invokeFunction(
    method: string,
    appName: string, 
    functionName: string, 
    payload: any
  ): Observable<FunctionResponse> {
    // Get the current user to include their username in the URL
    const currentUser = this.authService.getCurrentUser();
    if (!currentUser || !currentUser.username) {
      throw new Error('User must be logged in to invoke functions');
    }

    const url = `${this.API_URL}/${currentUser.username}/functions/${appName}/${functionName}`;
    let httpCall;
    
    // Create request payload (ensure backward compatibility)
    const requestPayload = payload && typeof payload === 'object' && 'payload' in payload 
                          ? payload 
                          : payload;  // Use payload directly if it doesn't have a payload property

    // Build headers starting with Content-Type
    const headers = new HttpHeaders({
      'Content-Type': 'application/json'
    });

    // Add any custom headers from the payload (including X-Function-Key for private functions)
    let finalHeaders = headers;
    if (requestPayload && requestPayload.headers) {
      Object.keys(requestPayload.headers).forEach(key => {
        if (requestPayload.headers[key]) {
          finalHeaders = finalHeaders.set(key, requestPayload.headers[key]);
        }
      });
    }

    const httpOptions = {
      headers: finalHeaders
    };

    // Extract the body from the payload (if it exists)
    const requestBody = requestPayload?.body || {};
    
    // For GET and DELETE requests, convert body to query parameters
    // For other methods, use the body as the request body
    switch (method.toUpperCase()) {
      case 'GET': {
        // Combine path query params with body params for GET requests
        let allParams: any = {};
        
        // Add path from payload if it exists and contains query parameters
        if (requestPayload?.path && requestPayload.path.includes('?')) {
          const pathParams = new URLSearchParams(requestPayload.path.split('?')[1]);
          pathParams.forEach((value, key) => {
            allParams[key] = value;
          });
        }
        
        // Add body parameters
        if (requestBody && typeof requestBody === 'object') {
          allParams = { ...allParams, ...requestBody };
        }
        
        const params = Object.keys(allParams).length > 0 ? new HttpParams({ fromObject: allParams }) : undefined;
        httpCall = this.http.get<any>(url, { ...httpOptions, params });
        break;
      }
      case 'POST': {
        // POST requests support query parameters only (e.g. ?id=1)
        let finalUrl = url;
        if (requestPayload?.path && requestPayload.path.trim() && requestPayload.path.startsWith('?')) {
          finalUrl = url + requestPayload.path;
        }
        httpCall = this.http.post<any>(finalUrl, requestBody, httpOptions);
        break;
      }
      case 'PUT': {
        // Extract query parameters from path if they exist
        let finalUrl = url;
        if (requestPayload?.path && requestPayload.path.trim()) {
          // If path starts with ?, it's just query params, append to base URL
          if (requestPayload.path.startsWith('?')) {
            finalUrl = url + requestPayload.path;
          } else {
            // If path doesn't start with ?, it might be a path segment + query params
            finalUrl = url + (requestPayload.path.startsWith('/') ? '' : '/') + requestPayload.path;
          }
        }
        httpCall = this.http.put<any>(finalUrl, requestBody, httpOptions);
        break;
      }
      case 'DELETE': {
        // For DELETE, we can include query parameters but typically no body
        let allParams: any = {};
        
        if (requestPayload?.path && requestPayload.path.includes('?')) {
          const pathParams = new URLSearchParams(requestPayload.path.split('?')[1]);
          pathParams.forEach((value, key) => {
            allParams[key] = value;
          });
        }
        
        if (requestBody && typeof requestBody === 'object') {
          allParams = { ...allParams, ...requestBody };
        }
        
        const params = Object.keys(allParams).length > 0 ? new HttpParams({ fromObject: allParams }) : undefined;
        httpCall = this.http.delete<any>(url, { ...httpOptions, params });
        break;
      }
      case 'PATCH': {
        // Extract query parameters from path if they exist (same logic as PUT)
        let finalUrl = url;
        if (requestPayload?.path && requestPayload.path.trim()) {
          // If path starts with ?, it's just query params, append to base URL
          if (requestPayload.path.startsWith('?')) {
            finalUrl = url + requestPayload.path;
          } else {
            // If path doesn't start with ?, it might be a path segment + query params
            finalUrl = url + (requestPayload.path.startsWith('/') ? '' : '/') + requestPayload.path;
          }
        }
        httpCall = this.http.patch<any>(finalUrl, requestBody, httpOptions);
        break;
      }
      default:
        throw new Error(`Unsupported HTTP method: ${method}`);
    }

    return httpCall.pipe(
      map(response => {
        console.log('Raw HTTP response:', response);
        
        // Check if response contains FINAL_RESULT prefix (indicating backend processing error)
        if (typeof response === 'string' && response.includes('FINAL_RESULT:')) {
          console.error('Backend returned raw container logs:', response);
          throw new Error('Backend processing error: received raw container output instead of JSON');
        }
        
        // If response is not already in the expected format, transform it
        if (!response.hasOwnProperty('statusCode') && !response.hasOwnProperty('body')) {
          return {
            statusCode: 200, // Assume success if we got a response
            headers: {}, // No headers in direct response
            body: response // The entire response is the body
          };
        }
        return response;
      })
    );
  }

  /**
   * Undeploy a function
   * Authentication is handled automatically by the auth interceptor
   */
  undeployFunction(appName: string, functionName: string): Observable<ApiResponse<any>> {
    return this.http.delete<ApiResponse<any>>(
      `${this.API_URL}/functions/undeploy/${appName}/${functionName}`
    );
  }

  /**
   * Get all functions (admin only)
   */
  getAllFunctions(): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(`${this.API_URL}/admin/functions`);
  }

  /**
   * Toggle function security between private and public
   */
  toggleFunctionSecurity(functionId: string, isPrivate: boolean): Observable<ApiResponse<any>> {
    return this.http.put<ApiResponse<any>>(
      `${this.API_URL}/functions/${functionId}/security`,
      { isPrivate }
    );
  }
}
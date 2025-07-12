export interface ApiResponse<T> {
    success: boolean;
    message: string;
    data?: T;
    timestamp?: string;
    error?: string;
  }
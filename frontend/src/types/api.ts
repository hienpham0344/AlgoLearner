/**
 * Standard API response envelope matching Spring Boot backend (ApiResponse<T>)
 */
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string; // ISO-8601 UTC string (e.g. 2026-10-25T14:30:00Z)
}

/**
 * Standard pagination metadata and list response wrapper
 */
export interface PaginatedData<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
}

export type PageResponse<T> = ApiResponse<PaginatedData<T>>;

/**
 * Field-level validation error
 */
export interface ValidationError {
  field: string;
  rejectedValue?: unknown;
  message: string;
}

/**
 * Standard error response envelope matching ApiErrorResponse
 */
export interface ApiErrorResponse {
  success: false;
  errorCode: string;
  message: string;
  errors?: ValidationError[];
  path: string;
  timestamp: string;
}

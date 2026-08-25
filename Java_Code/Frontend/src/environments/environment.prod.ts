/**
 * Production configuration. The API is served from the same origin as the built frontend, so a
 * relative base URL keeps the bundle deployment independent.
 */
export const environment = {
  production: true,
  /** Actuator health probe, used by the sidebar status indicator. Unauthenticated. */
  healthUrl: '/actuator/health',
  apiBaseUrl: '/api',
};

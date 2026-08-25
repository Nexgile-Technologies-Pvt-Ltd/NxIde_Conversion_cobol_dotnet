/** Development configuration. The Spring Boot backend listens on port 8080 by default. */
export const environment = {
  production: false,
  /** Actuator health probe, used by the sidebar status indicator. Unauthenticated. */
  healthUrl: 'http://localhost:8080/actuator/health',
  apiBaseUrl: 'http://localhost:8080/api',
};

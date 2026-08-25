import { Environment } from './environment.model';

/**
 * Production configuration. The API is served from the same origin as the built frontend, so a
 * relative base URL keeps the bundle deployment independent.
 */
export const environment: Environment = {
  production: true,
  apiBaseUrl: '/api',
  healthUrl: '/actuator/health',

  // Kept so a demonstration deployment behaves like the development one. Set to null before this
  // carries anything real: whatever is here is readable by anyone who loads the page.
  demoCredentials: { userId: 'ADMIN001', password: 'PASSWORD1' },
};

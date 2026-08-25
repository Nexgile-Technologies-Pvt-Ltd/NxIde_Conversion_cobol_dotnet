import { Environment } from './environment.model';

/** Development configuration. The Spring Boot backend listens on port 8080 by default. */
export const environment: Environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api',
  healthUrl: 'http://localhost:8080/actuator/health',

  // Fixture administrator from AWS.M2.CARDDEMO.USRSEC.PS. Set to null once this carries real data.
  demoCredentials: { userId: 'ADMIN001', password: 'PASSWORD1' },
};

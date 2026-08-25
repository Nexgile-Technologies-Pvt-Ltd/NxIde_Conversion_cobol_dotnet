/**
 * Shape shared by every environment file, so the development and production builds cannot drift
 * apart on the fields the application reads.
 */
export interface Environment {
  production: boolean;

  /** Base URL of the Spring Boot API. */
  apiBaseUrl: string;

  /** Actuator health probe, used by the sidebar status indicator. Unauthenticated. */
  healthUrl: string;

  /**
   * Credentials pre-filled on the sign-on screen so the demonstration data can be reached without
   * typing them each time.
   *
   * <p>Set this to {@code null} before the application carries anything real. Whatever is here ships
   * inside the browser bundle and is readable by anyone who loads the page, so it is only ever
   * appropriate for the shipped fixture accounts.</p>
   */
  demoCredentials: { userId: string; password: string } | null;
}

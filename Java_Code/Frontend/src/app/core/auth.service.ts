import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { environment } from '../../environments/environment';
import { AuthConfig, LoginResponse, UserProfile } from './models';

const TOKEN_KEY = 'carddemo.token';
const USER_KEY = 'carddemo.user';

/**
 * Session state for the whole application.
 *
 * The COBOL design carried user id and role in the mutable 160-byte COMMAREA. Here the identity
 * lives in a signed token issued by the backend; the client keeps a cached copy purely to render
 * the shell, and every protected call is re-authorised server side.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly tokenSignal = signal<string | null>(readStorage(TOKEN_KEY));
  private readonly userSignal = signal<UserProfile | null>(readUser());

  readonly user = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null);
  readonly isAdmin = computed(() => this.userSignal()?.admin === true);
  readonly displayName = computed(() => {
    const user = this.userSignal();
    if (!user) {
      return '';
    }
    const full = `${user.firstName ?? ''} ${user.lastName ?? ''}`.trim();
    return full.length > 0 ? full : user.userId;
  });

  get token(): string | null {
    return this.tokenSignal();
  }

  config(): Observable<AuthConfig> {
    return this.http.get<AuthConfig>(`${environment.apiBaseUrl}/auth/config`);
  }

  /** Sign on. COBOL program COSGN00C, transaction CC00. */
  login(userId: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiBaseUrl}/auth/login`, { userId, password })
      .pipe(tap((response) => this.accept(response)));
  }

  /** Self-service registration; the backend always assigns the regular user role. */
  signup(payload: {
    userId: string;
    firstName: string;
    lastName: string;
    password: string;
    confirmPassword: string;
  }): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiBaseUrl}/auth/signup`, payload)
      .pipe(tap((response) => this.accept(response)));
  }

  changePassword(payload: {
    currentPassword: string;
    newPassword: string;
    confirmPassword: string;
  }): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `${environment.apiBaseUrl}/auth/change-password`,
      payload,
    );
  }

  /** Refreshes the cached profile from the server, which is the authority on the role. */
  refreshProfile(): Observable<UserProfile> {
    return this.http
      .get<UserProfile>(`${environment.apiBaseUrl}/auth/me`)
      .pipe(tap((user) => this.setUser(user)));
  }

  /** Sign off. Records the audit event, then clears local state and returns to the sign-on screen. */
  logout(navigate = true): void {
    if (this.tokenSignal()) {
      this.http.post(`${environment.apiBaseUrl}/auth/logout`, {}).subscribe({
        next: () => undefined,
        error: () => undefined,
      });
    }
    this.clear();
    if (navigate) {
      void this.router.navigate(['/login']);
    }
  }

  /** Clears the session without calling the backend; used when a token is rejected. */
  clear(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    writeStorage(TOKEN_KEY, null);
    writeStorage(USER_KEY, null);
  }

  private accept(response: LoginResponse): void {
    this.tokenSignal.set(response.token);
    writeStorage(TOKEN_KEY, response.token);
    this.setUser(response.user);
  }

  private setUser(user: UserProfile): void {
    this.userSignal.set(user);
    writeStorage(USER_KEY, JSON.stringify(user));
  }
}

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string | null): void {
  try {
    if (value === null) {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, value);
    }
  } catch {
    /* storage can be unavailable in a private window; the session simply becomes memory only */
  }
}

function readUser(): UserProfile | null {
  const raw = readStorage(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as UserProfile;
  } catch {
    return null;
  }
}

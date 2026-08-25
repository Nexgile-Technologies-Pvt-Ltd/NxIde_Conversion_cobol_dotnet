import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { environment } from '../../../environments/environment';
import { errorField, errorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { IconComponent } from '../../shared/icon';
import { MessageLineComponent } from '../../shared/message-line';
import { AuthBrandPanelComponent } from './auth-brand-panel';

/**
 * Sign-on screen. COBOL program {@code COSGN00C}, map {@code COSGN00 / COSGN0A}, transaction
 * {@code CC00}.
 *
 * <p>The eight-character user id and masked password contract is preserved, as is the routing
 * decision: an {@code A} user lands on the administrator menu and everyone else on the main
 * menu. Unlike the source, the failure message does not reveal whether the user id or the
 * password was wrong.</p>
 */
@Component({
  selector: 'cd-login',
  imports: [FormsModule, RouterLink, IconComponent, MessageLineComponent, AuthBrandPanelComponent],
  template: `
    <div class="cd-auth">
      <cd-auth-brand-panel />

      <section class="cd-auth__panel">
        <form class="cd-auth__card" (ngSubmit)="submit()">
          <span class="cd-auth__crest"><cd-icon name="emblem" [size]="30" /></span>

          <h2>Welcome Back!</h2>
          <p class="cd-auth__lead">Sign on to continue servicing accounts</p>
          <div class="cd-auth__rule"><span>SECURE SIGN-ON</span></div>

          <cd-message [text]="message()" [kind]="messageKind()" />

          <label class="cd-inputgroup" [class.is-invalid]="field() === 'userId'">
            <span class="cd-inputgroup__icon"><cd-icon name="user" /></span>
            <input
              name="userId"
              maxlength="8"
              [attr.autocomplete]="demoFilled ? 'off' : 'username'"
              placeholder="User ID"
              aria-label="User ID"
              [(ngModel)]="userId"
            />
          </label>

          <label class="cd-inputgroup" [class.is-invalid]="field() === 'password'">
            <span class="cd-inputgroup__icon"><cd-icon name="lock" /></span>
            <input
              name="password"
              [type]="revealed() ? 'text' : 'password'"
              [attr.autocomplete]="demoFilled ? 'off' : 'current-password'"
              placeholder="Password"
              aria-label="Password"
              [(ngModel)]="password"
            />
            <button
              type="button"
              class="cd-inputgroup__reveal"
              [attr.aria-label]="revealed() ? 'Hide password' : 'Show password'"
              (click)="revealed.set(!revealed())"
            >
              <cd-icon [name]="revealed() ? 'eyeOff' : 'eye'" />
            </button>
          </label>

          <button type="submit" class="cd-auth__submit" [disabled]="busy()">
            @if (busy()) {
              <span class="cd-spinner cd-spinner--light"></span>
            } @else {
              <span>Sign In</span>
              <cd-icon name="arrowRight" [size]="19" />
            }
          </button>

          @if (signupEnabled()) {
            <p class="cd-auth__foot">
              Don't have an account? <a routerLink="/signup">Create Account</a>
            </p>
          }
        </form>
      </section>
    </div>
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /**
   * Pre-filled from the configured demonstration credentials so the fixture data is reachable
   * without typing them each time. Blank whenever none are configured.
   */
  userId = environment.demoCredentials?.userId ?? '';
  password = environment.demoCredentials?.password ?? '';

  /**
   * Whether the fields arrived pre-filled. Browser autofill is suppressed while they are, so a
   * saved credential cannot overwrite them.
   */
  readonly demoFilled = environment.demoCredentials !== null;

  readonly busy = signal(false);
  readonly revealed = signal(false);
  readonly message = signal<string | null>(null);
  readonly messageKind = signal<'error' | 'info' | 'ok'>('error');
  readonly field = signal<string | null>(null);
  readonly signupEnabled = signal(true);

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    if (params.get('expired')) {
      this.messageKind.set('info');
      this.message.set('Your session has ended. Please sign on again ...');
    } else if (params.get('signedOff')) {
      this.messageKind.set('ok');
      this.message.set('Thank you for using CardDemo application...');
    }

    this.auth.config().subscribe({
      next: (config) => this.signupEnabled.set(config.signupEnabled),
      error: () => this.signupEnabled.set(false),
    });
  }

  submit(): void {
    // Source validation order: user id first, then password; first error only.
    this.field.set(null);
    if (!this.userId.trim()) {
      this.fail('Please enter User ID ...', 'userId');
      return;
    }
    if (!this.password) {
      this.fail('Please enter Password ...', 'password');
      return;
    }

    this.busy.set(true);
    this.message.set(null);
    this.auth.login(this.userId.trim(), this.password).subscribe({
      next: () => {
        this.busy.set(false);
        // Sign-on lands on the dashboard; a guard-supplied returnUrl still wins so a deep link
        // survives the detour through this screen.
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        void this.router.navigateByUrl(returnUrl ?? '/dashboard');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.password = '';
        this.fail(errorMessage(error, 'Unable to verify the User ...'), errorField(error));
      },
    });
  }

  private fail(text: string, field: string | null): void {
    this.messageKind.set('error');
    this.message.set(text);
    this.field.set(field);
  }
}

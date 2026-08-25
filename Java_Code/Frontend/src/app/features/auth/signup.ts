import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { errorField, errorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { IconComponent } from '../../shared/icon';
import { MessageLineComponent } from '../../shared/message-line';
import { AuthBrandPanelComponent } from './auth-brand-panel';

/**
 * Self-service registration.
 *
 * <p>The legacy application had no such screen: accounts were created only by an administrator
 * through {@code COUSR01C}. This screen therefore always creates a regular {@code U} user; the
 * administrator role can only be granted from the user administration screens.</p>
 */
@Component({
  selector: 'cd-signup',
  imports: [FormsModule, RouterLink, IconComponent, MessageLineComponent, AuthBrandPanelComponent],
  template: `
    <div class="cd-auth">
      <cd-auth-brand-panel />

      <section class="cd-auth__panel">
        <form class="cd-auth__card" (ngSubmit)="submit()">
          <span class="cd-auth__crest"><cd-icon name="emblem" [size]="30" /></span>

          <h2>Create Account</h2>
          <p class="cd-auth__lead">New accounts receive the regular user role</p>
          <div class="cd-auth__rule"><span>ROLE U &middot; SELF SERVICE</span></div>

          <cd-message [text]="message()" kind="error" />

          <label class="cd-inputgroup" [class.is-invalid]="field() === 'userId'">
            <span class="cd-inputgroup__icon"><cd-icon name="user" /></span>
            <input
              name="userId"
              maxlength="8"
              placeholder="User ID (up to 8 characters)"
              aria-label="User ID"
              [(ngModel)]="userId"
            />
          </label>

          <div class="cd-auth__row">
            <label class="cd-inputgroup" [class.is-invalid]="field() === 'firstName'">
              <input
                name="firstName"
                maxlength="20"
                placeholder="First name"
                aria-label="First name"
                [(ngModel)]="firstName"
              />
            </label>
            <label class="cd-inputgroup" [class.is-invalid]="field() === 'lastName'">
              <input
                name="lastName"
                maxlength="20"
                placeholder="Last name"
                aria-label="Last name"
                [(ngModel)]="lastName"
              />
            </label>
          </div>

          <label class="cd-inputgroup" [class.is-invalid]="field() === 'password'">
            <span class="cd-inputgroup__icon"><cd-icon name="lock" /></span>
            <input
              name="password"
              [type]="revealed() ? 'text' : 'password'"
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

          <label class="cd-inputgroup" [class.is-invalid]="field() === 'confirmPassword'">
            <span class="cd-inputgroup__icon"><cd-icon name="lock" /></span>
            <input
              name="confirmPassword"
              type="password"
              placeholder="Confirm password"
              aria-label="Confirm password"
              [(ngModel)]="confirmPassword"
            />
          </label>

          <p class="cd-auth__hint">
            At least {{ minLength() }} characters, with letters and numbers. Stored only as a
            salted hash.
          </p>

          <button type="submit" class="cd-auth__submit" [disabled]="busy()">
            @if (busy()) {
              <span class="cd-spinner cd-spinner--light"></span>
            } @else {
              <span>Create Account</span>
              <cd-icon name="arrowRight" [size]="19" />
            }
          </button>

          <p class="cd-auth__foot">Already registered? <a routerLink="/login">Sign on</a></p>
        </form>
      </section>
    </div>
  `,
})
export class SignupComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  userId = '';
  firstName = '';
  lastName = '';
  password = '';
  confirmPassword = '';

  readonly busy = signal(false);
  readonly revealed = signal(false);
  readonly message = signal<string | null>(null);
  readonly field = signal<string | null>(null);
  readonly minLength = signal(8);

  constructor() {
    this.auth.config().subscribe({
      next: (config) => this.minLength.set(config.minPasswordLength),
      error: () => undefined,
    });
  }

  submit(): void {
    this.field.set(null);
    this.message.set(null);

    if (!this.userId.trim()) {
      this.fail('User ID can NOT be empty...', 'userId');
      return;
    }
    if (!this.firstName.trim()) {
      this.fail('First Name can NOT be empty...', 'firstName');
      return;
    }
    if (!this.lastName.trim()) {
      this.fail('Last Name can NOT be empty...', 'lastName');
      return;
    }
    if (!this.password) {
      this.fail('Password can NOT be empty...', 'password');
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.fail('Passwords do not match ...', 'confirmPassword');
      return;
    }

    this.busy.set(true);
    this.auth
      .signup({
        userId: this.userId.trim(),
        firstName: this.firstName.trim(),
        lastName: this.lastName.trim(),
        password: this.password,
        confirmPassword: this.confirmPassword,
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          void this.router.navigateByUrl('/dashboard');
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.fail(errorMessage(error), errorField(error));
        },
      });
  }

  private fail(text: string, field: string | null): void {
    this.message.set(text);
    this.field.set(field);
  }
}

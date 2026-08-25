import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { errorField, errorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { MessageLineComponent } from '../../shared/message-line';

/**
 * Self-service registration.
 *
 * <p>The legacy application had no such screen: accounts were created only by an administrator
 * through {@code COUSR01C}. This screen therefore always creates a regular {@code U} user; the
 * administrator role can only be granted from the user administration screens.</p>
 */
@Component({
  selector: 'cd-signup',
  imports: [FormsModule, RouterLink, MessageLineComponent],
  template: `
    <div class="cd-auth">
      <div class="cd-auth__card">
        <div class="cd-auth__head">
          <div class="cd-auth__terminal">Create account &middot; Regular user</div>
          <h1>Sign up</h1>
          <p>New accounts receive the regular user role.</p>
        </div>

        <form class="cd-auth__body" (ngSubmit)="submit()">
          <cd-message [text]="message()" kind="error" />

          <div class="cd-field">
            <label for="userId">User ID</label>
            <input
              id="userId"
              name="userId"
              class="cd-mono"
              maxlength="8"
              [class.cd-invalid]="field() === 'userId'"
              [(ngModel)]="userId"
            />
            <span class="cd-field__hint">Up to 8 letters or digits</span>
          </div>

          <div class="cd-grid cd-grid--tight">
            <div class="cd-field">
              <label for="firstName">First name</label>
              <input
                id="firstName"
                name="firstName"
                maxlength="20"
                [class.cd-invalid]="field() === 'firstName'"
                [(ngModel)]="firstName"
              />
            </div>
            <div class="cd-field">
              <label for="lastName">Last name</label>
              <input
                id="lastName"
                name="lastName"
                maxlength="20"
                [class.cd-invalid]="field() === 'lastName'"
                [(ngModel)]="lastName"
              />
            </div>
          </div>

          <div class="cd-field">
            <label for="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              [class.cd-invalid]="field() === 'password'"
              [(ngModel)]="password"
            />
            <span class="cd-field__hint">
              At least {{ minLength() }} characters, with letters and numbers
            </span>
          </div>

          <div class="cd-field">
            <label for="confirmPassword">Confirm password</label>
            <input
              id="confirmPassword"
              name="confirmPassword"
              type="password"
              [class.cd-invalid]="field() === 'confirmPassword'"
              [(ngModel)]="confirmPassword"
            />
          </div>

          <div class="cd-actions">
            <button type="submit" class="cd-primary" [disabled]="busy()">
              @if (busy()) {
                <span class="cd-spinner"></span>
              } @else {
                Create account
              }
            </button>
          </div>
        </form>

        <div class="cd-auth__foot">
          Already registered? <a routerLink="/login">Sign on</a>
        </div>
      </div>
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
        next: (response) => {
          this.busy.set(false);
          void this.router.navigateByUrl(response.user.landingScreen);
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

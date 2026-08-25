import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { errorField, errorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { MessageLineComponent } from '../../shared/message-line';

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
  imports: [FormsModule, RouterLink, MessageLineComponent],
  template: `
    <div class="cd-auth">
      <div class="cd-auth__card">
        <div class="cd-auth__head">
          <div class="cd-auth__terminal">Tran CC00 &middot; COSGN00C &middot; Sign on</div>
          <h1>CardDemo</h1>
          <p>Credit card servicing system</p>
        </div>

        <form class="cd-auth__body" (ngSubmit)="submit()">
          <cd-message [text]="message()" [kind]="messageKind()" />

          <div class="cd-field">
            <label for="userId">User ID</label>
            <input
              id="userId"
              name="userId"
              class="cd-mono"
              maxlength="8"
              autocomplete="username"
              autofocus
              [class.cd-invalid]="field() === 'userId'"
              [(ngModel)]="userId"
            />
            <span class="cd-field__hint">Up to 8 characters</span>
          </div>

          <div class="cd-field">
            <label for="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              class="cd-mono"
              autocomplete="current-password"
              [class.cd-invalid]="field() === 'password'"
              [(ngModel)]="password"
            />
          </div>

          <div class="cd-actions">
            <button type="submit" class="cd-primary" [disabled]="busy()">
              @if (busy()) {
                <span class="cd-spinner"></span>
              } @else {
                Sign on
              }
            </button>
          </div>
        </form>

        @if (signupEnabled()) {
          <div class="cd-auth__foot">
            No account yet? <a routerLink="/signup">Create one</a>
          </div>
        }
      </div>
    </div>
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  userId = '';
  password = '';

  readonly busy = signal(false);
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
      next: (response) => {
        this.busy.set(false);
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        void this.router.navigateByUrl(returnUrl ?? response.user.landingScreen);
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

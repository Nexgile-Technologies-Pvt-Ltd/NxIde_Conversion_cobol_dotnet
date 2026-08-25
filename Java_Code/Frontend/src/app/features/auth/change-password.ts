import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { errorField, errorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Password change for the signed-on user.
 *
 * <p>The legacy update screen loaded the stored password back into a masked field. That is a
 * credential-exposure defect, so the target never returns a stored credential: the user supplies
 * the current password and a new one.</p>
 */
@Component({
  selector: 'cd-change-password',
  imports: [FormsModule, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Change password"
      subtitle="Credentials are stored as salted hashes; no password is ever displayed."
    />

    <div class="cd-panel">
      <div class="cd-panel__body">
        <cd-message [text]="message()" [kind]="kind()" />

        <form (ngSubmit)="submit()">
          <div class="cd-field" style="margin-bottom: 14px">
            <label for="current">Current password</label>
            <input
              id="current"
              name="current"
              type="password"
              [class.cd-invalid]="field() === 'currentPassword'"
              [(ngModel)]="currentPassword"
            />
          </div>

          <div class="cd-field" style="margin-bottom: 14px">
            <label for="next">New password</label>
            <input
              id="next"
              name="next"
              type="password"
              [class.cd-invalid]="field() === 'password' || field() === 'newPassword'"
              [(ngModel)]="newPassword"
            />
          </div>

          <div class="cd-field" style="margin-bottom: 18px">
            <label for="confirm">Confirm new password</label>
            <input
              id="confirm"
              name="confirm"
              type="password"
              [class.cd-invalid]="field() === 'confirmPassword'"
              [(ngModel)]="confirmPassword"
            />
          </div>

          <div class="cd-actions">
            <button type="submit" class="cd-primary" [disabled]="busy()">Change password</button>
          </div>
        </form>
      </div>
    </div>
  `,
})
export class ChangePasswordComponent {
  private readonly auth = inject(AuthService);

  currentPassword = '';
  newPassword = '';
  confirmPassword = '';

  readonly busy = signal(false);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'ok'>('error');
  readonly field = signal<string | null>(null);

  submit(): void {
    this.field.set(null);
    this.message.set(null);

    if (!this.currentPassword) {
      this.fail('Current password can NOT be empty...', 'currentPassword');
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.fail('Passwords do not match ...', 'confirmPassword');
      return;
    }

    this.busy.set(true);
    this.auth
      .changePassword({
        currentPassword: this.currentPassword,
        newPassword: this.newPassword,
        confirmPassword: this.confirmPassword,
      })
      .subscribe({
        next: (result) => {
          this.busy.set(false);
          this.kind.set('ok');
          this.message.set(result.message);
          this.currentPassword = '';
          this.newPassword = '';
          this.confirmPassword = '';
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.fail(errorMessage(error), errorField(error));
        },
      });
  }

  private fail(text: string, field: string | null): void {
    this.kind.set('error');
    this.message.set(text);
    this.field.set(field);
  }
}

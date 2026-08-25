import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { UserRole } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Add a security user. COBOL program {@code COUSR01C}, map {@code COUSR01 / COUSR1A}, transaction
 * {@code CU01}.
 *
 * <p>Validation order is the source one: first name, last name, user id, password, user type. The
 * safe target additionally restricts the type to {@code A} or {@code U} and applies the configured
 * password policy before hashing (FR-USER-003).</p>
 */
@Component({
  selector: 'cd-user-add',
  imports: [FormsModule, RouterLink, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Add security user"
      subtitle="Validation order: first name, last name, user ID, password, user type"
    />

    <cd-message [text]="message()" [kind]="kind()" />

    <form (ngSubmit)="submit()">
      <div class="cd-panel" style="max-width: 720px">
        <div class="cd-panel__body">
          <div class="cd-grid">
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
              <span class="cd-field__hint">Up to 8 letters or digits, stored uppercase</span>
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
              <span class="cd-field__hint">Stored only as a salted hash</span>
            </div>
            <div class="cd-field">
              <label for="userType">User type</label>
              <select
                id="userType"
                name="userType"
                [class.cd-invalid]="field() === 'userType'"
                [(ngModel)]="userType"
              >
                <option value="U">U &ndash; Regular user</option>
                <option value="A">A &ndash; Administrator</option>
              </select>
            </div>
          </div>
        </div>

        <div class="cd-pfkeys">
          <button type="submit" class="cd-primary" [disabled]="busy()">
            <span class="cd-pfkey__label">Enter</span>Add user
          </button>
          <button type="button" (click)="clear()">
            <span class="cd-pfkey__label">F4</span>Clear
          </button>
          <a class="cd-btn" routerLink="/admin/users">
            <span class="cd-pfkey__label">F3</span>Return
          </a>
        </div>
      </div>
    </form>
  `,
})
export class UserAddComponent {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  firstName = '';
  lastName = '';
  userId = '';
  password = '';
  userType: UserRole = 'U';

  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'ok'>('error');
  readonly field = signal<string | null>(null);
  readonly busy = signal(false);

  submit(): void {
    this.busy.set(true);
    this.message.set(null);
    this.field.set(null);
    this.api
      .createUser({
        firstName: this.firstName,
        lastName: this.lastName,
        userId: this.userId,
        password: this.password,
        userType: this.userType,
      })
      .subscribe({
        next: (created) => {
          this.busy.set(false);
          this.kind.set('ok');
          this.message.set(`User ${created.userId} has been added ...`);
          this.clear();
          void this.router.navigate(['/admin/users']);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.kind.set('error');
          this.message.set(errorMessage(error));
          this.field.set(errorField(error));
        },
      });
  }

  clear(): void {
    this.firstName = '';
    this.lastName = '';
    this.userId = '';
    this.password = '';
    this.userType = 'U';
    this.field.set(null);
  }
}

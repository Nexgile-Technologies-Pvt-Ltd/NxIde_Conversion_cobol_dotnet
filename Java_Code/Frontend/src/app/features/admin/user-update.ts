import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { UserDetail, UserRole } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Update a security user. COBOL program {@code COUSR02C}, map {@code COUSR02 / COUSR2A},
 * transaction {@code CU02}.
 *
 * <p>FR-USER-005: save and return are separate actions here. The legacy PF3 ran the save and then
 * navigated away even when the save had failed, which could hide the error.</p>
 *
 * <p>A blank password leaves the stored credential unchanged: the source loaded the stored
 * password back into a masked field, which the safe target never does.</p>
 */
@Component({
  selector: 'cd-user-update',
  imports: [DatePipe, FormsModule, RouterLink, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Update security user"
      subtitle="Save and Return are separate actions; a blank password keeps the current one"
    />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Search</h2>
        <form class="cd-actions" (ngSubmit)="fetch()">
          <input
            name="userId"
            class="cd-mono"
            style="width: 160px"
            maxlength="8"
            placeholder="User ID"
            [class.cd-invalid]="field() === 'userId'"
            [(ngModel)]="userId"
            aria-label="User ID"
          />
          <button type="submit" class="cd-primary">Fetch</button>
        </form>
      </div>
    </div>

    <cd-message [text]="message()" [kind]="kind()" />

    @if (detail(); as user) {
      <form (ngSubmit)="save()">
        <div class="cd-panel">
          <div class="cd-panel__head">
            <h2>User {{ user.userId }}</h2>
            <span class="cd-inline-note">
              Last sign-on:
              {{ user.lastLoginAt ? (user.lastLoginAt | date: 'yyyy-MM-dd HH:mm') : 'never' }}
            </span>
          </div>

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
                <label for="password">New password</label>
                <input
                  id="password"
                  name="password"
                  type="password"
                  placeholder="Leave blank to keep the current password"
                  [class.cd-invalid]="field() === 'password'"
                  [(ngModel)]="password"
                />
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

            <label class="cd-checkbox" style="margin-top: 18px">
              <input type="checkbox" name="active" [(ngModel)]="active" />
              Account is enabled
            </label>
          </div>

          <div class="cd-pfkeys">
            <button type="submit" class="cd-primary" [disabled]="busy()">
              Save
            </button>
            <button type="button" (click)="cancel()">
              Cancel
            </button>
            <a class="cd-btn" routerLink="/admin/users">
              Return without saving
            </a>
          </div>
        </div>
      </form>
    }
  `,
})
export class UserUpdateComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  userId = '';
  firstName = '';
  lastName = '';
  password = '';
  userType: UserRole = 'U';
  active = true;

  readonly detail = signal<UserDetail | null>(null);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'ok' | 'info'>('error');
  readonly field = signal<string | null>(null);
  readonly busy = signal(false);

  constructor() {
    const fromRoute = this.route.snapshot.paramMap.get('userId');
    if (fromRoute) {
      this.userId = fromRoute;
      this.fetch();
    }
  }

  fetch(): void {
    this.message.set(null);
    this.field.set(null);
    const id = this.userId.trim();
    if (!id) {
      this.field.set('userId');
      this.kind.set('error');
      this.message.set('User ID can NOT be empty...');
      return;
    }
    this.api.user(id).subscribe({
      next: (user) => this.accept(user),
      error: (error: unknown) => {
        this.detail.set(null);
        this.kind.set('error');
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  save(): void {
    const user = this.detail();
    if (!user) {
      return;
    }
    this.busy.set(true);
    this.message.set(null);
    this.field.set(null);
    this.api
      .updateUser(user.userId, {
        firstName: this.firstName,
        lastName: this.lastName,
        password: this.password,
        userType: this.userType,
        active: this.active,
        version: user.version,
      })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.accept(updated);
          this.kind.set('ok');
          this.message.set(`User ${updated.userId} has been updated ...`);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.kind.set('error');
          this.message.set(errorMessage(error));
          this.field.set(errorField(error));
        },
      });
  }

  cancel(): void {
    const user = this.detail();
    if (user) {
      this.accept(user);
      this.kind.set('info');
      this.message.set('Changes discarded; the fetched values have been restored.');
    }
  }

  private accept(user: UserDetail): void {
    this.detail.set(user);
    this.userId = user.userId;
    this.firstName = user.firstName;
    this.lastName = user.lastName;
    this.userType = user.userType;
    this.active = user.active;
    this.password = '';
  }
}

import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { UserDetail } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Delete a security user. COBOL program {@code COUSR03C}, map {@code COUSR03 / COUSR3A},
 * transaction {@code CU03}.
 *
 * <p>FR-USER-006: the legacy screen deleted the locked record with no confirmation, no self-delete
 * check and no final-administrator guard. All three protections are enforced here and again on the
 * server.</p>
 */
@Component({
  selector: 'cd-user-delete',
  imports: [FormsModule, RouterLink, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Delete security user"
      subtitle="Confirmation required; the acting user and the last administrator are protected"
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
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>User {{ user.userId }}</h2>
        </div>
        <div class="cd-panel__body">
          <div class="cd-grid">
            <div class="cd-field">
              <label>First name</label>
              <div class="cd-value">{{ user.firstName }}</div>
            </div>
            <div class="cd-field">
              <label>Last name</label>
              <div class="cd-value">{{ user.lastName }}</div>
            </div>
            <div class="cd-field">
              <label>User type</label>
              <div class="cd-value">
                {{ user.userType }} &ndash; {{ user.userType === 'A' ? 'Administrator' : 'User' }}
              </div>
            </div>
          </div>

          @if (isSelf(user)) {
            <div class="cd-message cd-message--warn" style="margin-top: 16px">
              You cannot delete the account you are signed on with.
            </div>
          } @else {
            <label class="cd-checkbox" style="margin-top: 18px">
              <input type="checkbox" name="confirmed" [(ngModel)]="confirmed" />
              Confirm deletion of {{ user.userId }}
            </label>
          }
        </div>

        <div class="cd-pfkeys">
          <button
            type="button"
            class="cd-danger"
            [disabled]="!confirmed || isSelf(user) || busy()"
            (click)="remove(user)"
          >
            Delete
          </button>
          <a class="cd-btn" routerLink="/admin/users">
            Return
          </a>
        </div>
      </div>
    }
  `,
})
export class UserDeleteComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  userId = '';
  confirmed = false;

  readonly detail = signal<UserDetail | null>(null);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'ok' | 'warn'>('error');
  readonly field = signal<string | null>(null);
  readonly busy = signal(false);

  constructor() {
    const fromRoute = this.route.snapshot.paramMap.get('userId');
    if (fromRoute) {
      this.userId = fromRoute;
      this.fetch();
    }
  }

  isSelf(user: UserDetail): boolean {
    return user.userId === this.auth.user()?.userId;
  }

  fetch(): void {
    this.message.set(null);
    this.field.set(null);
    this.confirmed = false;
    const id = this.userId.trim();
    if (!id) {
      this.field.set('userId');
      this.kind.set('error');
      this.message.set('User ID can NOT be empty...');
      return;
    }
    this.api.user(id).subscribe({
      next: (user) => this.detail.set(user),
      error: (error: unknown) => {
        this.detail.set(null);
        this.kind.set('error');
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  remove(user: UserDetail): void {
    this.busy.set(true);
    this.message.set(null);
    this.api.deleteUser(user.userId).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.kind.set('ok');
        this.message.set(result.message);
        this.detail.set(null);
        this.userId = '';
        this.confirmed = false;
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.kind.set('error');
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }
}

import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService, errorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { PageResult, UserRow } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Security user list. COBOL program {@code COUSR00C}, map {@code COUSR00 / COUSR0A}, transaction
 * {@code CU00}.
 *
 * <p>Ten rows per page with keyset paging and the two legacy row actions: U update and D delete.
 * Deletion asks for an explicit confirmation, which the source did not (FR-USER-006).</p>
 */
@Component({
  selector: 'cd-user-list',
  imports: [FormsModule, RouterLink, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      tran="CU00"
      program="COUSR00C"
      title="Security users"
      subtitle="Ten rows per page; U updates a user, D deletes one"
      origin="COUSR00C.cbl / COUSR00.bms"
    />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Search</h2>
        <form class="cd-actions" (ngSubmit)="search()">
          <input
            name="filter"
            class="cd-mono"
            style="width: 160px"
            maxlength="8"
            placeholder="User ID"
            [(ngModel)]="filter"
            aria-label="User ID filter"
          />
          <button type="submit" class="cd-primary">Search</button>
          <button type="button" (click)="reset()">Clear</button>
          <a class="cd-btn" routerLink="/admin/users/add">Add user</a>
        </form>
      </div>
    </div>

    <cd-message [text]="message()" [kind]="kind()" />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Users</h2>
        <span class="cd-inline-note">Page {{ page()?.pageNumber ?? 1 }}</span>
      </div>

      <div class="cd-panel__body cd-panel__body--flush">
        <div class="cd-table-wrap">
          <table class="cd-table">
            <thead>
              <tr>
                <th>User ID</th>
                <th>First name</th>
                <th>Last name</th>
                <th>Type</th>
                <th>State</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (row of page()?.rows ?? []; track row.userId) {
                <tr>
                  <td class="cd-mono">{{ row.userId }}</td>
                  <td>{{ row.firstName }}</td>
                  <td>{{ row.lastName }}</td>
                  <td>
                    <span
                      class="cd-badge"
                      [class.cd-badge--admin]="row.userType === 'A'"
                      [class.cd-badge--off]="row.userType !== 'A'"
                    >
                      {{ row.userType === 'A' ? 'Administrator' : 'User' }}
                    </span>
                  </td>
                  <td>
                    <span
                      class="cd-badge"
                      [class.cd-badge--ok]="row.active"
                      [class.cd-badge--off]="!row.active"
                    >
                      {{ row.active ? 'Active' : 'Disabled' }}
                    </span>
                  </td>
                  <td>
                    <div class="cd-row-actions">
                      <a class="cd-btn cd-small" [routerLink]="['/admin/users/update', row.userId]">
                        U Update
                      </a>
                      <button
                        type="button"
                        class="cd-small cd-danger"
                        [disabled]="row.userId === auth.user()?.userId"
                        (click)="confirmDelete(row)"
                      >
                        D Delete
                      </button>
                    </div>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="6" class="cd-empty">No users match this search.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <div class="cd-pfkeys">
        <button type="button" [disabled]="!page()?.hasPrevious" (click)="previous()">
          <span class="cd-pfkey__label">F7</span>Previous page
        </button>
        <button type="button" [disabled]="!page()?.hasNext" (click)="next()">
          <span class="cd-pfkey__label">F8</span>Next page
        </button>
        <a class="cd-btn" routerLink="/admin-menu">
          <span class="cd-pfkey__label">F3</span>Admin menu
        </a>
      </div>
    </div>

    @if (pendingDelete(); as target) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Confirm deletion</h2>
        </div>
        <div class="cd-panel__body">
          <p>
            Delete user <strong class="cd-mono">{{ target.userId }}</strong>
            ({{ target.firstName }} {{ target.lastName }})? This cannot be undone.
          </p>
          <div class="cd-actions" style="margin-top: 14px">
            <button type="button" class="cd-danger" (click)="doDelete(target)">Yes, delete</button>
            <button type="button" (click)="pendingDelete.set(null)">Cancel</button>
          </div>
        </div>
      </div>
    }
  `,
})
export class UserListComponent {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);

  filter = '';

  readonly page = signal<PageResult<UserRow> | null>(null);
  readonly pendingDelete = signal<UserRow | null>(null);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'ok' | 'info'>('error');

  private pageNumber = 1;

  constructor() {
    this.load(null, 'first', 1);
  }

  search(): void {
    this.pageNumber = 1;
    this.load(null, 'first', 1);
  }

  reset(): void {
    this.filter = '';
    this.search();
  }

  next(): void {
    const current = this.page();
    if (current?.hasNext && current.lastKey) {
      this.pageNumber += 1;
      this.load(current.lastKey, 'next', this.pageNumber);
    }
  }

  previous(): void {
    const current = this.page();
    if (current?.hasPrevious && current.firstKey) {
      this.pageNumber = Math.max(1, this.pageNumber - 1);
      this.load(current.firstKey, 'prev', this.pageNumber);
    }
  }

  confirmDelete(row: UserRow): void {
    this.message.set(null);
    this.pendingDelete.set(row);
  }

  doDelete(row: UserRow): void {
    this.api.deleteUser(row.userId).subscribe({
      next: (result) => {
        this.pendingDelete.set(null);
        this.kind.set('ok');
        this.message.set(result.message);
        this.search();
      },
      error: (error: unknown) => {
        this.pendingDelete.set(null);
        this.kind.set('error');
        this.message.set(errorMessage(error));
      },
    });
  }

  private load(cursor: string | null, direction: string, page: number): void {
    this.api
      .users({ filter: this.filter.trim() || undefined, cursor, direction, page })
      .subscribe({
        next: (result) => {
          this.page.set(result);
          if (result.message && result.rows.length === 0) {
            this.kind.set('info');
            this.message.set(result.message);
          }
        },
        error: (error: unknown) => {
          this.page.set(null);
          this.kind.set('error');
          this.message.set(errorMessage(error));
        },
      });
  }
}

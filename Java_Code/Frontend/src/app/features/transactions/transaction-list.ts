import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { PageResult, TransactionRow } from '../../core/models';
import { AmountPipe } from '../../shared/amount.pipe';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Transaction list. COBOL program {@code COTRN00C}, map {@code COTRN00 / COTRN0A}, transaction
 * {@code CT00}.
 *
 * <p>Ten rows per page ordered by transaction id, an optional sixteen character numeric filter,
 * F7/F8 keyset paging and S to open a row.</p>
 */
@Component({
  selector: 'cd-transaction-list',
  imports: [FormsModule, RouterLink, AmountPipe, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      tran="CT00"
      program="COTRN00C"
      title="Transaction list"
      subtitle="Ten rows per page; S opens the detail screen"
      origin="COTRN00C.cbl / COTRN00.bms"
    />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Search</h2>
        <form class="cd-actions" (ngSubmit)="search()">
          <input
            name="filter"
            class="cd-mono"
            style="width: 220px"
            maxlength="16"
            placeholder="Transaction ID"
            [class.cd-invalid]="field() === 'filter'"
            [(ngModel)]="filter"
            aria-label="Transaction ID filter"
          />
          <button type="submit" class="cd-primary">Search</button>
          <button type="button" (click)="reset()">Clear</button>
          <a class="cd-btn" routerLink="/transactions/add">Add transaction</a>
        </form>
      </div>
    </div>

    <cd-message [text]="message()" [kind]="kind()" />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Transactions</h2>
        <span class="cd-inline-note">Page {{ page()?.pageNumber ?? 1 }}</span>
      </div>

      <div class="cd-panel__body cd-panel__body--flush">
        <div class="cd-table-wrap">
          <table class="cd-table">
            <thead>
              <tr>
                <th>Tran ID</th>
                <th>Date</th>
                <th>Description</th>
                <th class="cd-num">Amount</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (row of page()?.rows ?? []; track row.transactionId) {
                <tr>
                  <td class="cd-mono">{{ row.transactionId }}</td>
                  <td class="cd-mono">{{ row.date }}</td>
                  <td>{{ row.description }}</td>
                  <td class="cd-num" [class.cd-amount-neg]="row.amount < 0">
                    {{ row.amount | cdAmount }}
                  </td>
                  <td>
                    <a class="cd-btn cd-small" [routerLink]="['/transactions/view', row.transactionId]">
                      S View
                    </a>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" class="cd-empty">No transactions match this search.</td>
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
        <button type="button" (click)="reset()">
          <span class="cd-pfkey__label">F3</span>Return
        </button>
      </div>
    </div>
  `,
})
export class TransactionListComponent {
  private readonly api = inject(ApiService);

  filter = '';

  readonly page = signal<PageResult<TransactionRow> | null>(null);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'info'>('error');
  readonly field = signal<string | null>(null);

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

  private load(cursor: string | null, direction: string, page: number): void {
    this.message.set(null);
    this.field.set(null);
    this.api
      .transactions({ filter: this.filter.trim() || undefined, cursor, direction, page })
      .subscribe({
        next: (result) => {
          this.page.set(result);
          if (result.message) {
            this.kind.set('info');
            this.message.set(result.message);
          }
        },
        error: (error: unknown) => {
          this.page.set(null);
          this.kind.set('error');
          this.message.set(errorMessage(error));
          this.field.set(errorField(error));
        },
      });
  }
}

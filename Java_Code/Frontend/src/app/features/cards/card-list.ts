import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { CardRow, PageResult } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Card list. COBOL program {@code COCRDLIC}, map {@code COCRDLI / CCRDLIA}, transaction
 * {@code CCLI}.
 *
 * <p>Seven rows per page, an optional account filter and an optional card filter combined with
 * AND, and F7/F8 keyset paging. Page availability is computed from the next matching row, so F8
 * never leads to an empty page the way the legacy unfiltered look-ahead could.</p>
 */
@Component({
  selector: 'cd-card-list',
  imports: [FormsModule, RouterLink, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Credit card list"
      subtitle="Seven rows per page; S views a card, U updates it"
    />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Filters</h2>
        <form class="cd-actions" (ngSubmit)="search()">
          <input
            name="accountFilter"
            class="cd-mono"
            style="width: 160px"
            maxlength="11"
            placeholder="Account"
            [class.cd-invalid]="field() === 'accountFilter'"
            [(ngModel)]="accountFilter"
            aria-label="Account filter"
          />
          <input
            name="cardFilter"
            class="cd-mono"
            style="width: 200px"
            maxlength="16"
            placeholder="Card number"
            [class.cd-invalid]="field() === 'cardFilter'"
            [(ngModel)]="cardFilter"
            aria-label="Card filter"
          />
          <button type="submit" class="cd-primary">Search</button>
          <button type="button" (click)="reset()">Clear</button>
        </form>
      </div>
    </div>

    <cd-message [text]="message()" [kind]="kind()" />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Cards</h2>
        <span class="cd-inline-note">Page {{ page()?.pageNumber ?? 1 }}</span>
      </div>

      <div class="cd-panel__body cd-panel__body--flush">
        <div class="cd-table-wrap">
          <table class="cd-table">
            <thead>
              <tr>
                <th>Card number</th>
                <th>Account</th>
                <th>Embossed name</th>
                <th>Expiry</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (row of page()?.rows ?? []; track row.cardNumber) {
                <tr>
                  <td class="cd-mono">{{ row.cardNumber }}</td>
                  <td class="cd-mono">
                    <a [routerLink]="['/accounts/view', row.accountId]">{{ row.accountId }}</a>
                  </td>
                  <td>{{ row.embossedName }}</td>
                  <td class="cd-mono">{{ row.expirationDate }}</td>
                  <td>
                    <span
                      class="cd-badge"
                      [class.cd-badge--ok]="row.activeStatus === 'Y'"
                      [class.cd-badge--off]="row.activeStatus !== 'Y'"
                    >
                      {{ row.activeStatus === 'Y' ? 'Active' : 'Inactive' }}
                    </span>
                  </td>
                  <td>
                    <div class="cd-row-actions">
                      <a class="cd-btn cd-small" [routerLink]="['/cards/view', row.cardNumber]">S View</a>
                      <a class="cd-btn cd-small" [routerLink]="['/cards/update', row.cardNumber]">
                        U Update
                      </a>
                    </div>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="6" class="cd-empty">No records found for this search condition.</td>
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
export class CardListComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  accountFilter = '';
  cardFilter = '';

  readonly page = signal<PageResult<CardRow> | null>(null);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'info'>('error');
  readonly field = signal<string | null>(null);

  private pageNumber = 1;

  constructor() {
    const accountId = this.route.snapshot.queryParamMap.get('accountId');
    if (accountId) {
      this.accountFilter = accountId;
    }
    this.load(null, 'first', 1);
  }

  search(): void {
    this.pageNumber = 1;
    this.load(null, 'first', 1);
  }

  reset(): void {
    this.accountFilter = '';
    this.cardFilter = '';
    this.search();
  }

  /** F8 forward paging starts at the current last key. */
  next(): void {
    const current = this.page();
    if (current?.hasNext && current.lastKey) {
      this.pageNumber += 1;
      this.load(current.lastKey, 'next', this.pageNumber);
    }
  }

  /** F7 backward paging starts from the current first key. */
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
      .cards({
        accountId: this.accountFilter.trim() || undefined,
        cardNumber: this.cardFilter.trim() || undefined,
        cursor,
        direction,
        page,
      })
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

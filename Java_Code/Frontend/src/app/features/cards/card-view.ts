import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { CardDetail, TransactionRow } from '../../core/models';
import { AmountPipe } from '../../shared/amount.pipe';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Card view. COBOL program {@code COCRDSLC}, map {@code COCRDSL / CCRDSLA}, transaction
 * {@code CCDL}.
 *
 * <p>When both an account and a card are supplied the backend verifies that the card actually
 * belongs to that account (FR-CARD-004); the legacy program validated the account and then read
 * by card key alone. The CVV is never returned by the API and therefore never shown.</p>
 */
@Component({
  selector: 'cd-card-view',
  imports: [FormsModule, RouterLink, AmountPipe, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="View credit card"
      subtitle="The card must belong to the account entered"
    />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Search</h2>
        <form class="cd-actions" (ngSubmit)="fetch()">
          <input
            name="accountId"
            class="cd-mono"
            style="width: 160px"
            maxlength="11"
            placeholder="Account (optional)"
            [class.cd-invalid]="field() === 'accountId'"
            [(ngModel)]="accountId"
            aria-label="Account number"
          />
          <input
            name="cardNumber"
            class="cd-mono"
            style="width: 200px"
            maxlength="16"
            placeholder="Card number"
            [class.cd-invalid]="field() === 'cardNumber'"
            [(ngModel)]="cardNumber"
            aria-label="Card number"
          />
          <button type="submit" class="cd-primary">Fetch</button>
        </form>
      </div>
    </div>

    <cd-message [text]="message()" kind="error" />

    @if (detail(); as card) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Card {{ card.cardNumber }}</h2>
          <a class="cd-btn cd-small" [routerLink]="['/cards/update', card.cardNumber]">Update</a>
        </div>
        <div class="cd-panel__body">
          <div class="cd-grid">
            <div class="cd-field">
              <label>Account</label>
              <div class="cd-value">
                <a [routerLink]="['/accounts/view', card.accountId]">{{ card.accountId }}</a>
              </div>
            </div>
            <div class="cd-field">
              <label>Embossed name</label>
              <div class="cd-value">{{ card.embossedName }}</div>
            </div>
            <div class="cd-field">
              <label>Active status</label>
              <div class="cd-value">{{ card.activeStatus }}</div>
            </div>
            <div class="cd-field">
              <label>Expiry date</label>
              <div class="cd-value">{{ card.expirationDate }}</div>
            </div>
            <div class="cd-field">
              <label>CVV</label>
              <div class="cd-value">Not displayed</div>
              <span class="cd-field__hint">The verification value is never returned by the API</span>
            </div>
          </div>
        </div>

        <div class="cd-pfkeys">
          <a class="cd-btn" routerLink="/cards">
            <span class="cd-pfkey__label">F3</span>Return to list
          </a>
        </div>
      </div>

      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Transactions on this card</h2>
          <span class="cd-inline-note">{{ transactions().length }} record(s)</span>
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
                </tr>
              </thead>
              <tbody>
                @for (row of transactions(); track row.transactionId) {
                  <tr>
                    <td class="cd-mono">
                      <a [routerLink]="['/transactions/view', row.transactionId]">
                        {{ row.transactionId }}
                      </a>
                    </td>
                    <td class="cd-mono">{{ row.date }}</td>
                    <td>{{ row.description }}</td>
                    <td class="cd-num" [class.cd-amount-neg]="row.amount < 0">
                      {{ row.amount | cdAmount }}
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="4" class="cd-empty">No transactions for this card.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      </div>
    }
  `,
})
export class CardViewComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  accountId = '';
  cardNumber = '';

  readonly detail = signal<CardDetail | null>(null);
  readonly transactions = signal<TransactionRow[]>([]);
  readonly message = signal<string | null>(null);
  readonly field = signal<string | null>(null);

  constructor() {
    const routeCard = this.route.snapshot.paramMap.get('cardNumber');
    const queryAccount = this.route.snapshot.queryParamMap.get('accountId');
    if (queryAccount) {
      this.accountId = queryAccount;
    }
    if (routeCard) {
      this.cardNumber = routeCard;
      this.fetch();
    }
  }

  fetch(): void {
    this.message.set(null);
    this.field.set(null);
    const card = this.cardNumber.trim();
    if (!card) {
      this.field.set('cardNumber');
      this.message.set('Card number must be supplied.');
      this.detail.set(null);
      return;
    }
    const account = this.accountId.trim();
    const request$ = account
      ? this.api.cardForAccount(card, account)
      : this.api.card(card);

    request$.subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.cardNumber = detail.cardNumber;
        this.accountId = detail.accountId;
        this.loadTransactions(detail.cardNumber);
      },
      error: (error: unknown) => {
        this.detail.set(null);
        this.transactions.set([]);
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  private loadTransactions(cardNumber: string): void {
    this.api.transactionsByCard(cardNumber).subscribe({
      next: (rows) => this.transactions.set(rows),
      error: () => this.transactions.set([]),
    });
  }
}

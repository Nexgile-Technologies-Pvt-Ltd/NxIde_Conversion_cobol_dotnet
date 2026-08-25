import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { TransactionDetail } from '../../core/models';
import { AmountPipe } from '../../shared/amount.pipe';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Transaction detail. COBOL program {@code COTRN01C}, map {@code COTRN01 / COTRN1A}, transaction
 * {@code CT01}.
 *
 * <p>Read only: the source issued {@code READ UPDATE} on a view-only screen, which FR-TRAN-003
 * forbids. Type and category descriptions are resolved through the reference tables using the
 * composite {@code (type, category)} key.</p>
 */
@Component({
  selector: 'cd-transaction-view',
  imports: [FormsModule, RouterLink, AmountPipe, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="View transaction"
      subtitle="Read-only detail with resolved type and category descriptions"
    />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Search</h2>
        <form class="cd-actions" (ngSubmit)="fetch()">
          <input
            name="transactionId"
            class="cd-mono"
            style="width: 220px"
            maxlength="16"
            placeholder="Transaction ID"
            [class.cd-invalid]="field() === 'transactionId'"
            [(ngModel)]="transactionId"
            aria-label="Transaction ID"
          />
          <button type="submit" class="cd-primary">
            <span class="cd-pfkey__label">Enter</span>Fetch
          </button>
          <button type="button" (click)="clear()">
            <span class="cd-pfkey__label">F4</span>Clear
          </button>
          <a class="cd-btn" routerLink="/transactions">
            <span class="cd-pfkey__label">F5</span>Transaction list
          </a>
        </form>
      </div>
    </div>

    <cd-message [text]="message()" kind="error" />

    @if (detail(); as tran) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Transaction {{ tran.transactionId }}</h2>
        </div>
        <div class="cd-panel__body">
          <div class="cd-grid">
            <div class="cd-field">
              <label>Card number</label>
              <div class="cd-value">
                <a [routerLink]="['/cards/view', tran.cardNumber]">{{ tran.cardNumber }}</a>
              </div>
            </div>
            <div class="cd-field">
              <label>Account</label>
              <div class="cd-value">
                @if (tran.accountId) {
                  <a [routerLink]="['/accounts/view', tran.accountId]">{{ tran.accountId }}</a>
                } @else {
                  (unresolved)
                }
              </div>
            </div>
            <div class="cd-field">
              <label>Type</label>
              <div class="cd-value">{{ tran.typeCode }} &nbsp; {{ tran.typeDescription }}</div>
            </div>
            <div class="cd-field">
              <label>Category</label>
              <div class="cd-value">{{ tran.categoryCode }} &nbsp; {{ tran.categoryDescription }}</div>
            </div>
            <div class="cd-field">
              <label>Source</label>
              <div class="cd-value">{{ tran.source }}</div>
            </div>
            <div class="cd-field">
              <label>Amount</label>
              <div class="cd-value cd-value--num" [class.cd-amount-neg]="tran.amount < 0">
                {{ tran.amount | cdAmount }}
              </div>
            </div>
            <div class="cd-field">
              <label>Origin date</label>
              <div class="cd-value">{{ tran.originDate }}</div>
            </div>
            <div class="cd-field">
              <label>Process date</label>
              <div class="cd-value">{{ tran.processDate }}</div>
            </div>
          </div>

          <div class="cd-section-title" style="margin-top: 22px">Description and merchant</div>
          <div class="cd-grid cd-grid--2">
            <div class="cd-field">
              <label>Description</label>
              <div class="cd-value">{{ tran.description }}</div>
            </div>
            <div class="cd-field">
              <label>Merchant ID</label>
              <div class="cd-value">{{ tran.merchantId }}</div>
            </div>
            <div class="cd-field">
              <label>Merchant name</label>
              <div class="cd-value">{{ tran.merchantName }}</div>
            </div>
            <div class="cd-field">
              <label>Merchant city</label>
              <div class="cd-value">{{ tran.merchantCity }}</div>
            </div>
            <div class="cd-field">
              <label>Merchant ZIP</label>
              <div class="cd-value">{{ tran.merchantZip }}</div>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class TransactionViewComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  transactionId = '';

  readonly detail = signal<TransactionDetail | null>(null);
  readonly message = signal<string | null>(null);
  readonly field = signal<string | null>(null);

  constructor() {
    const fromRoute = this.route.snapshot.paramMap.get('transactionId');
    if (fromRoute) {
      this.transactionId = fromRoute;
      this.fetch();
    }
  }

  fetch(): void {
    this.message.set(null);
    this.field.set(null);
    const id = this.transactionId.trim();
    if (!id) {
      this.field.set('transactionId');
      this.message.set('Tran ID can NOT be empty...');
      this.detail.set(null);
      return;
    }
    this.api.transaction(id).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.transactionId = detail.transactionId;
      },
      error: (error: unknown) => {
        this.detail.set(null);
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  /** F4 clears the screen. */
  clear(): void {
    this.transactionId = '';
    this.detail.set(null);
    this.message.set(null);
    this.field.set(null);
  }
}

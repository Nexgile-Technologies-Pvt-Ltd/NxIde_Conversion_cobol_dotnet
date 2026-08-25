import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { BillPaymentView } from '../../core/models';
import { AmountPipe } from '../../shared/amount.pipe';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Bill payment. COBOL program {@code COBIL00C}, map {@code COBIL00 / COBIL0A}, transaction
 * {@code CB00}.
 *
 * <p>The screen shows the current balance and asks for a confirmation. A non-positive balance
 * creates nothing; a confirmed positive balance creates a full balance payment using the source
 * values (type {@code 02}, category {@code 0002}, source {@code POS TERM}, description
 * {@code BILL PAYMENT - ONLINE}) and reduces the account balance by exactly that amount.</p>
 */
@Component({
  selector: 'cd-bill-payment',
  imports: [FormsModule, RouterLink, AmountPipe, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Bill payment"
      subtitle="Pays the whole current balance in one atomic unit of work"
    />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Account</h2>
        <form class="cd-actions" (ngSubmit)="fetch()">
          <input
            name="accountId"
            class="cd-mono"
            style="width: 170px"
            maxlength="11"
            placeholder="00000000001"
            [class.cd-invalid]="field() === 'accountId'"
            [(ngModel)]="accountId"
            aria-label="Account number"
          />
          <button type="submit" class="cd-primary">
            Fetch
          </button>
          <button type="button" (click)="clear()">
            Clear
          </button>
        </form>
      </div>
    </div>

    <cd-message [text]="message()" [kind]="kind()" />

    @if (view(); as bill) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Account {{ bill.accountId }}</h2>
          <a class="cd-btn cd-small" [routerLink]="['/accounts/view', bill.accountId]">View account</a>
        </div>
        <div class="cd-panel__body">
          <div class="cd-grid">
            <div class="cd-field">
              <label>Current balance</label>
              <div class="cd-value cd-value--num" [class.cd-amount-neg]="bill.currentBalance < 0">
                {{ bill.currentBalance | cdAmount }}
              </div>
            </div>
            <div class="cd-field">
              <label>Card used for the payment</label>
              <div class="cd-value">{{ bill.cardNumber || '(none)' }}</div>
            </div>
          </div>

          @if (bill.payable) {
            <label class="cd-checkbox" style="margin-top: 20px">
              <input type="checkbox" name="confirmed" [(ngModel)]="confirmed" />
              Confirm payment of {{ bill.currentBalance | cdAmount }}
            </label>
          }
        </div>

        <div class="cd-pfkeys">
          <button
            type="button"
            class="cd-primary"
            [disabled]="!bill.payable || !confirmed || busy()"
            (click)="pay()"
          >
            Pay full balance
          </button>
          <a class="cd-btn" routerLink="/main-menu">
            Return
          </a>
        </div>
      </div>
    }
  `,
})
export class BillPaymentComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  accountId = '';
  confirmed = false;

  readonly view = signal<BillPaymentView | null>(null);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'ok' | 'warn'>('error');
  readonly field = signal<string | null>(null);
  readonly busy = signal(false);

  constructor() {
    const fromQuery = this.route.snapshot.queryParamMap.get('accountId');
    if (fromQuery) {
      this.accountId = fromQuery;
      this.fetch();
    }
  }

  fetch(): void {
    this.message.set(null);
    this.field.set(null);
    this.confirmed = false;
    const id = this.accountId.trim();
    if (!id) {
      this.field.set('accountId');
      this.kind.set('error');
      this.message.set('Account Number must be supplied.');
      this.view.set(null);
      return;
    }
    this.api.billPaymentView(id).subscribe({
      next: (result) => {
        this.view.set(result);
        this.accountId = result.accountId;
        if (result.message) {
          this.kind.set('warn');
          this.message.set(result.message);
        }
      },
      error: (error: unknown) => {
        this.view.set(null);
        this.kind.set('error');
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  pay(): void {
    const bill = this.view();
    if (!bill) {
      return;
    }
    this.busy.set(true);
    this.message.set(null);
    this.api.payBill(bill.accountId, this.confirmed).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.kind.set('ok');
        this.message.set(result.message);
        this.confirmed = false;
        this.fetchQuietly(bill.accountId);
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
    this.accountId = '';
    this.confirmed = false;
    this.view.set(null);
    this.message.set(null);
    this.field.set(null);
  }

  /** Refreshes the balance after a payment without clearing the success message. */
  private fetchQuietly(accountId: string): void {
    this.api.billPaymentView(accountId).subscribe({
      next: (result) => this.view.set(result),
      error: () => undefined,
    });
  }
}

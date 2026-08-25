import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { AccountDetail, AccountSummary } from '../../core/models';
import { AmountPipe } from '../../shared/amount.pipe';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Account view. COBOL program {@code COACTVWC}, map {@code COACTVW / CACTVWA}, transaction
 * {@code CAVW}.
 *
 * <p>The account id must be an eleven digit non-zero number. The backend resolves cross-reference
 * then account then customer, in that order, and stops on the first missing record instead of
 * continuing with stale buffers as the source did.</p>
 */
@Component({
  selector: 'cd-account-view',
  imports: [FormsModule, RouterLink, AmountPipe, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="View account"
      subtitle="Account, customer and the cross-referenced card"
    />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Search</h2>
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
          <button type="submit" class="cd-primary" [disabled]="busy()">Fetch</button>
          <button type="button" (click)="clear()">Clear</button>
        </form>
      </div>

      @if (accounts().length) {
        <div class="cd-panel__body" style="padding-top: 12px; padding-bottom: 12px">
          <div class="cd-field">
            <label for="picker">Or pick an account</label>
            <select id="picker" name="picker" [(ngModel)]="accountId" (change)="fetch()">
              <option value="">-- select --</option>
              @for (item of accounts(); track item.accountId) {
                <option [value]="item.accountId">
                  {{ item.accountId }} &nbsp; {{ item.customerName }}
                </option>
              }
            </select>
          </div>
        </div>
      }
    </div>

    <cd-message [text]="message()" kind="error" />

    @if (detail(); as account) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Account {{ account.accountId }}</h2>
          <div class="cd-actions">
            <a class="cd-btn cd-small" [routerLink]="['/accounts/update', account.accountId]">
              Update
            </a>
            <a class="cd-btn cd-small" [routerLink]="['/cards']" [queryParams]="{ accountId: account.accountId }">
              Cards
            </a>
            <a
              class="cd-btn cd-small"
              [routerLink]="['/bill-payment']"
              [queryParams]="{ accountId: account.accountId }"
            >
              Bill payment
            </a>
          </div>
        </div>

        <div class="cd-panel__body">
          <div class="cd-section-title">Account</div>
          <div class="cd-grid">
            <div class="cd-field">
              <label>Active status</label>
              <div class="cd-value">{{ account.activeStatus }}</div>
            </div>
            <div class="cd-field">
              <label>Open date</label>
              <div class="cd-value">{{ account.openDate }}</div>
            </div>
            <div class="cd-field">
              <label>Expiry date</label>
              <div class="cd-value">{{ account.expirationDate }}</div>
            </div>
            <div class="cd-field">
              <label>Reissue date</label>
              <div class="cd-value">{{ account.reissueDate }}</div>
            </div>
            <div class="cd-field">
              <label>Current balance</label>
              <div class="cd-value cd-value--num">{{ account.currentBalance | cdAmount }}</div>
            </div>
            <div class="cd-field">
              <label>Credit limit</label>
              <div class="cd-value cd-value--num">{{ account.creditLimit | cdAmount }}</div>
            </div>
            <div class="cd-field">
              <label>Cash credit limit</label>
              <div class="cd-value cd-value--num">{{ account.cashCreditLimit | cdAmount }}</div>
            </div>
            <div class="cd-field">
              <label>Current cycle credit</label>
              <div class="cd-value cd-value--num">{{ account.currentCycleCredit | cdAmount }}</div>
            </div>
            <div class="cd-field">
              <label>Current cycle debit</label>
              <div class="cd-value cd-value--num">{{ account.currentCycleDebit | cdAmount }}</div>
            </div>
            <div class="cd-field">
              <label>Account ZIP</label>
              <div class="cd-value">{{ account.accountZip }}</div>
            </div>
            <div class="cd-field">
              <label>Disclosure group</label>
              <div class="cd-value">{{ account.groupId || '(none)' }}</div>
            </div>
            <div class="cd-field">
              <label>Cross-referenced card</label>
              <div class="cd-value">
                @if (account.cardNumber) {
                  <a [routerLink]="['/cards/view', account.cardNumber]">{{ account.cardNumber }}</a>
                } @else {
                  (none)
                }
              </div>
            </div>
          </div>

          <div class="cd-section-title" style="margin-top: 22px">Customer</div>
          <div class="cd-grid">
            <div class="cd-field">
              <label>Customer ID</label>
              <div class="cd-value">{{ account.customerId }}</div>
            </div>
            <div class="cd-field">
              <label>First name</label>
              <div class="cd-value">{{ account.firstName }}</div>
            </div>
            <div class="cd-field">
              <label>Middle name</label>
              <div class="cd-value">{{ account.middleName || '-' }}</div>
            </div>
            <div class="cd-field">
              <label>Last name</label>
              <div class="cd-value">{{ account.lastName }}</div>
            </div>
            <div class="cd-field">
              <label>SSN</label>
              <div class="cd-value">{{ maskSsn(account.ssn) }}</div>
            </div>
            <div class="cd-field">
              <label>Date of birth</label>
              <div class="cd-value">{{ account.dateOfBirth }}</div>
            </div>
            <div class="cd-field">
              <label>FICO score</label>
              <div class="cd-value cd-value--num">{{ account.ficoScore }}</div>
            </div>
            <div class="cd-field">
              <label>Primary card holder</label>
              <div class="cd-value">{{ account.primaryCardHolderIndicator }}</div>
            </div>
          </div>

          <div class="cd-section-title" style="margin-top: 22px">Address and contact</div>
          <div class="cd-grid">
            <div class="cd-field">
              <label>Address line 1</label>
              <div class="cd-value">{{ account.addressLine1 }}</div>
            </div>
            <div class="cd-field">
              <label>Address line 2</label>
              <div class="cd-value">{{ account.addressLine2 || '-' }}</div>
            </div>
            <div class="cd-field">
              <label>City</label>
              <div class="cd-value">{{ account.city }}</div>
            </div>
            <div class="cd-field">
              <label>State</label>
              <div class="cd-value">{{ account.stateCode }}</div>
            </div>
            <div class="cd-field">
              <label>ZIP</label>
              <div class="cd-value">{{ account.zipCode }}</div>
            </div>
            <div class="cd-field">
              <label>Country</label>
              <div class="cd-value">{{ account.countryCode }}</div>
            </div>
            <div class="cd-field">
              <label>Phone 1</label>
              <div class="cd-value">{{ account.phone1 }}</div>
            </div>
            <div class="cd-field">
              <label>Phone 2</label>
              <div class="cd-value">{{ account.phone2 }}</div>
            </div>
            <div class="cd-field">
              <label>Government issued ID</label>
              <div class="cd-value">{{ maskTail(account.governmentIssuedId) }}</div>
            </div>
            <div class="cd-field">
              <label>EFT account</label>
              <div class="cd-value">{{ maskTail(account.eftAccountId) }}</div>
            </div>
          </div>
        </div>

        <div class="cd-pfkeys">
          <button type="button" (click)="clear()">
            Return
          </button>
          <button type="button" (click)="fetch()">
            Refresh
          </button>
        </div>
      </div>
    }
  `,
})
export class AccountViewComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  accountId = '';

  readonly detail = signal<AccountDetail | null>(null);
  readonly accounts = signal<AccountSummary[]>([]);
  readonly message = signal<string | null>(null);
  readonly field = signal<string | null>(null);
  readonly busy = signal(false);

  constructor() {
    this.api.accounts(500).subscribe({
      next: (rows) => this.accounts.set(rows),
      error: () => undefined,
    });

    const fromRoute =
      this.route.snapshot.paramMap.get('accountId') ??
      this.route.snapshot.queryParamMap.get('accountId');
    if (fromRoute) {
      this.accountId = fromRoute;
      this.fetch();
    }
  }

  fetch(): void {
    this.message.set(null);
    this.field.set(null);
    const id = this.accountId.trim();
    if (!id) {
      this.field.set('accountId');
      this.message.set('Account Number must be supplied.');
      this.detail.set(null);
      return;
    }
    this.busy.set(true);
    this.api.account(id).subscribe({
      next: (detail) => {
        this.busy.set(false);
        this.detail.set(detail);
        this.accountId = detail.accountId;
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.detail.set(null);
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  clear(): void {
    this.accountId = '';
    this.detail.set(null);
    this.message.set(null);
    this.field.set(null);
    void this.router.navigate(['/accounts/view']);
  }

  /** Sensitive values are never shown in full (NFR-006). */
  maskSsn(value: string): string {
    const digits = (value ?? '').replace(/\D/g, '');
    return digits.length === 9 ? `***-**-${digits.slice(5)}` : '***-**-****';
  }

  maskTail(value: string): string {
    const text = (value ?? '').trim();
    if (text.length <= 4) {
      return text ? '****' : '-';
    }
    return `${'*'.repeat(text.length - 4)}${text.slice(-4)}`;
  }
}

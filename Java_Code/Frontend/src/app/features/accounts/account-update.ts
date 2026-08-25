import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { AccountDetail, AccountUpdateRequest } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Account update. COBOL program {@code COACTUPC}, map {@code COACTUP / CACTUPA}, transaction
 * {@code CAUP}.
 *
 * <p>The state machine is the source one: search, show details, edit, save (F5), cancel (F12).
 * Save is only offered once a record has been fetched. Validation runs on the server in the exact
 * documented order, and the returned message names the first offending field.</p>
 */
@Component({
  selector: 'cd-account-update',
  imports: [FormsModule, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Update account"
      subtitle="Account and customer are saved together as one unit of work"
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
          <button type="submit" class="cd-primary">Fetch</button>
        </form>
      </div>
    </div>

    <cd-message [text]="message()" [kind]="kind()" />

    @if (form(); as model) {
      <form (ngSubmit)="save()">
        <div class="cd-panel">
          <div class="cd-panel__head">
            <h2>Account {{ original()?.accountId }}</h2>
          </div>
          <div class="cd-panel__body">
            <div class="cd-grid">
              <div class="cd-field">
                <label for="activeStatus">Active status (Y/N)</label>
                <input
                  id="activeStatus"
                  name="activeStatus"
                  class="cd-mono"
                  maxlength="1"
                  [class.cd-invalid]="field() === 'activeStatus'"
                  [(ngModel)]="model.activeStatus"
                />
              </div>
              <div class="cd-field">
                <label for="openDate">Open date</label>
                <input
                  id="openDate"
                  name="openDate"
                  class="cd-mono"
                  maxlength="10"
                  placeholder="YYYY-MM-DD"
                  [class.cd-invalid]="field() === 'openDate'"
                  [(ngModel)]="model.openDate"
                />
              </div>
              <div class="cd-field">
                <label for="expirationDate">Expiry date</label>
                <input
                  id="expirationDate"
                  name="expirationDate"
                  class="cd-mono"
                  maxlength="10"
                  placeholder="YYYY-MM-DD"
                  [class.cd-invalid]="field() === 'expirationDate'"
                  [(ngModel)]="model.expirationDate"
                />
              </div>
              <div class="cd-field">
                <label for="reissueDate">Reissue date</label>
                <input
                  id="reissueDate"
                  name="reissueDate"
                  class="cd-mono"
                  maxlength="10"
                  placeholder="YYYY-MM-DD"
                  [class.cd-invalid]="field() === 'reissueDate'"
                  [(ngModel)]="model.reissueDate"
                />
              </div>
              <div class="cd-field">
                <label for="creditLimit">Credit limit</label>
                <input
                  id="creditLimit"
                  name="creditLimit"
                  class="cd-mono"
                  [class.cd-invalid]="field() === 'creditLimit'"
                  [(ngModel)]="model.creditLimit"
                />
              </div>
              <div class="cd-field">
                <label for="cashCreditLimit">Cash credit limit</label>
                <input
                  id="cashCreditLimit"
                  name="cashCreditLimit"
                  class="cd-mono"
                  [class.cd-invalid]="field() === 'cashCreditLimit'"
                  [(ngModel)]="model.cashCreditLimit"
                />
              </div>
              <div class="cd-field">
                <label for="currentBalance">Current balance</label>
                <input
                  id="currentBalance"
                  name="currentBalance"
                  class="cd-mono"
                  [class.cd-invalid]="field() === 'currentBalance'"
                  [(ngModel)]="model.currentBalance"
                />
              </div>
              <div class="cd-field">
                <label for="currentCycleCredit">Current cycle credit</label>
                <input
                  id="currentCycleCredit"
                  name="currentCycleCredit"
                  class="cd-mono"
                  [class.cd-invalid]="field() === 'currentCycleCredit'"
                  [(ngModel)]="model.currentCycleCredit"
                />
              </div>
              <div class="cd-field">
                <label for="currentCycleDebit">Current cycle debit</label>
                <input
                  id="currentCycleDebit"
                  name="currentCycleDebit"
                  class="cd-mono"
                  [class.cd-invalid]="field() === 'currentCycleDebit'"
                  [(ngModel)]="model.currentCycleDebit"
                />
              </div>
              <div class="cd-field">
                <label for="groupId">Disclosure group</label>
                <input
                  id="groupId"
                  name="groupId"
                  class="cd-mono"
                  maxlength="10"
                  [(ngModel)]="model.groupId"
                />
              </div>
            </div>
          </div>
        </div>

        <div class="cd-panel">
          <div class="cd-panel__head">
            <h2>Customer {{ original()?.customerId }}</h2>
          </div>
          <div class="cd-panel__body">
            <div class="cd-grid">
              <div class="cd-field">
                <label for="ssn">SSN</label>
                <input
                  id="ssn"
                  name="ssn"
                  class="cd-mono"
                  maxlength="11"
                  placeholder="123456789"
                  [class.cd-invalid]="field() === 'ssn'"
                  [(ngModel)]="model.ssn"
                />
                <span class="cd-field__hint">
                  First part cannot be 000, 666 or 900-999
                </span>
              </div>
              <div class="cd-field">
                <label for="dateOfBirth">Date of birth</label>
                <input
                  id="dateOfBirth"
                  name="dateOfBirth"
                  class="cd-mono"
                  maxlength="10"
                  placeholder="YYYY-MM-DD"
                  [class.cd-invalid]="field() === 'dateOfBirth'"
                  [(ngModel)]="model.dateOfBirth"
                />
                <span class="cd-field__hint">Must be in the past</span>
              </div>
              <div class="cd-field">
                <label for="ficoScore">FICO score</label>
                <input
                  id="ficoScore"
                  name="ficoScore"
                  class="cd-mono"
                  maxlength="3"
                  [class.cd-invalid]="field() === 'ficoScore'"
                  [(ngModel)]="model.ficoScore"
                />
                <span class="cd-field__hint">300 to 850</span>
              </div>
              <div class="cd-field">
                <label for="primaryCardHolderIndicator">Primary card holder (Y/N)</label>
                <input
                  id="primaryCardHolderIndicator"
                  name="primaryCardHolderIndicator"
                  class="cd-mono"
                  maxlength="1"
                  [class.cd-invalid]="field() === 'primaryCardHolderIndicator'"
                  [(ngModel)]="model.primaryCardHolderIndicator"
                />
              </div>
              <div class="cd-field">
                <label for="firstName">First name</label>
                <input
                  id="firstName"
                  name="firstName"
                  maxlength="25"
                  [class.cd-invalid]="field() === 'firstName'"
                  [(ngModel)]="model.firstName"
                />
              </div>
              <div class="cd-field">
                <label for="middleName">Middle name</label>
                <input
                  id="middleName"
                  name="middleName"
                  maxlength="25"
                  [class.cd-invalid]="field() === 'middleName'"
                  [(ngModel)]="model.middleName"
                />
              </div>
              <div class="cd-field">
                <label for="lastName">Last name</label>
                <input
                  id="lastName"
                  name="lastName"
                  maxlength="25"
                  [class.cd-invalid]="field() === 'lastName'"
                  [(ngModel)]="model.lastName"
                />
              </div>
            </div>

            <div class="cd-section-title" style="margin-top: 22px">Address and contact</div>
            <div class="cd-grid">
              <div class="cd-field">
                <label for="addressLine1">Address line 1</label>
                <input
                  id="addressLine1"
                  name="addressLine1"
                  maxlength="50"
                  [class.cd-invalid]="field() === 'addressLine1'"
                  [(ngModel)]="model.addressLine1"
                />
              </div>
              <div class="cd-field">
                <label for="addressLine2">Address line 2</label>
                <input
                  id="addressLine2"
                  name="addressLine2"
                  maxlength="50"
                  [(ngModel)]="model.addressLine2"
                />
              </div>
              <div class="cd-field">
                <label for="city">City</label>
                <input
                  id="city"
                  name="city"
                  maxlength="50"
                  [class.cd-invalid]="field() === 'city'"
                  [(ngModel)]="model.city"
                />
              </div>
              <div class="cd-field">
                <label for="stateCode">State</label>
                <input
                  id="stateCode"
                  name="stateCode"
                  class="cd-mono"
                  maxlength="2"
                  [class.cd-invalid]="field() === 'stateCode'"
                  [(ngModel)]="model.stateCode"
                />
              </div>
              <div class="cd-field">
                <label for="zipCode">ZIP</label>
                <input
                  id="zipCode"
                  name="zipCode"
                  class="cd-mono"
                  maxlength="10"
                  [class.cd-invalid]="field() === 'zipCode'"
                  [(ngModel)]="model.zipCode"
                />
                <span class="cd-field__hint">Must match the state</span>
              </div>
              <div class="cd-field">
                <label for="countryCode">Country</label>
                <input
                  id="countryCode"
                  name="countryCode"
                  class="cd-mono"
                  maxlength="3"
                  [class.cd-invalid]="field() === 'countryCode'"
                  [(ngModel)]="model.countryCode"
                />
              </div>
              <div class="cd-field">
                <label for="phone1">Phone 1</label>
                <input
                  id="phone1"
                  name="phone1"
                  class="cd-mono"
                  maxlength="15"
                  placeholder="(908)119-8310"
                  [class.cd-invalid]="field() === 'phone1'"
                  [(ngModel)]="model.phone1"
                />
              </div>
              <div class="cd-field">
                <label for="phone2">Phone 2</label>
                <input
                  id="phone2"
                  name="phone2"
                  class="cd-mono"
                  maxlength="15"
                  placeholder="(373)693-8684"
                  [class.cd-invalid]="field() === 'phone2'"
                  [(ngModel)]="model.phone2"
                />
              </div>
              <div class="cd-field">
                <label for="governmentIssuedId">Government issued ID</label>
                <input
                  id="governmentIssuedId"
                  name="governmentIssuedId"
                  class="cd-mono"
                  maxlength="20"
                  [(ngModel)]="model.governmentIssuedId"
                />
              </div>
              <div class="cd-field">
                <label for="eftAccountId">EFT account</label>
                <input
                  id="eftAccountId"
                  name="eftAccountId"
                  class="cd-mono"
                  maxlength="10"
                  [(ngModel)]="model.eftAccountId"
                />
              </div>
            </div>
          </div>

          <div class="cd-pfkeys">
            <button type="submit" class="cd-primary" [disabled]="busy()">
              <span class="cd-pfkey__label">F5</span>Save
            </button>
            <button type="button" (click)="cancel()">
              <span class="cd-pfkey__label">F12</span>Cancel
            </button>
            <button type="button" (click)="close()">
              <span class="cd-pfkey__label">F3</span>Return
            </button>
          </div>
        </div>
      </form>
    }
  `,
})
export class AccountUpdateComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  accountId = '';

  readonly original = signal<AccountDetail | null>(null);
  readonly form = signal<AccountUpdateRequest | null>(null);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'ok' | 'info'>('error');
  readonly field = signal<string | null>(null);
  readonly busy = signal(false);

  constructor() {
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
      this.kind.set('error');
      this.message.set('Account Number must be supplied.');
      return;
    }
    this.api.account(id).subscribe({
      next: (detail) => {
        this.original.set(detail);
        this.form.set(toForm(detail));
        this.accountId = detail.accountId;
      },
      error: (error: unknown) => {
        this.original.set(null);
        this.form.set(null);
        this.kind.set('error');
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  /** F5: save. Validation order is applied server side; the first failure is reported here. */
  save(): void {
    const payload = this.form();
    const original = this.original();
    if (!payload || !original) {
      return;
    }
    this.busy.set(true);
    this.message.set(null);
    this.field.set(null);
    this.api.updateAccount(original.accountId, payload).subscribe({
      next: (detail) => {
        this.busy.set(false);
        this.original.set(detail);
        this.form.set(toForm(detail));
        this.kind.set('ok');
        this.message.set(`Account ${detail.accountId} has been updated ...`);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.kind.set('error');
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  /** F12: restore the values that were fetched. */
  cancel(): void {
    const original = this.original();
    if (original) {
      this.form.set(toForm(original));
      this.kind.set('info');
      this.message.set('Changes discarded; the fetched values have been restored.');
      this.field.set(null);
    }
  }

  /** F3: leave the screen without saving. */
  close(): void {
    this.original.set(null);
    this.form.set(null);
    this.accountId = '';
    this.message.set(null);
    this.field.set(null);
  }
}

/** Maps a fetched record onto the editable form, keeping the concurrency tokens. */
function toForm(detail: AccountDetail): AccountUpdateRequest {
  return {
    accountId: detail.accountId,
    activeStatus: detail.activeStatus,
    openDate: detail.openDate,
    creditLimit: detail.creditLimit.toFixed(2),
    expirationDate: detail.expirationDate,
    cashCreditLimit: detail.cashCreditLimit.toFixed(2),
    reissueDate: detail.reissueDate,
    currentBalance: detail.currentBalance.toFixed(2),
    currentCycleCredit: detail.currentCycleCredit.toFixed(2),
    currentCycleDebit: detail.currentCycleDebit.toFixed(2),
    groupId: detail.groupId,
    ssn: detail.ssn,
    dateOfBirth: detail.dateOfBirth,
    ficoScore: String(detail.ficoScore),
    firstName: detail.firstName,
    middleName: detail.middleName,
    lastName: detail.lastName,
    addressLine1: detail.addressLine1,
    stateCode: detail.stateCode,
    addressLine2: detail.addressLine2,
    zipCode: detail.zipCode,
    city: detail.city,
    countryCode: detail.countryCode,
    phone1: detail.phone1,
    phone2: detail.phone2,
    governmentIssuedId: detail.governmentIssuedId,
    eftAccountId: detail.eftAccountId,
    primaryCardHolderIndicator: detail.primaryCardHolderIndicator,
    accountVersion: detail.accountVersion,
    customerVersion: detail.customerVersion,
  };
}

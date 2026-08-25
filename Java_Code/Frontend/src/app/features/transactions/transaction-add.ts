import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { TransactionAddRequest, TransactionCategoryDto, TransactionTypeDto } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Transaction add. COBOL program {@code COTRN02C}, map {@code COTRN02 / COTRN2A}, transaction
 * {@code CT02}.
 *
 * <p>Key resolution follows the source: when an account is supplied it wins and its
 * cross-reference supplies the card; otherwise the card is resolved to an account; both blank is
 * an error. F5 copies the non-key values from the highest transaction id. Nothing is written until
 * every validation has passed and the confirmation box is ticked.</p>
 */
@Component({
  selector: 'cd-transaction-add',
  imports: [FormsModule, RouterLink, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Add transaction"
      subtitle="Account takes precedence over card when both are supplied"
    />

    <cd-message [text]="message()" [kind]="kind()" />

    <form (ngSubmit)="submit()">
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Key</h2>
          <div class="cd-actions">
            <button type="button" (click)="copyLatest()">
              Copy last transaction
            </button>
            <button type="button" (click)="clear()">
              Clear
            </button>
          </div>
        </div>
        <div class="cd-panel__body">
          <div class="cd-grid">
            <div class="cd-field">
              <label for="accountId">Account ID</label>
              <input
                id="accountId"
                name="accountId"
                class="cd-mono"
                maxlength="11"
                [class.cd-invalid]="field() === 'accountId'"
                [(ngModel)]="model.accountId"
              />
              <span class="cd-field__hint">Wins over the card number when both are entered</span>
            </div>
            <div class="cd-field">
              <label for="cardNumber">Card number</label>
              <input
                id="cardNumber"
                name="cardNumber"
                class="cd-mono"
                maxlength="16"
                [class.cd-invalid]="field() === 'cardNumber'"
                [(ngModel)]="model.cardNumber"
              />
            </div>
          </div>
        </div>
      </div>

      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Transaction</h2>
        </div>
        <div class="cd-panel__body">
          <div class="cd-grid">
            <div class="cd-field">
              <label for="typeCode">Type code</label>
              <select
                id="typeCode"
                name="typeCode"
                [class.cd-invalid]="field() === 'typeCode'"
                [(ngModel)]="model.typeCode"
                (change)="onTypeChange()"
              >
                <option value="">-- select --</option>
                @for (type of types(); track type.typeCode) {
                  <option [value]="type.typeCode">{{ type.typeCode }} &nbsp; {{ type.description }}</option>
                }
              </select>
            </div>
            <div class="cd-field">
              <label for="categoryCode">Category code</label>
              <select
                id="categoryCode"
                name="categoryCode"
                [disabled]="!model.typeCode"
                [class.cd-invalid]="field() === 'categoryCode'"
                [(ngModel)]="model.categoryCode"
              >
                <option value="">
                  {{ model.typeCode ? '-- select --' : '-- choose a type first --' }}
                </option>
                @for (category of categoriesForType(); track category.categoryCode) {
                  <option [value]="category.categoryCode">
                    {{ category.categoryCode }} &nbsp; {{ category.description }}
                  </option>
                }
              </select>
              <span class="cd-field__hint">Categories are keyed by (type, category)</span>
            </div>
            <div class="cd-field">
              <label for="source">Source</label>
              <input
                id="source"
                name="source"
                maxlength="10"
                [class.cd-invalid]="field() === 'source'"
                [(ngModel)]="model.source"
              />
            </div>
            <div class="cd-field">
              <label for="amount">Amount</label>
              <input
                id="amount"
                name="amount"
                class="cd-mono"
                maxlength="12"
                placeholder="-00000000.00"
                [class.cd-invalid]="field() === 'amount'"
                [(ngModel)]="model.amount"
              />
              <span class="cd-field__hint">Signed, exactly two decimals</span>
            </div>
            <div class="cd-field">
              <label for="originDate">Origin date</label>
              <input
                id="originDate"
                name="originDate"
                class="cd-mono"
                maxlength="10"
                placeholder="YYYY-MM-DD"
                [class.cd-invalid]="field() === 'originDate'"
                [(ngModel)]="model.originDate"
              />
            </div>
            <div class="cd-field">
              <label for="processDate">Process date</label>
              <input
                id="processDate"
                name="processDate"
                class="cd-mono"
                maxlength="10"
                placeholder="YYYY-MM-DD"
                [class.cd-invalid]="field() === 'processDate'"
                [(ngModel)]="model.processDate"
              />
            </div>
          </div>

          <div class="cd-field" style="margin-top: 16px">
            <label for="description">Description</label>
            <input
              id="description"
              name="description"
              maxlength="100"
              [class.cd-invalid]="field() === 'description'"
              [(ngModel)]="model.description"
            />
          </div>

          <div class="cd-section-title" style="margin-top: 22px">Merchant</div>
          <div class="cd-grid">
            <div class="cd-field">
              <label for="merchantId">Merchant ID</label>
              <input
                id="merchantId"
                name="merchantId"
                class="cd-mono"
                maxlength="9"
                [class.cd-invalid]="field() === 'merchantId'"
                [(ngModel)]="model.merchantId"
              />
            </div>
            <div class="cd-field">
              <label for="merchantName">Merchant name</label>
              <input
                id="merchantName"
                name="merchantName"
                maxlength="50"
                [class.cd-invalid]="field() === 'merchantName'"
                [(ngModel)]="model.merchantName"
              />
            </div>
            <div class="cd-field">
              <label for="merchantCity">Merchant city</label>
              <input
                id="merchantCity"
                name="merchantCity"
                maxlength="50"
                [class.cd-invalid]="field() === 'merchantCity'"
                [(ngModel)]="model.merchantCity"
              />
            </div>
            <div class="cd-field">
              <label for="merchantZip">Merchant ZIP</label>
              <input
                id="merchantZip"
                name="merchantZip"
                class="cd-mono"
                maxlength="10"
                [class.cd-invalid]="field() === 'merchantZip'"
                [(ngModel)]="model.merchantZip"
              />
            </div>
          </div>

          <label class="cd-checkbox" style="margin-top: 20px">
            <input
              type="checkbox"
              name="confirmed"
              [class.cd-invalid]="field() === 'confirmed'"
              [(ngModel)]="model.confirmed"
            />
            Confirm to add this transaction
          </label>
        </div>

        <div class="cd-pfkeys">
          <button type="submit" class="cd-primary" [disabled]="busy()">
            Add transaction
          </button>
          <a class="cd-btn" routerLink="/transactions">
            Return
          </a>
        </div>
      </div>
    </form>
  `,
})
export class TransactionAddComponent {
  private readonly api = inject(ApiService);

  model: TransactionAddRequest = emptyModel();

  readonly types = signal<TransactionTypeDto[]>([]);
  readonly categories = signal<TransactionCategoryDto[]>([]);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'ok'>('error');
  readonly field = signal<string | null>(null);
  readonly busy = signal(false);

  constructor() {
    this.api.transactionTypes().subscribe({
      next: (rows) => this.types.set(rows),
      error: () => undefined,
    });
    this.api.transactionCategories().subscribe({
      next: (rows) => this.categories.set(rows),
      error: () => undefined,
    });
  }

  /**
   * Categories are keyed by {@code (type, category)}, so the same code means different things
   * under different types: {@code 0001} is a sales draft under 01 and a cash payment under 02.
   * Listing them all before a type is chosen would offer six indistinguishable "0001" rows, so
   * the picker stays empty until the type narrows it.
   */
  categoriesForType(): TransactionCategoryDto[] {
    const type = this.model.typeCode;
    return type ? this.categories().filter((c) => c.typeCode === type) : [];
  }

  onTypeChange(): void {
    const available = this.categoriesForType();
    if (!available.some((c) => c.categoryCode === this.model.categoryCode)) {
      this.model.categoryCode = '';
    }
  }

  /** F5: copy the non-key values from the greatest transaction id. */
  copyLatest(): void {
    this.api.latestTransactionValues().subscribe({
      next: (prefill) => {
        this.model = {
          ...this.model,
          typeCode: prefill.typeCode,
          categoryCode: prefill.categoryCode,
          source: prefill.source,
          description: prefill.description,
          amount: prefill.amount,
          originDate: prefill.originDate,
          processDate: prefill.processDate,
          merchantId: prefill.merchantId,
          merchantName: prefill.merchantName,
          merchantCity: prefill.merchantCity,
          merchantZip: prefill.merchantZip,
        };
        this.kind.set('ok');
        this.message.set('Values copied from the most recent transaction.');
      },
      error: (error: unknown) => {
        this.kind.set('error');
        this.message.set(errorMessage(error));
      },
    });
  }

  /** F4 clears the screen. */
  clear(): void {
    this.model = emptyModel();
    this.message.set(null);
    this.field.set(null);
  }

  submit(): void {
    this.busy.set(true);
    this.message.set(null);
    this.field.set(null);
    this.api.addTransaction(this.model).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.kind.set('ok');
        this.message.set(result.message);
        this.model = emptyModel();
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

function emptyModel(): TransactionAddRequest {
  return {
    accountId: '',
    cardNumber: '',
    typeCode: '',
    categoryCode: '',
    source: '',
    description: '',
    amount: '',
    originDate: '',
    processDate: '',
    merchantId: '',
    merchantName: '',
    merchantCity: '',
    merchantZip: '',
    confirmed: false,
  };
}

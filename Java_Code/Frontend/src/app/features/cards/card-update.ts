import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { CardDetail } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Card update. COBOL program {@code COCRDUPC}, map {@code COCRDUP / CCRDUPA}, transaction
 * {@code CCUP}.
 *
 * <p>Editable fields are the embossed name, the active status and the expiry month and year, in
 * that validation order. The expiry day the BMS map hid is preserved and the complete date is
 * validated before anything is written (FR-CARD-006). The CVV is never touched (FR-CARD-007).</p>
 */
@Component({
  selector: 'cd-card-update',
  imports: [FormsModule, RouterLink, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Update credit card"
      subtitle="Embossed name, active status and expiry month/year"
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

    <cd-message [text]="message()" [kind]="kind()" />

    @if (detail(); as card) {
      <form (ngSubmit)="save()">
        <div class="cd-panel">
          <div class="cd-panel__head">
            <h2>Card {{ card.cardNumber }}</h2>
            <a class="cd-btn cd-small" [routerLink]="['/cards/view', card.cardNumber]">View</a>
          </div>

          <div class="cd-panel__body">
            <div class="cd-grid">
              <div class="cd-field">
                <label>Account</label>
                <div class="cd-value">{{ card.accountId }}</div>
              </div>
              <div class="cd-field">
                <label for="embossedName">Embossed name</label>
                <input
                  id="embossedName"
                  name="embossedName"
                  maxlength="50"
                  [class.cd-invalid]="field() === 'embossedName'"
                  [(ngModel)]="embossedName"
                />
                <span class="cd-field__hint">Letters and spaces only</span>
              </div>
              <div class="cd-field">
                <label for="activeStatus">Active status (Y/N)</label>
                <input
                  id="activeStatus"
                  name="activeStatus"
                  class="cd-mono"
                  maxlength="1"
                  [class.cd-invalid]="field() === 'activeStatus'"
                  [(ngModel)]="activeStatus"
                />
              </div>
              <div class="cd-field">
                <label for="expirationMonth">Expiry month</label>
                <input
                  id="expirationMonth"
                  name="expirationMonth"
                  class="cd-mono"
                  maxlength="2"
                  [class.cd-invalid]="field() === 'expirationMonth'"
                  [(ngModel)]="expirationMonth"
                />
                <span class="cd-field__hint">1 to 12</span>
              </div>
              <div class="cd-field">
                <label for="expirationYear">Expiry year</label>
                <input
                  id="expirationYear"
                  name="expirationYear"
                  class="cd-mono"
                  maxlength="4"
                  [class.cd-invalid]="field() === 'expirationYear'"
                  [(ngModel)]="expirationYear"
                />
                <span class="cd-field__hint">1950 to 2099</span>
              </div>
              <div class="cd-field">
                <label>Expiry day (retained)</label>
                <div class="cd-value">{{ card.expirationDay }}</div>
                <span class="cd-field__hint">
                  Not editable here; kept and validated with the new month and year
                </span>
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
            <a class="cd-btn" routerLink="/cards">
              <span class="cd-pfkey__label">F3</span>Return
            </a>
          </div>
        </div>
      </form>
    }
  `,
})
export class CardUpdateComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  accountId = '';
  cardNumber = '';

  embossedName = '';
  activeStatus = '';
  expirationMonth = '';
  expirationYear = '';

  readonly detail = signal<CardDetail | null>(null);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'ok' | 'info'>('error');
  readonly field = signal<string | null>(null);
  readonly busy = signal(false);

  constructor() {
    const routeCard = this.route.snapshot.paramMap.get('cardNumber');
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
      this.kind.set('error');
      this.message.set('Card number must be supplied.');
      return;
    }
    const account = this.accountId.trim();
    const request$ = account ? this.api.cardForAccount(card, account) : this.api.card(card);
    request$.subscribe({
      next: (detail) => this.accept(detail),
      error: (error: unknown) => {
        this.detail.set(null);
        this.kind.set('error');
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  save(): void {
    const card = this.detail();
    if (!card) {
      return;
    }
    this.busy.set(true);
    this.message.set(null);
    this.field.set(null);
    this.api
      .updateCard(card.cardNumber, {
        accountId: card.accountId,
        cardNumber: card.cardNumber,
        embossedName: this.embossedName,
        activeStatus: this.activeStatus,
        expirationMonth: this.expirationMonth,
        expirationYear: this.expirationYear,
        version: card.version,
      })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.accept(updated);
          this.kind.set('ok');
          this.message.set(`Card ${updated.cardNumber} has been updated ...`);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.kind.set('error');
          this.message.set(errorMessage(error));
          this.field.set(errorField(error));
        },
      });
  }

  /** F12: restore the fetched values. */
  cancel(): void {
    const card = this.detail();
    if (card) {
      this.accept(card);
      this.kind.set('info');
      this.message.set('Changes discarded; the fetched values have been restored.');
    }
  }

  private accept(card: CardDetail): void {
    this.detail.set(card);
    this.cardNumber = card.cardNumber;
    this.accountId = card.accountId;
    this.embossedName = card.embossedName;
    this.activeStatus = card.activeStatus;
    this.expirationMonth = card.expirationMonth;
    this.expirationYear = card.expirationYear;
  }
}

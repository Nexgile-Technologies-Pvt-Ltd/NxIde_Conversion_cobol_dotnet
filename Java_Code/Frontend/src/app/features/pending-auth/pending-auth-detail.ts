import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { PendingAuthDetailView } from '../../core/models';
import { AmountPipe } from '../../shared/amount.pipe';
import { DialogComponent } from '../../shared/dialog';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Authorization detail. COBOL program {@code COPAUS1C}, map {@code COPAU01 / COPAU1A}, transaction
 * {@code CPVD}.
 *
 * <p>The source legend read {@code F3=Back  F5=Mark/Remove Fraud  F8=Next Auth}. The action bar at
 * the foot of the panel carries those three and one more: the record chain runs both ways, so a
 * step backwards is offered even though the map had no key for it.</p>
 *
 * <p>Two corrections to the map. It labelled a field {@code Auth Code:} and moved the processing
 * code into it, never showing the authorization id code at all; both are shown here under their
 * own labels. And it showed the approved amount alone, so the requested amount is shown beside
 * it.</p>
 *
 * <p>F5 toggled the fraud flag on the key press alone, which on a misread screen reported the
 * wrong authorization. The intended state is confirmed here before it is sent, and an optional
 * note travels with it into the audit trail.</p>
 */
@Component({
  selector: 'cd-pending-auth-detail',
  imports: [
    FormsModule,
    RouterLink,
    AmountPipe,
    MessageLineComponent,
    ScreenHeaderComponent,
    DialogComponent,
  ],
  template: `
    <cd-screen-header
      title="Authorization details"
      subtitle="One pending authorization, with the fraud report made an explicit action"
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
            placeholder="Account ID"
            [class.cd-invalid]="field() === 'accountId'"
            [(ngModel)]="accountId"
            aria-label="Account number"
          />
          <input
            name="authKey"
            class="cd-mono"
            style="width: 200px"
            maxlength="14"
            placeholder="Authorization key"
            [class.cd-invalid]="field() === 'authKey'"
            [(ngModel)]="authKey"
            aria-label="Authorization key"
          />
          <button type="submit" class="cd-primary">Fetch</button>
          <button type="button" (click)="clear()">Clear</button>
          <a class="cd-btn" routerLink="/pending-authorizations">Authorization list</a>
        </form>
      </div>
    </div>

    <cd-message [text]="message()" [kind]="kind()" />

    @if (detail(); as auth) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Authorization {{ auth.transactionId }}</h2>
          <span class="cd-inline-note">Card {{ auth.cardNumber }}</span>
        </div>

        <div class="cd-panel__body">
          <div class="cd-section-title">Card and timing</div>
          <div class="cd-grid">
            <div class="cd-field">
              <label>Card number</label>
              <div class="cd-value">
                <a [routerLink]="['/cards/view', auth.cardNumber]">{{ auth.cardNumber }}</a>
              </div>
            </div>
            <div class="cd-field">
              <label>Account</label>
              <div class="cd-value">
                <a [routerLink]="['/accounts/view', auth.accountId]">{{ auth.accountId }}</a>
              </div>
            </div>
            <div class="cd-field">
              <label>Auth date</label>
              <div class="cd-value">{{ auth.authDate }}</div>
            </div>
            <div class="cd-field">
              <label>Auth time</label>
              <div class="cd-value">{{ auth.authTime }}</div>
            </div>
            <div class="cd-field">
              <label>Original date</label>
              <div class="cd-value">{{ auth.authOrigDate }}</div>
            </div>
            <div class="cd-field">
              <label>Original time</label>
              <div class="cd-value">{{ auth.authOrigTime }}</div>
            </div>
          </div>

          <div class="cd-section-title" style="margin-top: 22px">Decision</div>
          <div class="cd-grid">
            <div class="cd-field">
              <label>Auth response</label>
              <div class="cd-value">
                <span
                  class="cd-badge"
                  [class.cd-badge--ok]="auth.authRespCode === '00'"
                  [class.cd-badge--err]="auth.authRespCode !== '00'"
                >
                  {{ auth.authRespText }}
                </span>
              </div>
              <span class="cd-field__hint">Code {{ auth.authRespCode }}</span>
            </div>
            <div class="cd-field">
              <label>Response reason</label>
              <div class="cd-value">{{ auth.authRespReason }} - {{ auth.authRespReasonText }}</div>
            </div>
            <div class="cd-field">
              <label>Auth code</label>
              <div class="cd-value">{{ auth.authIdCode }}</div>
            </div>
            <div class="cd-field">
              <label>Processing code</label>
              <div class="cd-value">{{ auth.processingCode }}</div>
              <span class="cd-field__hint">
                The source screen carried this value under its Auth Code label.
              </span>
            </div>
          </div>

          <div class="cd-section-title" style="margin-top: 22px">Amount and channel</div>
          <div class="cd-grid">
            <div class="cd-field">
              <label>Approved amount</label>
              <div class="cd-value cd-value--num" [class.cd-amount-neg]="auth.approvedAmt < 0">
                {{ auth.approvedAmt | cdAmount }}
              </div>
            </div>
            <div class="cd-field">
              <label>Requested amount</label>
              <div class="cd-value cd-value--num" [class.cd-amount-neg]="auth.transactionAmt < 0">
                {{ auth.transactionAmt | cdAmount }}
              </div>
              <span class="cd-field__hint">
                The source screen showed the approved amount alone.
              </span>
            </div>
            <div class="cd-field">
              <label>POS entry mode</label>
              <div class="cd-value">
                {{ auth.posEntryMode }} &nbsp; {{ auth.posEntryModeText }}
              </div>
            </div>
            <div class="cd-field">
              <label>Source</label>
              <div class="cd-value">{{ auth.messageSource }}</div>
            </div>
            <div class="cd-field">
              <label>Message type</label>
              <div class="cd-value">{{ auth.messageType }}</div>
            </div>
          </div>

          <div class="cd-section-title" style="margin-top: 22px">Codes</div>
          <div class="cd-grid">
            <div class="cd-field">
              <label>MCC code</label>
              <div class="cd-value">{{ auth.mccCode }}</div>
            </div>
            <div class="cd-field">
              <label>Card expiry date</label>
              <div class="cd-value">{{ auth.cardExpiryDate }}</div>
            </div>
            <div class="cd-field">
              <label>Auth type</label>
              <div class="cd-value">{{ auth.authType }}</div>
            </div>
            <div class="cd-field">
              <label>Acquirer country</label>
              <div class="cd-value">{{ auth.acqrCountryCode }}</div>
            </div>
          </div>

          <div class="cd-section-title" style="margin-top: 22px">Status</div>
          <div class="cd-grid">
            <div class="cd-field">
              <label>Transaction ID</label>
              <div class="cd-value">{{ auth.transactionId }}</div>
            </div>
            <div class="cd-field">
              <label>Match status</label>
              <div class="cd-value">{{ auth.matchStatusText }}</div>
              <span class="cd-field__hint">Code {{ auth.matchStatus }}</span>
            </div>
            <div class="cd-field">
              <label>Fraud status</label>
              <div class="cd-value">
                @if (auth.authFraud === 'F') {
                  <span class="cd-badge cd-badge--err">{{ auth.fraudStatusText }}</span>
                  @if (auth.fraudRptDate) {
                    <span class="cd-inline-note">&nbsp; reported {{ auth.fraudRptDate }}</span>
                  }
                } @else {
                  {{ auth.fraudStatusText }}
                }
              </div>
            </div>
          </div>

          <div class="cd-section-title" style="margin-top: 22px">Merchant details</div>
          <div class="cd-grid cd-grid--2">
            <div class="cd-field">
              <label>Name</label>
              <div class="cd-value">{{ auth.merchantName }}</div>
            </div>
            <div class="cd-field">
              <label>Merchant ID</label>
              <div class="cd-value">{{ auth.merchantId }}</div>
            </div>
            <div class="cd-field">
              <label>City</label>
              <div class="cd-value">{{ auth.merchantCity }}</div>
            </div>
            <div class="cd-field">
              <label>State</label>
              <div class="cd-value">{{ auth.merchantState }}</div>
            </div>
            <div class="cd-field">
              <label>ZIP</label>
              <div class="cd-value">{{ auth.merchantZip }}</div>
            </div>
          </div>
        </div>

        <div class="cd-pfkeys">
          <button type="button" class="cd-danger" [disabled]="busy()" (click)="askFraud()">
            {{ auth.authFraud === 'F' ? 'Remove fraud report' : 'Mark as fraud' }}
          </button>
          <button type="button" [disabled]="!auth.previousAuthKey" (click)="previous()">
            Previous authorization
          </button>
          <button type="button" [disabled]="!auth.nextAuthKey" (click)="next()">
            Next authorization
          </button>
          <a
            class="cd-btn"
            [routerLink]="['/pending-authorizations']"
            [queryParams]="{ accountId: auth.accountId }"
          >
            Return to list
          </a>
        </div>
      </div>

      @if (confirming(); as intent) {
        <cd-dialog
          [heading]="intent === 'mark' ? 'Confirm fraud report' : 'Remove fraud report'"
          (closed)="cancelFraud()"
        >
          <p>
            @if (intent === 'mark') {
              Report authorization <strong class="cd-mono">{{ auth.authKey }}</strong> on card
              <strong class="cd-mono">{{ auth.cardNumber }}</strong> as fraud?
            } @else {
              Clear the fraud report from authorization
              <strong class="cd-mono">{{ auth.authKey }}</strong> on card
              <strong class="cd-mono">{{ auth.cardNumber }}</strong>?
            }
          </p>
          <p class="cd-inline-note">
            The source flipped this flag on the key press alone. The state you intend is sent
            explicitly, and the note below is kept with the audit record.
          </p>
          <div class="cd-field" style="margin-top: 14px">
            <label for="note">Note for the audit trail (optional)</label>
            <textarea id="note" name="note" rows="3" maxlength="200" [(ngModel)]="note"></textarea>
          </div>
          <div class="cd-actions" style="margin-top: 14px">
            <button type="button" class="cd-danger" [disabled]="busy()" (click)="applyFraud()">
              {{ intent === 'mark' ? 'Yes, mark as fraud' : 'Yes, remove the report' }}
            </button>
            <button type="button" [disabled]="busy()" (click)="cancelFraud()">Cancel</button>
          </div>
        </cd-dialog>
      }
    }
  `,
})
export class PendingAuthDetailComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  accountId = '';
  authKey = '';
  note = '';

  readonly detail = signal<PendingAuthDetailView | null>(null);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'info' | 'ok' | 'warn'>('error');
  readonly field = signal<string | null>(null);

  /** Which way the fraud flag is about to be moved, and nothing while no answer is being asked. */
  readonly confirming = signal<'mark' | 'remove' | null>(null);
  readonly busy = signal(false);

  constructor() {
    const key = this.route.snapshot.paramMap.get('authKey');
    const account = this.route.snapshot.queryParamMap.get('accountId');
    if (key) {
      this.authKey = key;
    }
    if (account) {
      this.accountId = account;
    }
    if (key && account) {
      this.load(account, key);
    }
  }

  /** ENTER: an account and an authorization key together name one record. */
  fetch(): void {
    this.message.set(null);
    this.field.set(null);
    this.confirming.set(null);
    const account = this.accountId.trim();
    const key = this.authKey.trim();
    if (!account) {
      this.reject('Please enter Acct Id...', 'accountId');
      return;
    }
    if (!key) {
      this.reject('Please select an authorization...', 'authKey');
      return;
    }
    this.load(account, key);
  }

  /** Empties the screen. F3 returns to the authorization list, which is the link beside this. */
  clear(): void {
    this.accountId = '';
    this.authKey = '';
    this.note = '';
    this.detail.set(null);
    this.confirming.set(null);
    this.message.set(null);
    this.field.set(null);
  }

  /** F8 advances to the next authorization under the same account. */
  next(): void {
    const auth = this.detail();
    if (!auth) {
      return;
    }
    if (!auth.nextAuthKey) {
      // The button is already disabled at the end of the chain. The source literal still answers
      // any other route to this action rather than the press appearing to do nothing.
      this.kind.set('info');
      this.message.set('Already at the last Authorization...');
      return;
    }
    this.step(auth.accountId, auth.nextAuthKey);
  }

  /** Steps back one authorization. The source had no backward key; the record chain has one. */
  previous(): void {
    const auth = this.detail();
    if (!auth) {
      return;
    }
    if (!auth.previousAuthKey) {
      this.kind.set('info');
      this.message.set('Already at the first Authorization...');
      return;
    }
    this.step(auth.accountId, auth.previousAuthKey);
  }

  /**
   * F5 marks or removes the fraud report. The source toggled the flag as the key was pressed, so
   * the intent is put to the user first and the request carries it rather than a toggle.
   */
  askFraud(): void {
    const auth = this.detail();
    if (!auth) {
      return;
    }
    this.message.set(null);
    this.field.set(null);
    this.note = '';
    this.confirming.set(auth.authFraud === 'F' ? 'remove' : 'mark');
  }

  /** Sends the confirmed state: marked when the report is being made, cleared when it is lifted. */
  applyFraud(): void {
    const auth = this.detail();
    const intent = this.confirming();
    if (!auth || !intent) {
      return;
    }
    const note = this.note.trim();
    this.busy.set(true);
    this.message.set(null);
    this.field.set(null);
    this.api
      .markAuthorizationFraud(auth.accountId, auth.authKey, {
        confirmed: intent === 'mark',
        note: note || undefined,
      })
      .subscribe({
        next: (result) => {
          this.busy.set(false);
          this.confirming.set(null);
          this.note = '';
          this.detail.set(result.detail);
          this.kind.set('ok');
          this.message.set(result.message);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.confirming.set(null);
          this.kind.set('error');
          this.message.set(errorMessage(error));
          this.field.set(errorField(error));
        },
      });
  }

  /** Leaves the flag as it stands. */
  cancelFraud(): void {
    this.confirming.set(null);
    this.note = '';
  }

  /** Moves along the chain, clearing whatever the previous record left on the message line. */
  private step(accountId: string, authKey: string): void {
    this.message.set(null);
    this.field.set(null);
    this.confirming.set(null);
    this.load(accountId, authKey);
  }

  private load(accountId: string, authKey: string): void {
    this.api.pendingAuthorization(accountId, authKey).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.accountId = detail.accountId;
        this.authKey = detail.authKey;
        this.syncUrl(detail);
      },
      error: (error: unknown) => {
        this.detail.set(null);
        this.kind.set('error');
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  /** Reports a client side edit with the source literal and highlights the offending box. */
  private reject(text: string, field: string): void {
    this.detail.set(null);
    this.kind.set('error');
    this.message.set(text);
    this.field.set(field);
  }

  /**
   * Keeps the address bar on the record being shown, so an authorization reached by stepping along
   * the chain is still linkable. The entry is replaced rather than pushed, which leaves Back
   * pointing at the list the screen was opened from rather than at every record walked through.
   */
  private syncUrl(detail: PendingAuthDetailView): void {
    this.router.navigate(['/pending-authorizations/view', detail.authKey], {
      queryParams: { accountId: detail.accountId },
      replaceUrl: true,
    });
  }
}

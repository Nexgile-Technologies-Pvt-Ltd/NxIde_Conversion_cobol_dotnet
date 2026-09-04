import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
// Aliased: this component already has a `filter` field holding the card number search box.
import { filter as rxFilter } from 'rxjs/operators';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { PageResult, PendingAuthRow, PendingAuthSummaryView } from '../../core/models';
import { AmountPipe } from '../../shared/amount.pipe';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Pending authorization summary. COBOL program {@code COPAUS0C}, map {@code COPAU00 / COPAU0A},
 * transaction {@code CPVS}.
 *
 * <p>One account at a time: its authorization totals above, then the authorizations themselves
 * five to a page, newest first, with an optional card number filter and a fraud-only switch.
 * The account id edits stay in the backend, which answers with the source literals, so nothing
 * beyond an empty box is judged here.</p>
 *
 * <p>The source screen expected an account id to be known before it could show anything at all.
 * The accounts that hold authorizations are listed under the search box instead, so the screen
 * can be reached from the menu by someone who has no id to hand.</p>
 */
@Component({
  selector: 'cd-pending-auth-list',
  imports: [FormsModule, RouterLink, AmountPipe, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Pending authorizations"
      subtitle="One account's authorization totals, then its authorizations five to a page"
    />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Search</h2>
        <form class="cd-actions" (ngSubmit)="search()">
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
            name="filter"
            class="cd-mono"
            style="width: 200px"
            maxlength="16"
            placeholder="Card number"
            [class.cd-invalid]="field() === 'filter'"
            [(ngModel)]="filter"
            aria-label="Card number filter"
          />
          <label class="cd-checkbox">
            <input type="checkbox" name="fraudOnly" [(ngModel)]="fraudOnly" (change)="search()" />
            Fraud only
          </label>
          <button type="submit" class="cd-primary">Search</button>
          <button type="button" (click)="reset()">Clear</button>
        </form>
      </div>

      @if (accounts().length) {
        <div class="cd-panel__body" style="padding-top: 12px; padding-bottom: 12px">
          <span class="cd-field__hint">Accounts with pending authorizations</span>
          <div class="cd-actions" style="margin-top: 8px">
            @for (id of accounts(); track id) {
              <button type="button" class="cd-btn cd-small cd-mono" (click)="pick(id)">
                {{ id }}
              </button>
            }
          </div>
        </div>
      }
    </div>

    <cd-message [text]="message()" [kind]="kind()" />

    @if (summary(); as s) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Account {{ s.accountId }}</h2>
        </div>

        <div class="cd-panel__body">
          <div class="cd-grid">
            <div class="cd-field">
              <label>Customer</label>
              <div class="cd-value">{{ s.customerName }} &nbsp; {{ s.customerId }}</div>
            </div>
            <div class="cd-field">
              <label>Account status</label>
              <div class="cd-value">
                @if (s.accountActiveStatus === 'Y') {
                  <span class="cd-badge cd-badge--ok">Active</span>
                } @else {
                  <span class="cd-badge cd-badge--off">Inactive</span>
                }
              </div>
            </div>
            <div class="cd-field">
              <label>Credit limit</label>
              <div class="cd-value cd-value--num">{{ s.creditLimit | cdAmount }}</div>
            </div>
            <div class="cd-field">
              <label>Cash limit</label>
              <div class="cd-value cd-value--num">{{ s.cashLimit | cdAmount }}</div>
            </div>
            <div class="cd-field">
              <label>Credit balance</label>
              <div class="cd-value cd-value--num">{{ s.creditBalance | cdAmount }}</div>
            </div>
            <div class="cd-field">
              <label>Cash balance</label>
              <div class="cd-value cd-value--num">{{ s.cashBalance | cdAmount }}</div>
            </div>
            <div class="cd-field">
              <label>Approved count</label>
              <div class="cd-value cd-value--num">{{ s.approvedAuthCount }}</div>
            </div>
            <div class="cd-field">
              <label>Approved amount</label>
              <div class="cd-value cd-value--num">{{ s.approvedAuthAmount | cdAmount }}</div>
            </div>
            <div class="cd-field">
              <label>Declined count</label>
              <div class="cd-value cd-value--num">{{ s.declinedAuthCount }}</div>
            </div>
            <div class="cd-field">
              <label>Declined amount</label>
              <div class="cd-value cd-value--num">{{ s.declinedAuthAmount | cdAmount }}</div>
            </div>
            <div class="cd-field">
              <label>Pending</label>
              <div class="cd-value cd-value--num">{{ s.pendingCount }}</div>
            </div>
            <div class="cd-field">
              <label>Fraud reported</label>
              <div class="cd-value">
                @if (s.fraudCount > 0) {
                  <span class="cd-badge cd-badge--err">{{ s.fraudCount }}</span>
                } @else {
                  {{ s.fraudCount }}
                }
              </div>
            </div>
          </div>
        </div>
      </div>
    }

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Authorizations</h2>
        <span class="cd-inline-note">Page {{ page()?.pageNumber ?? 1 }}</span>
      </div>

      <div class="cd-panel__body cd-panel__body--flush">
        <div class="cd-table-wrap">
          <table class="cd-table">
            <thead>
              <tr>
                <th>Auth date</th>
                <th>Auth time</th>
                <th>Card</th>
                <th>Merchant</th>
                <th>Response</th>
                <th>Status</th>
                <th>Fraud</th>
                <th class="cd-num">Amount</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (row of page()?.rows ?? []; track row.authKey) {
                <tr>
                  <td class="cd-mono">{{ row.authDate }}</td>
                  <td class="cd-mono">{{ row.authTime }}</td>
                  <td class="cd-mono">{{ row.cardNumber }}</td>
                  <td>{{ row.merchantName }}</td>
                  <td>
                    <span
                      class="cd-badge"
                      [class.cd-badge--ok]="row.authRespCode === '00'"
                      [class.cd-badge--err]="row.authRespCode !== '00'"
                    >
                      {{ row.authRespText }}
                    </span>
                  </td>
                  <td>{{ row.matchStatusText }}</td>
                  <td>
                    @if (row.authFraud === 'F') {
                      <span class="cd-badge cd-badge--err">{{ row.fraudStatusText }}</span>
                    } @else if (row.fraudStatusText) {
                      <span class="cd-inline-note">{{ row.fraudStatusText }}</span>
                    } @else {
                      <span class="cd-inline-note">&mdash;</span>
                    }
                  </td>
                  <td class="cd-num" [class.cd-amount-neg]="row.transactionAmt < 0">
                    {{ row.transactionAmt | cdAmount }}
                  </td>
                  <td>
                    <a
                      class="cd-btn cd-small"
                      [routerLink]="['/pending-authorizations/view', row.authKey]"
                      [queryParams]="{ accountId: loadedAccountId }"
                    >
                      View
                    </a>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="9" class="cd-empty">No authorizations match this search.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <div class="cd-pfkeys">
        <button type="button" [disabled]="!page()?.hasPrevious" (click)="previous()">
          Previous page
        </button>
        <button type="button" [disabled]="!page()?.hasNext" (click)="next()">
          Next page
        </button>
        <button type="button" (click)="reset()">
          Return
        </button>
      </div>
    </div>
  `,
})
export class PendingAuthListComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  accountId = '';
  filter = '';
  fraudOnly = false;

  /**
   * The account the rows on display belong to, which is the padded form the summary answered with
   * rather than whatever is in the search box. The detail screen reads the account from the query
   * string, so a half-typed box must never reach it.
   */
  loadedAccountId = '';

  readonly accounts = signal<string[]>([]);
  readonly summary = signal<PendingAuthSummaryView | null>(null);
  readonly page = signal<PageResult<PendingAuthRow> | null>(null);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'info'>('error');
  readonly field = signal<string | null>(null);

  private pageNumber = 1;

  /**
   * The navigation that put this screen on display. The outlet builds the component during change
   * detection, which can fall either side of that navigation's {@code NavigationEnd}, so the id is
   * taken from whichever of the two the router can still name. Matching on it means the arrival
   * already handled in the constructor is never loaded a second time, without assuming an ordering.
   */
  private readonly arrivedOn =
    this.router.getCurrentNavigation()?.id ?? this.router.lastSuccessfulNavigation?.id ?? -1;

  constructor() {
    // The router keeps this component alive when it navigates here again, so the constructor runs
    // only on the first arrival. Every later arrival -- an account jump adding ?accountId=, or the
    // sidebar entry returning to the empty screen -- has to be picked up from the event stream, or
    // the screen keeps showing the account the previous visit left on it.
    this.router.events
      .pipe(
        rxFilter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => {
        if (event.id !== this.arrivedOn && this.onPendingAuthList()) {
          this.applyRoute();
        }
      });

    this.api.pendingAuthAccounts().subscribe({
      next: (ids) => this.accounts.set(ids),
      error: () => undefined,
    });

    this.applyRoute();
  }

  /** ENTER: the account edits run in the backend, then the first page of its authorizations. */
  search(): void {
    this.message.set(null);
    this.field.set(null);
    this.pageNumber = 1;
    const id = this.accountId.trim();
    if (!id) {
      this.loadedAccountId = '';
      this.summary.set(null);
      this.page.set(null);
      this.kind.set('error');
      this.message.set('Please enter Acct Id...');
      this.field.set('accountId');
      return;
    }
    this.loadSummary(id);
  }

  /** F3 empties the screen: no account, no filters and nothing listed. */
  reset(): void {
    this.accountId = '';
    this.filter = '';
    this.fraudOnly = false;
    this.loadedAccountId = '';
    this.pageNumber = 1;
    this.summary.set(null);
    this.page.set(null);
    this.message.set(null);
    this.field.set(null);
  }

  /** Choosing one of the listed accounts saves typing an eleven digit id to reach the screen. */
  pick(accountId: string): void {
    this.accountId = accountId;
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

  /** True while this screen is the one on display, so navigations away are not acted on. */
  private onPendingAuthList(): boolean {
    return this.router.url.split('?')[0] === '/pending-authorizations';
  }

  /**
   * Takes the account from the address bar and reloads, so the screen matches the URL. Arriving
   * without one leaves the screen empty rather than guessing at an account.
   */
  private applyRoute(): void {
    const fromRoute = this.route.snapshot.queryParamMap.get('accountId')?.trim() ?? '';
    this.reset();
    if (fromRoute) {
      this.accountId = fromRoute;
      this.search();
    }
  }

  /**
   * The account block, and on success the first page under it. The list is loaded from here so a
   * rejected account id produces one message rather than the same one twice.
   */
  private loadSummary(accountId: string): void {
    this.api.pendingAuthSummary(accountId).subscribe({
      next: (view) => {
        this.summary.set(view);
        this.accountId = view.accountId;
        this.loadedAccountId = view.accountId;
        this.load(null, 'first', 1);
      },
      error: (error: unknown) => {
        this.loadedAccountId = '';
        this.summary.set(null);
        this.page.set(null);
        this.kind.set('error');
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  private load(cursor: string | null, direction: string, page: number): void {
    if (!this.loadedAccountId) {
      return;
    }
    this.message.set(null);
    this.field.set(null);
    this.api
      .pendingAuthorizations({
        accountId: this.loadedAccountId,
        filter: this.filter.trim() || undefined,
        fraudOnly: this.fraudOnly,
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

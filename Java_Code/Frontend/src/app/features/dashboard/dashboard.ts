import { DatePipe, DecimalPipe, KeyValuePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService, errorMessage } from '../../core/api.service';
import { DashboardSummary } from '../../core/models';
import { AmountPipe } from '../../shared/amount.pipe';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Portfolio overview. Every figure is aggregated from PostgreSQL by the backend; nothing on this
 * screen is a constant.
 */
@Component({
  selector: 'cd-dashboard',
  imports: [
    DatePipe,
    DecimalPipe,
    KeyValuePipe,
    RouterLink,
    AmountPipe,
    MessageLineComponent,
    ScreenHeaderComponent,
  ],
  template: `
    <cd-screen-header
      title="Portfolio overview"
      subtitle="Live aggregates across accounts, cards and transactions"
    />

    <cd-message [text]="message()" kind="error" />

    @if (data(); as summary) {
      <div class="cd-stats">
        <div class="cd-stat">
          <div class="cd-stat__label">Accounts</div>
          <div class="cd-stat__value">{{ summary.accountCount | number }}</div>
          <div class="cd-stat__sub">{{ summary.customerCount | number }} customers</div>
        </div>
        <div class="cd-stat">
          <div class="cd-stat__label">Cards</div>
          <div class="cd-stat__value">{{ summary.cardCount | number }}</div>
          <div class="cd-stat__sub">issued and cross-referenced</div>
        </div>
        <div class="cd-stat">
          <div class="cd-stat__label">Transactions</div>
          <div class="cd-stat__value">{{ summary.transactionCount | number }}</div>
          <div class="cd-stat__sub">
            {{ summary.pendingDailyTransactions | number }} daily records awaiting posting
          </div>
        </div>
        <div class="cd-stat">
          <div class="cd-stat__label">Total balance</div>
          <div class="cd-stat__value">{{ summary.totalBalance | cdAmount }}</div>
          <div class="cd-stat__sub">limit {{ summary.totalCreditLimit | cdAmount }}</div>
        </div>
        <div class="cd-stat">
          <div class="cd-stat__label">Security users</div>
          <div class="cd-stat__value">{{ summary.userCount | number }}</div>
          <div class="cd-stat__sub">administrators and regular users</div>
        </div>
      </div>

      <div class="cd-grid cd-grid--2">
        <div class="cd-panel">
          <div class="cd-panel__head">
            <h2>Latest transactions</h2>
            <a class="cd-btn cd-small" routerLink="/transactions">Open list</a>
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
                  @for (row of summary.recentTransactions; track row.transactionId) {
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
                      <td colspan="4" class="cd-empty">No transactions posted yet.</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div class="cd-panel">
          <div class="cd-panel__head">
            <h2>Volume by transaction type</h2>
          </div>
          <div class="cd-panel__body cd-panel__body--flush">
            <div class="cd-table-wrap">
              <table class="cd-table">
                <thead>
                  <tr>
                    <th>Type</th>
                    <th class="cd-num">Transactions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (entry of summary.transactionsByType | keyvalue; track entry.key) {
                    <tr>
                      <td>{{ entry.key }}</td>
                      <td class="cd-num">{{ entry.value | number }}</td>
                    </tr>
                  } @empty {
                    <tr>
                      <td colspan="2" class="cd-empty">No transactions posted yet.</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Recent batch runs</h2>
        </div>
        <div class="cd-panel__body cd-panel__body--flush">
          <div class="cd-table-wrap">
            <table class="cd-table">
              <thead>
                <tr>
                  <th>Job</th>
                  <th>Status</th>
                  <th class="cd-num">RC</th>
                  <th class="cd-num">Read</th>
                  <th class="cd-num">Accepted</th>
                  <th class="cd-num">Rejected</th>
                  <th>Started</th>
                </tr>
              </thead>
              <tbody>
                @for (run of summary.recentBatchRuns; track run.id) {
                  <tr>
                    <td class="cd-mono">{{ run.jobName }}</td>
                    <td>
                      <span
                        class="cd-badge"
                        [class.cd-badge--ok]="run.status === 'COMPLETED'"
                        [class.cd-badge--err]="run.status === 'FAILED'"
                        [class.cd-badge--off]="run.status === 'RUNNING'"
                      >
                        {{ run.status }}
                      </span>
                    </td>
                    <td class="cd-num">{{ run.returnCode }}</td>
                    <td class="cd-num">{{ run.recordsRead | number }}</td>
                    <td class="cd-num">{{ run.recordsAccepted | number }}</td>
                    <td class="cd-num">{{ run.recordsRejected | number }}</td>
                    <td>{{ run.startedAt | date: 'yyyy-MM-dd HH:mm' }}</td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="7" class="cd-empty">No batch job has been run yet.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      </div>
    } @else if (!message()) {
      <div class="cd-panel">
        <div class="cd-empty"><span class="cd-spinner"></span> Loading portfolio ...</div>
      </div>
    }
  `,
})
export class DashboardComponent {
  private readonly api = inject(ApiService);

  readonly data = signal<DashboardSummary | null>(null);
  readonly message = signal<string | null>(null);

  constructor() {
    this.api.dashboard().subscribe({
      next: (summary) => this.data.set(summary),
      error: (error: unknown) => this.message.set(errorMessage(error)),
    });
  }
}

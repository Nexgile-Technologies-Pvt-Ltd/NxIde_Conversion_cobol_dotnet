import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ReportRequestDto, ReportType } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Transaction report request. COBOL program {@code CORPT00C}, map {@code CORPT00 / CORPT0A},
 * transaction {@code CR00}.
 *
 * <p>Monthly produces the current month's first and last dates, yearly produces January 1 to
 * December 31, custom takes the entered dates. Exactly one selector is allowed and the range must
 * be ordered (FR-RPT-002). The confirmed request becomes a durable row consumed by the report job,
 * never generated JCL (FR-RPT-003).</p>
 */
@Component({
  selector: 'cd-report-request',
  imports: [DatePipe, FormsModule, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Transaction reports"
      subtitle="Requests are queued and rendered by the transaction report job"
    />

    <cd-message [text]="message()" [kind]="kind()" />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>New request</h2>
      </div>
      <div class="cd-panel__body">
        <div class="cd-grid cd-grid--tight">
          <div class="cd-field">
            <label for="reportType">Report type</label>
            <select id="reportType" name="reportType" [(ngModel)]="reportType">
              <option value="MONTHLY">Monthly (current month)</option>
              <option value="YEARLY">Yearly (current year)</option>
              <option value="CUSTOM">Custom date range</option>
            </select>
          </div>

          @if (reportType === 'CUSTOM') {
            <div class="cd-field">
              <label for="startDate">Start date</label>
              <input
                id="startDate"
                name="startDate"
                class="cd-mono"
                maxlength="10"
                placeholder="YYYY-MM-DD"
                [class.cd-invalid]="field() === 'startDate'"
                [(ngModel)]="startDate"
              />
            </div>
            <div class="cd-field">
              <label for="endDate">End date</label>
              <input
                id="endDate"
                name="endDate"
                class="cd-mono"
                maxlength="10"
                placeholder="YYYY-MM-DD"
                [class.cd-invalid]="field() === 'endDate'"
                [(ngModel)]="endDate"
              />
            </div>
          }
        </div>

        <label class="cd-checkbox" style="margin-top: 18px">
          <input
            type="checkbox"
            name="confirmed"
            [class.cd-invalid]="field() === 'confirmed'"
            [(ngModel)]="confirmed"
          />
          Confirm to submit this report request
        </label>
      </div>

      <div class="cd-pfkeys">
        <button type="button" class="cd-primary" [disabled]="busy()" (click)="submit()">
          Submit request
        </button>
        <button type="button" (click)="reload()">Refresh list</button>
      </div>
    </div>

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Requests</h2>
        @if (auth.isAdmin()) {
          <label class="cd-checkbox">
            <input type="checkbox" name="all" [(ngModel)]="showAll" (change)="reload()" />
            Show all users
          </label>
        }
      </div>
      <div class="cd-panel__body cd-panel__body--flush">
        <div class="cd-table-wrap">
          <table class="cd-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Type</th>
                <th>Range</th>
                <th>Status</th>
                <th class="cd-num">Lines</th>
                <th>Requested</th>
                <th>By</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (row of requests(); track row.id) {
                <tr>
                  <td class="cd-mono">{{ row.id }}</td>
                  <td>{{ row.reportType }}</td>
                  <td class="cd-mono">{{ row.startDate }} &rarr; {{ row.endDate }}</td>
                  <td>
                    <span
                      class="cd-badge"
                      [class.cd-badge--ok]="row.status === 'COMPLETED'"
                      [class.cd-badge--off]="row.status === 'SUBMITTED'"
                      [class.cd-badge--err]="row.status === 'FAILED'"
                    >
                      {{ row.status }}
                    </span>
                  </td>
                  <td class="cd-num">{{ row.lineCount }}</td>
                  <td>{{ row.requestedAt | date: 'yyyy-MM-dd HH:mm' }}</td>
                  <td class="cd-mono">{{ row.requestedBy }}</td>
                  <td>
                    <div class="cd-row-actions">
                      @if (row.status !== 'COMPLETED') {
                        <button
                          type="button"
                          class="cd-small"
                          [disabled]="generatingId() !== null"
                          (click)="generate(row)"
                        >
                          {{ generatingId() === row.id ? 'Generating ...' : 'Generate' }}
                        </button>
                      } @else {
                        <button type="button" class="cd-small" (click)="open(row)">Open</button>
                      }
                    </div>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="8" class="cd-empty">No report requests yet.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>

    @if (content(); as text) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Report {{ openedId() }}</h2>
          <button type="button" class="cd-small" (click)="content.set(null)">Close</button>
        </div>
        <div class="cd-panel__body">
          <div class="cd-pre">{{ text }}</div>
        </div>
      </div>
    }
  `,
})
export class ReportRequestComponent {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);

  reportType: ReportType = 'MONTHLY';
  startDate = '';
  endDate = '';
  confirmed = false;
  showAll = false;

  readonly requests = signal<ReportRequestDto[]>([]);
  readonly content = signal<string | null>(null);
  readonly openedId = signal<number | null>(null);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'ok' | 'info'>('error');
  readonly field = signal<string | null>(null);
  readonly busy = signal(false);

  /** Rendering a year of transactions takes seconds; without this the job can be fired twice. */
  readonly generatingId = signal<number | null>(null);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.api.reportRequests(this.showAll).subscribe({
      next: (rows) => this.requests.set(rows),
      error: (error: unknown) => {
        this.kind.set('error');
        this.message.set(errorMessage(error));
      },
    });
  }

  submit(): void {
    this.busy.set(true);
    this.message.set(null);
    this.field.set(null);
    this.api
      .submitReport({
        reportType: this.reportType,
        startDate: this.startDate,
        endDate: this.endDate,
        confirmed: this.confirmed,
      })
      .subscribe({
        next: (created) => {
          this.busy.set(false);
          this.kind.set('ok');
          this.message.set(
            `Report request ${created.id} submitted for ${created.startDate} to ${created.endDate}.`,
          );
          this.confirmed = false;
          this.reload();
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.kind.set('error');
          this.message.set(errorMessage(error));
          this.field.set(errorField(error));
        },
      });
  }

  generate(row: ReportRequestDto): void {
    this.message.set(null);
    this.generatingId.set(row.id);
    this.api.generateReport(row.id).subscribe({
      next: () => {
        this.generatingId.set(null);
        this.kind.set('ok');
        this.message.set(`Report ${row.id} generated.`);
        this.reload();
      },
      error: (error: unknown) => {
        this.generatingId.set(null);
        this.kind.set('error');
        this.message.set(errorMessage(error));
      },
    });
  }

  open(row: ReportRequestDto): void {
    this.api.reportContent(row.id).subscribe({
      next: (text) => {
        this.openedId.set(row.id);
        this.content.set(text);
      },
      error: (error: unknown) => {
        this.kind.set('error');
        this.message.set(errorMessage(error));
      },
    });
  }
}

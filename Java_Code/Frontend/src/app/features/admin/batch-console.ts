import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { BatchRejectDto, BatchRunDto, MigrationLogDto } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Batch operations console. Each button is one legacy job:
 * {@code POSTTRAN} ({@code CBTRN02C}), {@code INTCALC} ({@code CBACT04C}),
 * {@code TRANREPT} ({@code CBTRN03C}) and {@code CREASTMT} ({@code CBSTM03A}).
 *
 * <p>The run table replaces reading JES output: it carries the completion code, the record counts
 * and the reject list a 430-byte {@code DALYREJS} generation used to hold.</p>
 */
@Component({
  selector: 'cd-batch-console',
  imports: [DatePipe, DecimalPipe, FormsModule, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      tran="----"
      program="Batch console"
      title="Batch operations"
      subtitle="POSTTRAN, INTCALC, TRANREPT and CREASTMT job equivalents"
      origin="CBTRN02C / CBACT04C / CBTRN03C / CBSTM03A"
    />

    <cd-message [text]="message()" [kind]="kind()" />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Run a job</h2>
        <span class="cd-inline-note">
          {{ pending() | number }} daily transaction(s) awaiting posting
        </span>
      </div>
      <div class="cd-panel__body">
        <div class="cd-grid cd-grid--2">
          <div class="cd-field">
            <label>POSTTRAN &middot; post daily transactions</label>
            <p class="cd-inline-note">
              Validates card, account, credit limit and expiry in source order, then applies the
              category balance, account and transaction master changes per record.
            </p>
            <div class="cd-actions">
              <button type="button" class="cd-primary" [disabled]="busy()" (click)="runPosting()">
                Run POSTTRAN
              </button>
            </div>
          </div>

          <div class="cd-field">
            <label>INTCALC &middot; monthly interest</label>
            <p class="cd-inline-note">
              Balance times annual rate divided by 1200 per category, one system transaction each,
              then the account balance is updated and both cycle accumulators are reset.
            </p>
            <div class="cd-actions">
              <input
                name="cycleId"
                class="cd-mono"
                style="width: 150px"
                maxlength="10"
                placeholder="2022071800"
                [class.cd-invalid]="field() === 'cycleId'"
                [(ngModel)]="cycleId"
                aria-label="Cycle ID"
              />
              <button type="button" class="cd-primary" [disabled]="busy()" (click)="runInterest()">
                Run INTCALC
              </button>
            </div>
          </div>

          <div class="cd-field">
            <label>TRANREPT &middot; transaction report</label>
            <p class="cd-inline-note">
              Renders every submitted report request as fixed 133-column output with page, card and
              grand totals.
            </p>
            <div class="cd-actions">
              <button type="button" [disabled]="busy()" (click)="runReports()">Run TRANREPT</button>
            </div>
          </div>

          <div class="cd-field">
            <label>CREASTMT &middot; card statements</label>
            <p class="cd-inline-note">
              Produces the 80-column text statement and an escaped HTML document for every
              cross-referenced card.
            </p>
            <div class="cd-actions">
              <button type="button" [disabled]="busy()" (click)="runStatements()">Run CREASTMT</button>
            </div>
          </div>
        </div>

        @if (busy()) {
          <div class="cd-message cd-message--info" style="margin-top: 16px">
            <span class="cd-spinner"></span>
            <span>Job running. Large jobs take a while against a remote database.</span>
          </div>
        }
      </div>
    </div>

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Run history</h2>
        <button type="button" class="cd-small" (click)="reload()">Refresh</button>
      </div>
      <div class="cd-panel__body cd-panel__body--flush">
        <div class="cd-table-wrap">
          <table class="cd-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Job</th>
                <th>Status</th>
                <th class="cd-num">RC</th>
                <th class="cd-num">Read</th>
                <th class="cd-num">Accepted</th>
                <th class="cd-num">Rejected</th>
                <th>Message</th>
                <th>Started</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (run of runs(); track run.id) {
                <tr>
                  <td class="cd-mono">{{ run.id }}</td>
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
                  <td>{{ run.message }}</td>
                  <td>{{ run.startedAt | date: 'yyyy-MM-dd HH:mm' }}</td>
                  <td>
                    @if (run.recordsRejected > 0) {
                      <button type="button" class="cd-small" (click)="showRejects(run)">
                        Rejects
                      </button>
                    }
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="10" class="cd-empty">No batch job has been run yet.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>

    @if (rejects().length) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Rejected records &middot; run {{ rejectRunId() }}</h2>
          <button type="button" class="cd-small" (click)="rejects.set([])">Close</button>
        </div>
        <div class="cd-panel__body cd-panel__body--flush">
          <div class="cd-table-wrap">
            <table class="cd-table">
              <thead>
                <tr>
                  <th class="cd-num">Record</th>
                  <th>Transaction ID</th>
                  <th>Reason</th>
                  <th>Description</th>
                </tr>
              </thead>
              <tbody>
                @for (row of rejects(); track row.recordNumber) {
                  <tr>
                    <td class="cd-num">{{ row.recordNumber }}</td>
                    <td class="cd-mono">{{ row.transactionId }}</td>
                    <td class="cd-mono">{{ row.reasonCode }}</td>
                    <td>{{ row.reasonText }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      </div>
    }

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>COBOL data migration</h2>
        <div class="cd-actions">
          <button type="button" class="cd-small" (click)="loadMigration()">Refresh</button>
          <button type="button" class="cd-small" (click)="runMigration()">Re-run migration</button>
        </div>
      </div>
      <div class="cd-panel__body cd-panel__body--flush">
        <div class="cd-table-wrap">
          <table class="cd-table">
            <thead>
              <tr>
                <th>Source file</th>
                <th>Entity</th>
                <th>Codec</th>
                <th class="cd-num">Read</th>
                <th class="cd-num">Loaded</th>
                <th class="cd-num">Failed</th>
                <th>Detail</th>
                <th>Executed</th>
              </tr>
            </thead>
            <tbody>
              @for (row of migration(); track row.sourceFile + row.executedAt) {
                <tr>
                  <td class="cd-mono">{{ row.sourceFile }}</td>
                  <td>{{ row.entity }}</td>
                  <td class="cd-mono">{{ row.codec }}</td>
                  <td class="cd-num">{{ row.recordsRead | number }}</td>
                  <td class="cd-num">{{ row.recordsLoaded | number }}</td>
                  <td class="cd-num">{{ row.recordsFailed | number }}</td>
                  <td>{{ row.detail }}</td>
                  <td>{{ row.executedAt | date: 'yyyy-MM-dd HH:mm' }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="8" class="cd-empty">No migration has been recorded.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
})
export class BatchConsoleComponent {
  private readonly api = inject(ApiService);

  cycleId = '';

  readonly runs = signal<BatchRunDto[]>([]);
  readonly rejects = signal<BatchRejectDto[]>([]);
  readonly rejectRunId = signal<number | null>(null);
  readonly migration = signal<MigrationLogDto[]>([]);
  readonly pending = signal(0);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'ok' | 'info'>('info');
  readonly field = signal<string | null>(null);
  readonly busy = signal(false);

  constructor() {
    this.reload();
    this.loadMigration();
  }

  reload(): void {
    this.api.batchRuns(50).subscribe({
      next: (rows) => this.runs.set(rows),
      error: (error: unknown) => this.fail(error),
    });
    this.api.pendingPostings().subscribe({
      next: (result) => this.pending.set(result.pending),
      error: () => undefined,
    });
  }

  loadMigration(): void {
    this.api.migrationLog().subscribe({
      next: (rows) => this.migration.set(rows),
      error: () => undefined,
    });
  }

  runPosting(): void {
    this.start('POSTTRAN');
    this.api.runPosting().subscribe({
      next: (run) => this.finish(run),
      error: (error: unknown) => this.fail(error),
    });
  }

  runInterest(): void {
    this.start('INTCALC');
    this.api.runInterest(this.cycleId).subscribe({
      next: (run) => this.finish(run),
      error: (error: unknown) => this.fail(error),
    });
  }

  runReports(): void {
    this.start('TRANREPT');
    this.api.runReports().subscribe({
      next: (run) => this.finish(run),
      error: (error: unknown) => this.fail(error),
    });
  }

  runStatements(): void {
    this.start('CREASTMT');
    this.api.runStatements().subscribe({
      next: (run) => this.finish(run),
      error: (error: unknown) => this.fail(error),
    });
  }

  runMigration(): void {
    this.start('MIGRATION');
    this.api.runMigration().subscribe({
      next: (rows) => {
        this.busy.set(false);
        this.migration.set(rows);
        this.kind.set('ok');
        this.message.set('COBOL data migration completed.');
      },
      error: (error: unknown) => this.fail(error),
    });
  }

  showRejects(run: BatchRunDto): void {
    this.rejectRunId.set(run.id);
    this.api.batchRejects(run.id).subscribe({
      next: (rows) => this.rejects.set(rows),
      error: (error: unknown) => this.fail(error),
    });
  }

  private start(job: string): void {
    this.busy.set(true);
    this.field.set(null);
    this.kind.set('info');
    this.message.set(`${job} started ...`);
  }

  private finish(run: BatchRunDto): void {
    this.busy.set(false);
    this.kind.set(run.returnCode === 0 ? 'ok' : 'info');
    this.message.set(
      `${run.jobName} finished with return code ${run.returnCode}. ${run.message ?? ''}`,
    );
    this.reload();
  }

  private fail(error: unknown): void {
    this.busy.set(false);
    this.kind.set('error');
    this.message.set(errorMessage(error));
    this.field.set(errorField(error));
  }
}

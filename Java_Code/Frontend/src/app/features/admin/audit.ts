import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';

import { ApiService, errorMessage } from '../../core/api.service';
import { AuditEventDto } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Audit trail (FR-USER-007). Every sign-on, privileged mutation and batch run is recorded with
 * actor, target, time and outcome. No password, CVV, SSN, government id or full EFT id ever
 * reaches an audit row.
 */
@Component({
  selector: 'cd-audit',
  imports: [DatePipe, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Audit trail"
      subtitle="Actor, action, target, outcome and time for every privileged operation"
    />

    <cd-message [text]="message()" kind="error" />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Recent events</h2>
        <button type="button" class="cd-small" (click)="reload()">Refresh</button>
      </div>
      <div class="cd-panel__body cd-panel__body--flush">
        <div class="cd-table-wrap">
          <table class="cd-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Actor</th>
                <th>Action</th>
                <th>Target</th>
                <th>Outcome</th>
                <th>Detail</th>
              </tr>
            </thead>
            <tbody>
              @for (row of events(); track row.id) {
                <tr>
                  <td>{{ row.createdAt | date: 'yyyy-MM-dd HH:mm:ss' }}</td>
                  <td class="cd-mono">{{ row.actor }}</td>
                  <td class="cd-mono">{{ row.action }}</td>
                  <td class="cd-mono">
                    {{ row.targetType }}@if (row.targetId) {
                      &nbsp;{{ row.targetId }}
                    }
                  </td>
                  <td>
                    <span
                      class="cd-badge"
                      [class.cd-badge--ok]="row.outcome === 'SUCCESS'"
                      [class.cd-badge--err]="row.outcome !== 'SUCCESS'"
                    >
                      {{ row.outcome }}
                    </span>
                  </td>
                  <td>{{ row.detail }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="6" class="cd-empty">No audit events recorded.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
})
export class AuditComponent {
  private readonly api = inject(ApiService);

  readonly events = signal<AuditEventDto[]>([]);
  readonly message = signal<string | null>(null);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.api.auditEvents(200).subscribe({
      next: (rows) => this.events.set(rows),
      error: (error: unknown) => this.message.set(errorMessage(error)),
    });
  }
}

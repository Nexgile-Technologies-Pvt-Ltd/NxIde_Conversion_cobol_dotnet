import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService, errorMessage } from '../../core/api.service';
import { StatementDto } from '../../core/models';
import { AmountPipe } from '../../shared/amount.pipe';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Generated card statements. COBOL program {@code CBSTM03A}, job {@code CREASTMT}.
 *
 * <p>Both the fixed 80-column text form and the HTML document are stored and can be opened here.
 * The HTML is escaped, which the source never did.</p>
 */
@Component({
  selector: 'cd-statements',
  imports: [DatePipe, RouterLink, AmountPipe, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      tran="----"
      program="CBSTM03A"
      title="Card statements"
      subtitle="Fixed 80-column text and escaped HTML, one statement per cross-referenced card"
      origin="CBSTM03A.CBL / CREASTMT.JCL"
    />

    <cd-message [text]="message()" kind="error" />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Statements</h2>
        <span class="cd-inline-note">{{ statements().length }} generated</span>
      </div>
      <div class="cd-panel__body cd-panel__body--flush">
        <div class="cd-table-wrap">
          <table class="cd-table">
            <thead>
              <tr>
                <th>Card</th>
                <th>Account</th>
                <th>Customer</th>
                <th class="cd-num">Transactions</th>
                <th class="cd-num">Total</th>
                <th>Generated</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (row of statements(); track row.id) {
                <tr>
                  <td class="cd-mono">
                    <a [routerLink]="['/cards/view', row.cardNumber]">{{ row.cardNumber }}</a>
                  </td>
                  <td class="cd-mono">
                    <a [routerLink]="['/accounts/view', row.accountId]">{{ row.accountId }}</a>
                  </td>
                  <td class="cd-mono">{{ row.customerId }}</td>
                  <td class="cd-num">{{ row.tranCount }}</td>
                  <td class="cd-num" [class.cd-amount-neg]="row.totalAmount < 0">
                    {{ row.totalAmount | cdAmount }}
                  </td>
                  <td>{{ row.generatedAt | date: 'yyyy-MM-dd HH:mm' }}</td>
                  <td>
                    <div class="cd-row-actions">
                      <button type="button" class="cd-small" (click)="openText(row)">Text</button>
                      <button type="button" class="cd-small" (click)="openHtml(row)">HTML</button>
                    </div>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="7" class="cd-empty">
                    No statements yet. Run the CREASTMT job from the batch console.
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>

    @if (textContent(); as text) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Statement text</h2>
          <button type="button" class="cd-small" (click)="textContent.set(null)">Close</button>
        </div>
        <div class="cd-panel__body">
          <div class="cd-pre">{{ text }}</div>
        </div>
      </div>
    }

    @if (htmlContent(); as html) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Statement HTML source</h2>
          <button type="button" class="cd-small" (click)="htmlContent.set(null)">Close</button>
        </div>
        <div class="cd-panel__body">
          <div class="cd-pre">{{ html }}</div>
        </div>
      </div>
    }
  `,
})
export class StatementsComponent {
  private readonly api = inject(ApiService);

  readonly statements = signal<StatementDto[]>([]);
  readonly textContent = signal<string | null>(null);
  readonly htmlContent = signal<string | null>(null);
  readonly message = signal<string | null>(null);

  constructor() {
    this.api.statements(200).subscribe({
      next: (rows) => this.statements.set(rows),
      error: (error: unknown) => this.message.set(errorMessage(error)),
    });
  }

  openText(row: StatementDto): void {
    this.htmlContent.set(null);
    this.api.statementText(row.id).subscribe({
      next: (text) => this.textContent.set(text),
      error: (error: unknown) => this.message.set(errorMessage(error)),
    });
  }

  /**
   * The HTML body is shown as source rather than rendered: it is generated content and displaying
   * it as markup would defeat the escaping the generator applies.
   */
  openHtml(row: StatementDto): void {
    this.textContent.set(null);
    this.api.statementHtml(row.id).subscribe({
      next: (html) => this.htmlContent.set(html),
      error: (error: unknown) => this.message.set(errorMessage(error)),
    });
  }
}

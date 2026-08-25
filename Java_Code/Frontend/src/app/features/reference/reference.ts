import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, errorMessage } from '../../core/api.service';
import {
  CategoryBalanceDto,
  DisclosureGroupDto,
  TransactionCategoryDto,
  TransactionTypeDto,
} from '../../core/models';
import { AmountPipe } from '../../shared/amount.pipe';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Reference data browser: transaction types ({@code TRANTYPE}), categories ({@code TRANCATG}),
 * disclosure rates ({@code DISCGRP}) and category balances ({@code TCATBALF}).
 *
 * <p>All four are migrated COBOL data sets, not application constants.</p>
 */
@Component({
  selector: 'cd-reference',
  imports: [FormsModule, AmountPipe, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Reference data"
      subtitle="Transaction types, categories, disclosure groups and category balances"
    />

    <cd-message [text]="message()" kind="error" />

    <div class="cd-grid cd-grid--2">
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Transaction types</h2>
          <span class="cd-inline-note">{{ types().length }} rows &middot; TRANTYPE</span>
        </div>
        <div class="cd-panel__body cd-panel__body--flush">
          <div class="cd-table-wrap">
            <table class="cd-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Description</th>
                </tr>
              </thead>
              <tbody>
                @for (row of types(); track row.typeCode) {
                  <tr>
                    <td class="cd-mono">{{ row.typeCode }}</td>
                    <td>{{ row.description }}</td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="2" class="cd-empty">No transaction types loaded.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Transaction categories</h2>
          <span class="cd-inline-note">{{ categories().length }} rows &middot; TRANCATG</span>
        </div>
        <div class="cd-panel__body cd-panel__body--flush">
          <div class="cd-table-wrap">
            <table class="cd-table">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Category</th>
                  <th>Description</th>
                </tr>
              </thead>
              <tbody>
                @for (row of categories(); track row.typeCode + row.categoryCode) {
                  <tr>
                    <td class="cd-mono">{{ row.typeCode }}</td>
                    <td class="cd-mono">{{ row.categoryCode }}</td>
                    <td>{{ row.description }}</td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="3" class="cd-empty">No transaction categories loaded.</td>
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
        <h2>Disclosure interest rates</h2>
        <span class="cd-inline-note">
          {{ rates().length }} rows &middot; DISCGRP &middot; group DEFAULT is the fallback
        </span>
      </div>
      <div class="cd-panel__body cd-panel__body--flush">
        <div class="cd-table-wrap">
          <table class="cd-table">
            <thead>
              <tr>
                <th>Group</th>
                <th>Type</th>
                <th>Category</th>
                <th class="cd-num">Annual rate %</th>
                <th class="cd-num">Monthly divisor</th>
              </tr>
            </thead>
            <tbody>
              @for (row of rates(); track row.groupId + row.typeCode + row.categoryCode) {
                <tr>
                  <td class="cd-mono">{{ row.groupId }}</td>
                  <td class="cd-mono">{{ row.typeCode }}</td>
                  <td class="cd-mono">{{ row.categoryCode }}</td>
                  <td class="cd-num">{{ row.interestRate | cdAmount }}</td>
                  <td class="cd-num">1200</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" class="cd-empty">No disclosure rates loaded.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Category balances</h2>
        <form class="cd-actions" (ngSubmit)="loadBalances()">
          <input
            name="accountFilter"
            class="cd-mono"
            style="width: 170px"
            maxlength="11"
            placeholder="Account (optional)"
            [(ngModel)]="accountFilter"
            aria-label="Account filter"
          />
          <button type="submit">Filter</button>
        </form>
      </div>
      <div class="cd-panel__body cd-panel__body--flush">
        <div class="cd-table-wrap">
          <table class="cd-table">
            <thead>
              <tr>
                <th>Account</th>
                <th>Type</th>
                <th>Category</th>
                <th class="cd-num">Balance</th>
              </tr>
            </thead>
            <tbody>
              @for (row of balances(); track row.accountId + row.typeCode + row.categoryCode) {
                <tr>
                  <td class="cd-mono">{{ row.accountId }}</td>
                  <td class="cd-mono">{{ row.typeCode }}</td>
                  <td class="cd-mono">{{ row.categoryCode }}</td>
                  <td class="cd-num" [class.cd-amount-neg]="row.balance < 0">
                    {{ row.balance | cdAmount }}
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="4" class="cd-empty">No category balances found.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
})
export class ReferenceComponent {
  private readonly api = inject(ApiService);

  accountFilter = '';

  readonly types = signal<TransactionTypeDto[]>([]);
  readonly categories = signal<TransactionCategoryDto[]>([]);
  readonly rates = signal<DisclosureGroupDto[]>([]);
  readonly balances = signal<CategoryBalanceDto[]>([]);
  readonly message = signal<string | null>(null);

  constructor() {
    this.api.transactionTypes().subscribe({
      next: (rows) => this.types.set(rows),
      error: (error: unknown) => this.message.set(errorMessage(error)),
    });
    this.api.transactionCategories().subscribe({
      next: (rows) => this.categories.set(rows),
      error: () => undefined,
    });
    this.api.disclosureGroups().subscribe({
      next: (rows) => this.rates.set(rows),
      error: () => undefined,
    });
    this.loadBalances();
  }

  loadBalances(): void {
    this.api.categoryBalances(this.accountFilter.trim() || undefined).subscribe({
      next: (rows) => this.balances.set(rows),
      error: (error: unknown) => this.message.set(errorMessage(error)),
    });
  }
}

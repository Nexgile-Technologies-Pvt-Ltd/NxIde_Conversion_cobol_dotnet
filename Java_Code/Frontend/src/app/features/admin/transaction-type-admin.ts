import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService, errorField, errorMessage } from '../../core/api.service';
import { PageResult, TransactionCategoryDto, TransactionTypeDto } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Transaction type list and maintenance, the optional Db2 module. COBOL programs
 * {@code COTRTLIC} (list {@code CTLI}, seven rows, type and description filters) and
 * {@code COTRTUPC} (maintenance {@code CTTU}, create or update selected by existence).
 *
 * <p>The type must be a two digit non-zero number and the description must be non-blank, at most
 * fifty characters and limited to letters, digits and spaces (FR-OPT-002).</p>
 */
@Component({
  selector: 'cd-transaction-type-admin',
  imports: [FormsModule, RouterLink, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Transaction type maintenance"
      subtitle="Seven rows per page; F2 opens maintenance, F10 confirms a change"
    />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Filters</h2>
        <form class="cd-actions" (ngSubmit)="search()">
          <input
            name="typeFilter"
            class="cd-mono"
            style="width: 110px"
            maxlength="2"
            placeholder="Type"
            [class.cd-invalid]="field() === 'typeFilter'"
            [(ngModel)]="typeFilter"
            aria-label="Type filter"
          />
          <input
            name="descriptionFilter"
            style="width: 240px"
            maxlength="50"
            placeholder="Description contains"
            [(ngModel)]="descriptionFilter"
            aria-label="Description filter"
          />
          <button type="submit" class="cd-primary">Search</button>
          <button type="button" (click)="reset()">Clear</button>
        </form>
      </div>
    </div>

    <cd-message [text]="message()" [kind]="kind()" />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Transaction types</h2>
        <span class="cd-inline-note">Page {{ page()?.pageNumber ?? 1 }}</span>
      </div>

      <div class="cd-panel__body cd-panel__body--flush">
        <div class="cd-table-wrap">
          <table class="cd-table">
            <thead>
              <tr>
                <th>Type</th>
                <th>Description</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (row of page()?.rows ?? []; track row.typeCode) {
                <tr>
                  <td class="cd-mono">{{ row.typeCode }}</td>
                  <td>{{ row.description }}</td>
                  <td>
                    <div class="cd-row-actions">
                      <button type="button" class="cd-small" (click)="edit(row)">Update</button>
                      <button type="button" class="cd-small cd-danger" (click)="askDelete(row)">
                        Delete
                      </button>
                      <button type="button" class="cd-small" (click)="loadCategories(row.typeCode)">
                        Categories
                      </button>
                    </div>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="3" class="cd-empty">No records found for this search condition.</td>
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
        <button type="button" (click)="startNew()">
          New type
        </button>
        <a class="cd-btn" routerLink="/admin-menu">
          Admin menu
        </a>
      </div>
    </div>

    @if (editing()) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>{{ isNew() ? 'Create transaction type' : 'Update transaction type' }}</h2>
        </div>
        <form (ngSubmit)="save()">
          <div class="cd-panel__body">
            <div class="cd-grid">
              <div class="cd-field">
                <label for="typeCode">Type code</label>
                <input
                  id="typeCode"
                  name="typeCode"
                  class="cd-mono"
                  maxlength="2"
                  [readonly]="!isNew()"
                  [class.cd-invalid]="field() === 'typeCode'"
                  [(ngModel)]="editTypeCode"
                />
                <span class="cd-field__hint">Two digits, non-zero</span>
              </div>
              <div class="cd-field">
                <label for="description">Description</label>
                <input
                  id="description"
                  name="description"
                  maxlength="50"
                  [class.cd-invalid]="field() === 'description'"
                  [(ngModel)]="editDescription"
                />
                <span class="cd-field__hint">Letters, digits and spaces, at most 50 characters</span>
              </div>
            </div>
          </div>
          <div class="cd-pfkeys">
            <button type="submit" class="cd-primary" [disabled]="busy()">
              Confirm
            </button>
            <button type="button" (click)="editing.set(false)">Cancel</button>
          </div>
        </form>
      </div>
    }

    @if (pendingDelete(); as target) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Confirm deletion</h2>
        </div>
        <div class="cd-panel__body">
          <p>
            Delete transaction type <strong class="cd-mono">{{ target.typeCode }}</strong>
            ({{ target.description }})?
          </p>
          <div class="cd-actions" style="margin-top: 14px">
            <button type="button" class="cd-danger" (click)="doDelete(target)">Yes, delete</button>
            <button type="button" (click)="pendingDelete.set(null)">Cancel</button>
          </div>
        </div>
      </div>
    }

    @if (pendingCategoryDelete(); as target) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Confirm deletion</h2>
        </div>
        <div class="cd-panel__body">
          <p>
            Delete category
            <strong class="cd-mono">{{ target.typeCode }}/{{ target.categoryCode }}</strong>
            ({{ target.description }})?
          </p>
          <div class="cd-actions" style="margin-top: 14px">
            <button type="button" class="cd-danger" (click)="doDeleteCategory(target)">
              Yes, delete
            </button>
            <button type="button" (click)="pendingCategoryDelete.set(null)">Cancel</button>
          </div>
        </div>
      </div>
    }

    @if (categories().length) {
      <div class="cd-panel">
        <div class="cd-panel__head">
          <h2>Categories of type {{ categoryType() }}</h2>
          <button type="button" class="cd-small" (click)="categories.set([])">Close</button>
        </div>
        <div class="cd-panel__body cd-panel__body--flush">
          <div class="cd-table-wrap">
            <table class="cd-table">
              <thead>
                <tr>
                  <th>Category</th>
                  <th>Description</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (row of categories(); track row.categoryCode) {
                  <tr>
                    <td class="cd-mono">{{ row.categoryCode }}</td>
                    <td>
                      <input
                        class="cd-mono"
                        maxlength="50"
                        [name]="'cat-' + row.categoryCode"
                        [(ngModel)]="row.description"
                      />
                    </td>
                    <td>
                      <div class="cd-row-actions">
                        <button type="button" class="cd-small" (click)="saveCategory(row)">Save</button>
                        <button
                          type="button"
                          class="cd-small cd-danger"
                          (click)="pendingCategoryDelete.set(row)"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      </div>
    }
  `,
})
export class TransactionTypeAdminComponent {
  private readonly api = inject(ApiService);

  typeFilter = '';
  descriptionFilter = '';

  editTypeCode = '';
  editDescription = '';
  private editVersion = 0;

  readonly page = signal<PageResult<TransactionTypeDto> | null>(null);
  readonly editing = signal(false);
  readonly isNew = signal(false);
  readonly pendingDelete = signal<TransactionTypeDto | null>(null);
  readonly pendingCategoryDelete = signal<TransactionCategoryDto | null>(null);
  readonly categories = signal<TransactionCategoryDto[]>([]);
  readonly categoryType = signal('');
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'ok' | 'info'>('error');
  readonly field = signal<string | null>(null);
  readonly busy = signal(false);

  private pageNumber = 1;

  constructor() {
    this.load(null, 'first', 1);
  }

  search(): void {
    this.pageNumber = 1;
    this.load(null, 'first', 1);
  }

  reset(): void {
    this.typeFilter = '';
    this.descriptionFilter = '';
    this.search();
  }

  next(): void {
    const current = this.page();
    if (current?.hasNext && current.lastKey) {
      this.pageNumber += 1;
      this.load(current.lastKey, 'next', this.pageNumber);
    }
  }

  previous(): void {
    const current = this.page();
    if (current?.hasPrevious && current.firstKey) {
      this.pageNumber = Math.max(1, this.pageNumber - 1);
      this.load(current.firstKey, 'prev', this.pageNumber);
    }
  }

  startNew(): void {
    this.editing.set(true);
    this.isNew.set(true);
    this.editTypeCode = '';
    this.editDescription = '';
    this.editVersion = 0;
    this.field.set(null);
  }

  edit(row: TransactionTypeDto): void {
    this.editing.set(true);
    this.isNew.set(false);
    this.editTypeCode = row.typeCode;
    this.editDescription = row.description;
    this.editVersion = row.version;
    this.field.set(null);
  }

  save(): void {
    this.busy.set(true);
    this.message.set(null);
    this.field.set(null);
    this.api.saveTransactionType(this.editTypeCode, this.editDescription, this.editVersion).subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.editing.set(false);
        this.kind.set('ok');
        this.message.set(`Transaction type ${saved.typeCode} has been saved ...`);
        this.search();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.kind.set('error');
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  askDelete(row: TransactionTypeDto): void {
    this.message.set(null);
    this.pendingDelete.set(row);
  }

  doDelete(row: TransactionTypeDto): void {
    this.api.deleteTransactionType(row.typeCode).subscribe({
      next: (result) => {
        this.pendingDelete.set(null);
        this.kind.set('ok');
        this.message.set(result.message);
        this.search();
      },
      error: (error: unknown) => {
        this.pendingDelete.set(null);
        this.kind.set('error');
        this.message.set(errorMessage(error));
      },
    });
  }

  loadCategories(typeCode: string): void {
    this.categoryType.set(typeCode);
    this.api.categoriesOfType(typeCode).subscribe({
      next: (rows) => this.categories.set(rows),
      error: (error: unknown) => {
        this.kind.set('error');
        this.message.set(errorMessage(error));
      },
    });
  }

  /**
   * Remove a category. Without this a category added in error could never be taken away, and the
   * type owning it could never be deleted either, because that delete refuses while categories
   * remain.
   */
  doDeleteCategory(row: TransactionCategoryDto): void {
    this.pendingCategoryDelete.set(null);
    this.api.deleteTransactionCategory(row.typeCode, row.categoryCode).subscribe({
      next: (result) => {
        this.kind.set('ok');
        this.message.set(result.message);
        this.loadCategories(row.typeCode);
      },
      error: (error: unknown) => {
        this.kind.set('error');
        this.message.set(errorMessage(error));
        this.field.set(errorField(error));
      },
    });
  }

  saveCategory(row: TransactionCategoryDto): void {
    this.api
      .saveTransactionCategory(row.typeCode, row.categoryCode, row.description, row.version)
      .subscribe({
        next: (saved) => {
          row.version = saved.version;
          this.kind.set('ok');
          this.message.set(
            `Category ${saved.typeCode}/${saved.categoryCode} has been saved ...`,
          );
        },
        error: (error: unknown) => {
          this.kind.set('error');
          this.message.set(errorMessage(error));
        },
      });
  }

  private load(cursor: string | null, direction: string, page: number): void {
    this.api
      .adminTransactionTypes({
        typeCode: this.typeFilter.trim() || undefined,
        description: this.descriptionFilter.trim() || undefined,
        cursor,
        direction,
        page,
      })
      .subscribe({
        next: (result) => this.page.set(result),
        error: (error: unknown) => {
          this.page.set(null);
          this.kind.set('error');
          this.message.set(errorMessage(error));
          this.field.set(errorField(error));
        },
      });
  }
}

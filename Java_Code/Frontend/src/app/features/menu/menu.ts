import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiService, errorMessage } from '../../core/api.service';
import { MenuOption, MenuView } from '../../core/models';
import { MessageLineComponent } from '../../shared/message-line';
import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Main menu and administrator menu. COBOL programs {@code COMEN01C} ({@code CM00}, option table
 * {@code COMEN02Y}) and {@code COADM01C} ({@code CA00}, option table {@code COADM02Y}).
 *
 * <p>The numeric option box reproduces the legacy edit: the two-character entry is trimmed,
 * remaining spaces become zeroes, and non-numeric, zero or out-of-range values are rejected. An
 * optional module that is not installed reports the source style "not installed" message instead
 * of navigating.</p>
 */
@Component({
  selector: 'cd-menu',
  imports: [FormsModule, MessageLineComponent, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      [title]="admin ? 'Administrator menu' : 'Main menu'"
      [subtitle]="
        admin
          ? 'Security and reference-data functions'
          : 'Account, card, transaction, report and payment functions'
      "
    />

    <cd-message [text]="message()" [kind]="kind()" />

    <div class="cd-panel">
      <div class="cd-panel__head">
        <h2>Select an option</h2>
        <form class="cd-actions" (ngSubmit)="selectByNumber()">
          <input
            name="option"
            class="cd-mono"
            style="width: 74px"
            maxlength="2"
            placeholder="00"
            [(ngModel)]="option"
            aria-label="Option number"
          />
          <button type="submit">Go</button>
        </form>
      </div>

      <div class="cd-panel__body">
        <div class="cd-menu-grid">
          @for (item of view()?.options ?? []; track item.number) {
            <button
              type="button"
              class="cd-menu-card"
              [disabled]="!item.installed"
              (click)="open(item)"
            >
              <span class="cd-menu-card__num">{{ item.number }}</span>
              <span class="cd-menu-card__text">
                <span class="cd-menu-card__name">{{ item.name }}</span>
                <span class="cd-menu-card__pgm">
                  {{ item.program }}
                  @if (!item.installed) {
                    &middot; not installed
                  }
                </span>
              </span>
            </button>
          }
        </div>
      </div>
    </div>
  `,
})
export class MenuComponent {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly admin = this.route.snapshot.data['admin'] === true;
  readonly view = signal<MenuView | null>(null);
  readonly message = signal<string | null>(null);
  readonly kind = signal<'error' | 'info' | 'warn'>('error');

  option = '';

  constructor() {
    if (this.route.snapshot.queryParamMap.get('denied')) {
      this.kind.set('warn');
      this.message.set('No access - Admin Only option... ');
    }
    const source$ = this.admin ? this.api.adminMenu() : this.api.mainMenu();
    source$.subscribe({
      next: (view) => this.view.set(view),
      error: (error: unknown) => {
        this.kind.set('error');
        this.message.set(errorMessage(error));
      },
    });
  }

  /** Enter key: resolve the typed option through the backend so the legacy edits apply. */
  selectByNumber(): void {
    this.message.set(null);
    const typed = this.option.trim();
    if (!typed) {
      this.kind.set('error');
      this.message.set('Please enter a valid option number...');
      return;
    }
    this.resolve(typed);
  }

  private resolve(typed: string): void {
    const options = this.view()?.options ?? [];
    const numeric = Number(typed.replace(/\s/g, '0'));
    if (!Number.isInteger(numeric) || numeric < 1 || numeric > options.length) {
      this.kind.set('error');
      this.message.set('Please enter a valid option number...');
      return;
    }
    this.open(options[numeric - 1]);
  }

  open(item: MenuOption): void {
    if (!item.installed) {
      this.kind.set('warn');
      this.message.set(`${item.name} is not available at this time ...`);
      return;
    }
    this.option = '';
    void this.router.navigateByUrl(item.route);
  }
}

import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from '../core/auth.service';
import { IconComponent } from '../shared/icon';

/** One entry in the sidebar. */
interface NavItem {
  label: string;
  route: string;
  icon: string;
  /** CICS transaction the screen replaces, shown as a hint. */
  tran?: string;
}

/** A labelled group of sidebar entries. */
interface NavGroup {
  label: string;
  admin: boolean;
  items: NavItem[];
}

/**
 * Application shell: a fixed sidebar plus the routed screen.
 *
 * The sidebar carries every destination, grouped the way the two legacy menus grouped them: the
 * regular-user functions of {@code COMEN02Y} and, for an administrator, the security and
 * reference functions of {@code COADM02Y} together with the batch console.
 */
@Component({
  selector: 'cd-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  template: `
    <div class="cd-app" [class.is-menu-open]="menuOpen()">
      <div class="cd-app__scrim" (click)="menuOpen.set(false)"></div>

      <aside class="cd-sidebar">
        <a class="cd-sidebar__brand" routerLink="/dashboard">
          <span class="cd-sidebar__emblem"><cd-icon name="emblem" [size]="22" /></span>
          <span class="cd-sidebar__brandtext">
            CARDDEMO
            <small>Credit Card Servicing</small>
          </span>
        </a>

        <nav class="cd-sidebar__nav">
          @for (group of visibleGroups(); track group.label) {
            <div class="cd-navgroup">
              <span class="cd-navgroup__label">{{ group.label }}</span>
              @for (item of group.items; track item.route) {
                <a
                  [routerLink]="item.route"
                  routerLinkActive="active"
                  [routerLinkActiveOptions]="{ exact: item.route === '/dashboard' }"
                  (click)="menuOpen.set(false)"
                >
                  <cd-icon [name]="item.icon" />
                  <span>{{ item.label }}</span>
                  @if (item.tran) {
                    <em>{{ item.tran }}</em>
                  }
                </a>
              }
            </div>
          }
        </nav>

        <div class="cd-sidebar__user">
          <a class="cd-sidebar__profile" routerLink="/change-password" title="Change password">
            <span class="cd-sidebar__avatar">{{ initials() }}</span>
            <span class="cd-sidebar__profiletext">
              {{ auth.displayName() }}
              <small>{{ auth.isAdmin() ? 'Administrator' : 'Regular user' }}</small>
            </span>
          </a>
          <button type="button" class="cd-sidebar__signoff" (click)="signOff()" title="Sign off">
            <cd-icon name="logout" />
          </button>
        </div>
      </aside>

      <div class="cd-content">
        <button
          type="button"
          class="cd-menutoggle"
          aria-label="Toggle navigation"
          (click)="menuOpen.set(!menuOpen())"
        >
          <cd-icon name="menu" [size]="20" />
          <span>Menu</span>
        </button>

        <main class="cd-main">
          <router-outlet />
        </main>

        <footer class="cd-footer">
          Java conversion of the AWS Mainframe Modernization CardDemo COBOL application &middot;
          Angular &rarr; Spring Boot &rarr; PostgreSQL
        </footer>
      </div>
    </div>
  `,
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly menuOpen = signal(false);

  private readonly groups: NavGroup[] = [
    {
      label: 'Overview',
      admin: false,
      items: [
        { label: 'Dashboard', route: '/dashboard', icon: 'dashboard' },
        { label: 'Menu', route: '/main-menu', icon: 'menu', tran: 'CM00' },
      ],
    },
    {
      label: 'Servicing',
      admin: false,
      items: [
        { label: 'Accounts', route: '/accounts/view', icon: 'account', tran: 'CAVW' },
        { label: 'Cards', route: '/cards', icon: 'card', tran: 'CCLI' },
        { label: 'Transactions', route: '/transactions', icon: 'transactions', tran: 'CT00' },
        { label: 'Bill payment', route: '/bill-payment', icon: 'billPayment', tran: 'CB00' },
      ],
    },
    {
      label: 'Reporting',
      admin: false,
      items: [
        { label: 'Reports', route: '/reports', icon: 'reports', tran: 'CR00' },
        { label: 'Statements', route: '/statements', icon: 'statements' },
        { label: 'Reference data', route: '/reference', icon: 'reference' },
      ],
    },
    {
      label: 'Administration',
      admin: true,
      items: [
        { label: 'Security users', route: '/admin/users', icon: 'users', tran: 'CU00' },
        { label: 'Transaction types', route: '/admin/transaction-types', icon: 'tag', tran: 'CTLI' },
        { label: 'Batch operations', route: '/admin/batch', icon: 'batch' },
        { label: 'Audit trail', route: '/admin/audit', icon: 'audit' },
      ],
    },
  ];

  /** Administrator groups are hidden for a regular user; the backend enforces the same rule. */
  readonly visibleGroups = computed(() =>
    this.groups.filter((group) => !group.admin || this.auth.isAdmin()),
  );

  readonly initials = computed(() => {
    const user = this.auth.user();
    if (!user) {
      return '--';
    }
    const first = (user.firstName ?? '').trim().charAt(0);
    const last = (user.lastName ?? '').trim().charAt(0);
    const initials = `${first}${last}`.trim();
    return (initials.length > 0 ? initials : user.userId.slice(0, 2)).toUpperCase();
  });

  constructor() {
    // Close the mobile drawer whenever a navigation completes.
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => this.menuOpen.set(false));
  }

  /** Sign off, the web equivalent of F3 from the sign-on screen. */
  signOff(): void {
    this.auth.logout(false);
    void this.router.navigate(['/login'], { queryParams: { signedOff: '1' } });
  }
}

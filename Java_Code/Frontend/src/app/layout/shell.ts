import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth.service';

/**
 * Application shell: the navigation bar plus the routed screen.
 *
 * Navigation mirrors the two legacy menus. A regular user sees the eleven main-menu functions;
 * an administrator additionally sees the security and batch functions.
 */
@Component({
  selector: 'cd-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="cd-shell">
      <nav class="cd-topbar">
        <div class="cd-topbar__brand">
          CardDemo
          <small>Credit card servicing</small>
        </div>

        <div class="cd-topbar__nav">
          <a routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
          <a [routerLink]="auth.isAdmin() ? '/admin-menu' : '/main-menu'" routerLinkActive="active">
            Menu
          </a>
          <a routerLink="/accounts/view" routerLinkActive="active">Accounts</a>
          <a routerLink="/cards" routerLinkActive="active">Cards</a>
          <a routerLink="/transactions" routerLinkActive="active">Transactions</a>
          <a routerLink="/bill-payment" routerLinkActive="active">Bill payment</a>
          <a routerLink="/reports" routerLinkActive="active">Reports</a>
          <a routerLink="/statements" routerLinkActive="active">Statements</a>
          <a routerLink="/reference" routerLinkActive="active">Reference</a>
          @if (auth.isAdmin()) {
            <a routerLink="/admin/users" routerLinkActive="active">Users</a>
            <a routerLink="/admin/transaction-types" routerLinkActive="active">Tran types</a>
            <a routerLink="/admin/batch" routerLinkActive="active">Batch</a>
            <a routerLink="/admin/audit" routerLinkActive="active">Audit</a>
          }
        </div>

        <div class="cd-topbar__user">
          <span class="cd-role">{{ auth.isAdmin() ? 'Admin' : 'User' }}</span>
          <a routerLink="/change-password" title="Change password">{{ auth.displayName() }}</a>
          <button type="button" class="cd-small" (click)="signOff()">Sign off</button>
        </div>
      </nav>

      <main class="cd-main">
        <router-outlet />
      </main>

      <footer class="cd-footer">
        Java conversion of the AWS Mainframe Modernization CardDemo COBOL application &middot;
        Angular &rarr; Spring Boot &rarr; PostgreSQL
      </footer>
    </div>
  `,
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** F3 from the sign-on screen: thank the user and terminate the session. */
  signOff(): void {
    this.auth.logout(false);
    void this.router.navigate(['/login'], { queryParams: { signedOff: '1' } });
  }
}

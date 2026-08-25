import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from '../core/auth.service';
import { SystemStatusService } from '../core/system-status.service';
import { IconComponent } from '../shared/icon';
import { NavbarComponent } from './navbar';
import { NAV_GROUPS } from './navigation';

/**
 * Application shell: a sidebar of destinations, a navbar of context and identity, and the routed
 * screen between them.
 *
 * <p>The two are complementary rather than duplicated. Everywhere the user can go lives in the
 * sidebar, grouped the way the two legacy menus grouped it; where the user currently is, the quick
 * account lookup and the identity menu live in the navbar.</p>
 */
@Component({
  selector: 'cd-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, NavbarComponent],
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

        <p class="cd-sidebar__status" [attr.data-state]="system.status()">
          <i aria-hidden="true"></i>{{ system.label() }}
        </p>
      </aside>

      <div class="cd-content">
        <cd-navbar (toggleMenu)="menuOpen.set(!menuOpen())" />

        <main class="cd-main">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  readonly system = inject(SystemStatusService);
  private readonly router = inject(Router);

  readonly menuOpen = signal(false);

  /** Administrator groups are hidden for a regular user; the backend enforces the same rule. */
  readonly visibleGroups = computed(() =>
    NAV_GROUPS.filter((group) => !group.admin || this.auth.isAdmin()),
  );

  constructor() {
    // Close the mobile drawer whenever a navigation completes.
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => this.menuOpen.set(false));
  }
}

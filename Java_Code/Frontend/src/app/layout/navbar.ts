import { Component, ElementRef, HostListener, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';

import { AuthService } from '../core/auth.service';
import { IconComponent } from '../shared/icon';
import { resolveBreadcrumb } from './navigation';

/**
 * Top navigation bar.
 *
 * <p>Destinations live in the sidebar, so this bar carries what the sidebar cannot: where the user
 * currently is, a quick jump straight to an account, and the identity menu. On a narrow viewport it
 * also owns the button that opens the sidebar drawer.</p>
 */
@Component({
  selector: 'cd-navbar',
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <header class="cd-navbar">
      <div class="cd-navbar__inner">
        <button
          type="button"
          class="cd-navbar__burger"
          aria-label="Open navigation"
          (click)="toggleMenu.emit()"
        >
          <cd-icon name="menu" [size]="20" />
        </button>

        <nav class="cd-breadcrumb" aria-label="Breadcrumb">
          <span class="cd-breadcrumb__section">{{ crumb().section }}</span>
          <cd-icon name="chevron" [size]="14" />
          <span class="cd-breadcrumb__page">{{ crumb().page }}</span>
          @if (crumb().tran) {
            <span class="cd-breadcrumb__tran">{{ crumb().tran }}</span>
          }
        </nav>

        <form class="cd-quickfind" (ngSubmit)="jumpToAccount()">
          <cd-icon name="account" [size]="16" />
          <input
            name="quickAccount"
            class="cd-mono"
            maxlength="11"
            placeholder="Go to account"
            aria-label="Go to account number"
            [(ngModel)]="accountId"
          />
        </form>

        <div class="cd-usermenu" [class.is-open]="open()">
          <button
            type="button"
            class="cd-usermenu__trigger"
            [attr.aria-expanded]="open()"
            aria-haspopup="menu"
            (click)="open.set(!open())"
          >
            <span class="cd-usermenu__avatar">{{ initials() }}</span>
            <span class="cd-usermenu__text">
              {{ auth.displayName() }}
              <small>{{ auth.isAdmin() ? 'Administrator' : 'Regular user' }}</small>
            </span>
            <cd-icon name="chevron" [size]="14" />
          </button>

          @if (open()) {
            <div class="cd-usermenu__panel" role="menu">
              <div class="cd-usermenu__head">
                <strong>{{ auth.user()?.userId }}</strong>
                <span>{{ auth.isAdmin() ? 'Role A - Administrator' : 'Role U - Regular user' }}</span>
              </div>
              <a routerLink="/change-password" role="menuitem" (click)="open.set(false)">
                <cd-icon name="lock" [size]="16" />
                Change password
              </a>
              <button type="button" role="menuitem" class="cd-usermenu__signoff" (click)="signOff()">
                <cd-icon name="logout" [size]="16" />
                Sign off
              </button>
            </div>
          }
        </div>
      </div>
    </header>
  `,
})
export class NavbarComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  /** Raised when the burger is pressed, so the shell can open the sidebar drawer. */
  readonly toggleMenu = output<void>();

  readonly open = signal(false);
  accountId = '';

  /** Where the user currently is, recomputed on every completed navigation. */
  readonly crumb = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => resolveBreadcrumb(event.urlAfterRedirects)),
      startWith(resolveBreadcrumb(this.router.url)),
    ),
    { initialValue: resolveBreadcrumb(this.router.url) },
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

  /** Jumps straight to the account view, the lookup a servicing agent reaches for most. */
  jumpToAccount(): void {
    const id = this.accountId.trim();
    if (!id) {
      return;
    }
    this.accountId = '';
    void this.router.navigate(['/accounts/view', id]);
  }

  signOff(): void {
    this.open.set(false);
    this.auth.logout(false);
    void this.router.navigate(['/login'], { queryParams: { signedOff: '1' } });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.open.set(false);
  }
}

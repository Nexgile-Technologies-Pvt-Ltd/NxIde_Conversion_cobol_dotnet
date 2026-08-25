import { DatePipe } from '@angular/common';
import { Component, Input, OnDestroy, signal } from '@angular/core';

/**
 * The heading every screen carries: its title, a line of context beneath it, and the clock.
 *
 * <p>The BMS maps this application was converted from also carried a transaction id, a program
 * name and the source file each screen came from. Those identified the screen to someone working
 * a 3270; they mean nothing to someone working this one, so the header states the screen in its
 * own terms. The transaction ids remain in the breadcrumb and the sidebar, which is where they
 * are useful for tracing a screen back to its source.</p>
 */
@Component({
  selector: 'cd-screen-header',
  imports: [DatePipe],
  template: `
    <header class="cd-screen-header">
      <div class="cd-screen-header__title">
        <div>
          <h1>{{ title }}</h1>
          @if (subtitle) {
            <div class="cd-screen-header__subtitle">{{ subtitle }}</div>
          }
        </div>
        <span class="cd-screen-header__clock">{{ now() | date: 'MM/dd/yy' }} &middot; {{ now() | date: 'HH:mm:ss' }}</span>
      </div>
    </header>
  `,
})
export class ScreenHeaderComponent implements OnDestroy {
  @Input() title = '';
  @Input() subtitle = '';

  /**
   * The time now, not the moment the screen was opened. The map header this replaces was stamped
   * once when the screen was sent, which on a long-lived route reads as a stopped clock.
   */
  readonly now = signal(new Date());

  private readonly tick: ReturnType<typeof setInterval> = setInterval(
    () => this.now.set(new Date()),
    1000,
  );

  ngOnDestroy(): void {
    clearInterval(this.tick);
  }
}

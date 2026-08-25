import { Component, ElementRef, Input, inject } from '@angular/core';

/**
 * The single message line of a BMS map. Errors, informational text and confirmations all render
 * here so each screen has exactly one place a user looks for feedback.
 *
 * <p>A 3270 screen was one page, so the message line was always in view. A long form such as
 * account update is not: the submit button sits well below the fold, and a message rendered at the
 * top would go unread. Whenever the text changes the line brings itself into view, which restores
 * the property the original screens had for free.</p>
 */
@Component({
  selector: 'cd-message',
  template: `
    @if (text) {
      <div class="cd-message cd-message--{{ kind }}" role="status" aria-live="polite">
        <span>{{ text }}</span>
      </div>
    }
  `,
})
export class MessageLineComponent {
  private readonly host = inject(ElementRef<HTMLElement>);

  @Input()
  set text(value: string | null) {
    const changed = value !== this.currentText;
    this.currentText = value;
    if (changed && value) {
      this.reveal();
    }
  }

  get text(): string | null {
    return this.currentText;
  }

  @Input() kind: 'error' | 'info' | 'ok' | 'warn' = 'error';

  private currentText: string | null = null;

  /** Deferred a frame so the line has been rendered before it is scrolled to. */
  private reveal(): void {
    queueMicrotask(() => {
      const element = this.host.nativeElement as HTMLElement;
      const line = element.querySelector('.cd-message');
      if (!line) {
        return;
      }
      const box = line.getBoundingClientRect();
      if (box.top >= 0 && box.bottom <= window.innerHeight) {
        return; // already on screen; scrolling would only move the page under the reader
      }
      line.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
  }
}

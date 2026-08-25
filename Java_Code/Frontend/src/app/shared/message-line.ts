import { Component, Input } from '@angular/core';

/**
 * The single message line of a BMS map. Errors, informational text and confirmations all render
 * here so each screen has exactly one place a user looks for feedback.
 */
@Component({
  selector: 'cd-message',
  template: `
    @if (text) {
      <div class="cd-message cd-message--{{ kind }}" role="status">
        <span>{{ text }}</span>
      </div>
    }
  `,
})
export class MessageLineComponent {
  @Input() text: string | null = null;
  @Input() kind: 'error' | 'info' | 'ok' | 'warn' = 'error';
}

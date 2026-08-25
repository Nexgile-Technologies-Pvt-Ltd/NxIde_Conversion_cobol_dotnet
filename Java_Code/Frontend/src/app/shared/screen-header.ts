import { DatePipe } from '@angular/common';
import { Component, Input } from '@angular/core';

/**
 * The common screen header every BMS map carried: transaction id, program name, title, date and
 * time. Reproducing it keeps each screen recognisable to someone who used the 3270 application.
 */
@Component({
  selector: 'cd-screen-header',
  imports: [DatePipe],
  template: `
    <header class="cd-screen-header">
      <div class="cd-screen-header__line">
        <span>TRAN: {{ tran }}</span>
        <span>CardDemo</span>
        <span>{{ now | date: 'MM/dd/yy' }}</span>
      </div>
      <div class="cd-screen-header__line">
        <span>PGM: {{ program }}</span>
        <span></span>
        <span>{{ now | date: 'HH:mm:ss' }}</span>
      </div>
      <div class="cd-screen-header__title">
        <div>
          <h1>{{ title }}</h1>
          @if (subtitle) {
            <div class="cd-screen-header__subtitle">{{ subtitle }}</div>
          }
        </div>
        @if (origin) {
          <span class="cd-screen-header__origin">COBOL source: {{ origin }}</span>
        }
      </div>
    </header>
  `,
})
export class ScreenHeaderComponent {
  /** CICS transaction id, for example {@code CAVW}. */
  @Input() tran = '';
  /** COBOL program name, for example {@code COACTVWC}. */
  @Input() program = '';
  @Input() title = '';
  @Input() subtitle = '';
  /** Source file this screen was converted from, shown as provenance. */
  @Input() origin = '';

  readonly now = new Date();
}

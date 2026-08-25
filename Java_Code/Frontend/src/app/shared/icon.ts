import { Component, Input, computed, signal } from '@angular/core';

/**
 * Icon set used by the sidebar, the sign-on panels and the inline field adornments.
 *
 * Each icon is a list of path definitions drawn on a 24x24 grid with the current colour, so an
 * icon inherits whatever the surrounding text is using. Circles are expressed as arc paths to keep
 * the whole set to a single element type.
 */
const ICONS: Record<string, string[]> = {
  // ---------------------------------------------------------------- navigation
  dashboard: ['M3 3h7v8H3z', 'M14 3h7v5h-7z', 'M14 12h7v9h-7z', 'M3 15h7v6H3z'],
  menu: ['M4 6h16', 'M4 12h16', 'M4 18h16'],
  account: ['M3 5h18v14H3z', 'M11 12a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0', 'M14 10h4', 'M14 14h4'],
  card: ['M2 6h20v12H2z', 'M2 10h20', 'M6 15h4'],
  transactions: ['M4 8h13', 'M14 5l3 3-3 3', 'M20 16H7', 'M10 13l-3 3 3 3'],
  billPayment: ['M6 3h12v18l-3-2-3 2-3-2-3 2z', 'M9 8h6', 'M9 12h6'],
  reports: ['M3 21h18', 'M6 17v-5', 'M11 17V7', 'M16 17v-8'],
  statements: ['M7 3h7l5 5v13H7z', 'M14 3v5h5', 'M10 13h7', 'M10 17h5'],
  reference: ['M4 5a2 2 0 012-2h13v18H6a2 2 0 01-2-2z', 'M9 7h7', 'M9 11h7'],
  users: [
    'M11 9a3 3 0 11-6 0 3 3 0 016 0',
    'M2 20a6 6 0 0112 0',
    'M16 6.5a2.5 2.5 0 010 5',
    'M17 14.5a5.5 5.5 0 015 5.5',
  ],
  tag: ['M20.5 4H13L3 14l7 7 10.5-10.5z', 'M17 8.5a.6.6 0 100-1.2.6.6 0 000 1.2'],
  batch: [
    'M12 15a3 3 0 100-6 3 3 0 000 6',
    'M19.4 14a1.6 1.6 0 00.3 1.8l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1.6 1.6 0 00-2.7 1.1v.3a2 2 0 11-4 0v-.2a1.6 1.6 0 00-2.8-1.1l-.1.1a2 2 0 11-2.8-2.8l.1-.1A1.6 1.6 0 003.6 13H3a2 2 0 110-4h.2A1.6 1.6 0 004.3 6.2l-.1-.1a2 2 0 112.8-2.8l.1.1A1.6 1.6 0 0010 3.6V3a2 2 0 114 0v.2a1.6 1.6 0 002.7 1.1l.1-.1a2 2 0 112.8 2.8l-.1.1a1.6 1.6 0 001.1 2.7h.3a2 2 0 110 4h-.2a1.6 1.6 0 00-1.3 1z',
  ],
  audit: ['M12 3l8 3v6c0 5-3.4 8.2-8 9-4.6-.8-8-4-8-9V6z', 'M9 12l2.2 2.2L15.5 10'],

  // ---------------------------------------------------------------- sign-on panel
  shield: ['M12 3l8 3v6c0 5-3.4 8.2-8 9-4.6-.8-8-4-8-9V6z'],
  percent: ['M19 5L5 19', 'M9 7.5a2 2 0 11-4 0 2 2 0 014 0', 'M19 16.5a2 2 0 11-4 0 2 2 0 014 0'],
  ledger: ['M4 4h16v16H4z', 'M4 9h16', 'M9 9v11', 'M12.5 13h4', 'M12.5 16.5h4'],

  // ---------------------------------------------------------------- form adornments
  user: ['M15.5 8a3.5 3.5 0 11-7 0 3.5 3.5 0 017 0', 'M4 20a8 8 0 0116 0'],
  lock: ['M5 11h14v10H5z', 'M8 11V7.5a4 4 0 018 0V11', 'M12 15v2.5'],
  eye: ['M2 12s3.6-6.5 10-6.5S22 12 22 12s-3.6 6.5-10 6.5S2 12 2 12', 'M15 12a3 3 0 11-6 0 3 3 0 016 0'],
  eyeOff: ['M4 4l16 16', 'M9.9 5.7A10.6 10.6 0 0112 5.5c6.4 0 10 6.5 10 6.5a17 17 0 01-3.3 4', 'M6.4 7.6A16.6 16.6 0 002 12s3.6 6.5 10 6.5c1.5 0 2.8-.3 4-.8', 'M9.9 9.9a3 3 0 004.2 4.2'],
  arrowRight: ['M4 12h15', 'M13 6l6 6-6 6'],
  logout: ['M15 4h4v16h-4', 'M10 8l-4 4 4 4', 'M6 12h10'],
  chevron: ['M9 6l6 6-6 6'],

  // ---------------------------------------------------------------- brand emblem
  emblem: ['M3 7h18v10H3z', 'M3 10.5h18', 'M6.5 14h3.5', 'M12 3.2l1.6 1.1h1.9', 'M12 20.8l-1.6-1.1H8.5'],
};

/** Renders one icon from the shared set. */
@Component({
  selector: 'cd-icon',
  template: `
    <svg
      [attr.width]="size"
      [attr.height]="size"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="strokeWidth"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      @for (d of paths(); track $index) {
        <path [attr.d]="d" />
      }
    </svg>
  `,
  styles: [':host { display: inline-flex; line-height: 0; }'],
})
export class IconComponent {
  private readonly iconName = signal<string>('');

  @Input({ required: true })
  set name(value: string) {
    this.iconName.set(value);
  }

  @Input() size = 18;
  @Input() strokeWidth = 1.7;

  readonly paths = computed(() => ICONS[this.iconName()] ?? []);
}

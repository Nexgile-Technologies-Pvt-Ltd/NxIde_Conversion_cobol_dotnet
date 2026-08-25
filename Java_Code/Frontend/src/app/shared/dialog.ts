import { Component, HostListener, Input, OnDestroy, output } from '@angular/core';

/**
 * A panel raised over the screen instead of appended beneath it.
 *
 * <p>These panels answer a button press. Rendered in flow they land at the foot of the page,
 * which on a list screen is a whole table below the button that opened them, so the press reads
 * as having done nothing at all. Raised here the answer arrives in view wherever the page happens
 * to be scrolled to.</p>
 *
 * <p>The scrim and the Escape key both dismiss it, so it can always be left without hunting for
 * the close control.</p>
 */
@Component({
  selector: 'cd-dialog',
  template: `
    <div class="cd-dialog__scrim" (click)="closed.emit()"></div>
    <div class="cd-dialog" role="dialog" aria-modal="true" [attr.aria-label]="heading">
      <div class="cd-dialog__head">
        <h2>{{ heading }}</h2>
        <button type="button" class="cd-dialog__close" aria-label="Close" (click)="closed.emit()">
          &times;
        </button>
      </div>
      <div class="cd-dialog__body"><ng-content /></div>
    </div>
  `,
})
export class DialogComponent implements OnDestroy {
  /**
   * How many dialogs are currently raised. The page is held still while any of them is, so two
   * opening in turn cannot have the first one to close release the lock under the second.
   */
  private static open = 0;

  /** Where the page was standing when the first panel was raised, restored when the last closes. */
  private static parkedAt = 0;

  private locked = false;

  @Input() heading = '';
  readonly closed = output<void>();

  constructor() {
    this.lock();
  }

  ngOnDestroy(): void {
    this.release();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  /**
   * Holds the page still behind the panel.
   *
   * <p>Hiding the overflow is not enough on its own: an element with hidden overflow still scrolls
   * when something asks it to, so the screen behind can still be moved. The body is taken out of
   * flow instead and offset by however far the page was scrolled, which leaves the same view on
   * screen while giving the document nothing left to scroll.</p>
   *
   * <p>That also reclaims the scrollbar's width, so the width it occupied is handed back as
   * padding and the page does not jump sideways as it locks.</p>
   */
  private lock(): void {
    if (this.locked) {
      return;
    }
    this.locked = true;
    if (DialogComponent.open++ === 0) {
      const gap = window.innerWidth - document.documentElement.clientWidth;
      DialogComponent.parkedAt = window.scrollY;
      document.body.style.setProperty('--cd-scrollbar-gap', `${gap}px`);
      document.body.style.top = `-${DialogComponent.parkedAt}px`;
      document.body.classList.add('cd-dialog-open');
    }
  }

  private release(): void {
    if (!this.locked) {
      return;
    }
    this.locked = false;
    if (--DialogComponent.open === 0) {
      document.body.classList.remove('cd-dialog-open');
      document.body.style.removeProperty('--cd-scrollbar-gap');
      document.body.style.removeProperty('top');
      window.scrollTo(0, DialogComponent.parkedAt);
    }
  }
}

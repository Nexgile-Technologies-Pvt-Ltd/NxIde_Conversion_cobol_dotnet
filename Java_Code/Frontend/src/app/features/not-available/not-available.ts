import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ScreenHeaderComponent } from '../../shared/screen-header';

/**
 * Placeholder for an optional module that is not installed.
 *
 * <p>Main-menu option 11 (pending authorization view, {@code COPAUS0C}) belongs to the separately
 * packaged IMS/Db2/MQ extension. The legacy menu issued {@code INQUIRE PROGRAM} and reported "not
 * installed"; FR-OPT-017 requires the target to give the same navigation result rather than
 * failing, so this screen states the position instead of pretending the function exists.</p>
 */
@Component({
  selector: 'cd-not-available',
  imports: [RouterLink, ScreenHeaderComponent],
  template: `
    <cd-screen-header
      title="Pending authorization view"
      subtitle="Optional module"
    />

    <div class="cd-panel">
      <div class="cd-panel__body">
        <div class="cd-message cd-message--warn">
          <span>
            This function belongs to the optional authorization extension, which uses resources
            that are not part of this deployment.
          </span>
        </div>
        <div class="cd-actions" style="margin-top: 14px">
          <a class="cd-btn" routerLink="/main-menu">Return to the main menu</a>
        </div>
      </div>
    </div>
  `,
})
export class NotAvailableComponent {}

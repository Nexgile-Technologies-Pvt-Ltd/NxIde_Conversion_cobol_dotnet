import { Component } from '@angular/core';

import { IconComponent } from '../../shared/icon';

/**
 * The branded left half of the sign-on and sign-up screens.
 *
 * The six capability rows are the application's own: account and customer servicing, daily
 * transaction posting, monthly interest and statements, the security controls that replaced the
 * legacy plaintext credential model, online bill payment, and the batch console that stands in
 * for submitting the jobs through JES.
 */
@Component({
  selector: 'cd-auth-brand-panel',
  imports: [IconComponent],
  template: `
    <section class="cd-brand">
      <header class="cd-brand__mark">
        <span class="cd-brand__emblem"><cd-icon name="emblem" [size]="26" /></span>
        <span class="cd-brand__name">
          CARDDEMO
          <small>Credit Card Servicing</small>
          <em>Service. Post. Reconcile.</em>
        </span>
      </header>

      <div class="cd-brand__headline">
        <h1>
          Service Every Account.
          <span>Balance Every Cent.</span>
        </h1>
        <p>
          Credit card servicing on Angular, Spring Boot and PostgreSQL. Every screen, validation
          rule and batch calculation is verified to the cent against the shipped fixture data.
        </p>
      </div>

      <ul class="cd-brand__features">
        @for (feature of features; track feature.title) {
          <li>
            <span class="cd-brand__icon"><cd-icon [name]="feature.icon" [size]="19" /></span>
            <span>
              <strong>{{ feature.title }}</strong>
              {{ feature.copy }}
            </span>
          </li>
        }
      </ul>
    </section>
  `,
})
export class AuthBrandPanelComponent {
  readonly features = [
    {
      icon: 'account',
      title: 'Accounts & Cards',
      copy: 'View and update account, customer and card records under the original validation order.',
    },
    {
      icon: 'transactions',
      title: 'Transaction Posting',
      copy: 'Daily posting with credit-limit and expiry checks, and a precise reason for each reject.',
    },
    {
      icon: 'percent',
      title: 'Interest & Statements',
      copy: 'Monthly interest per disclosure group, plus fixed-width card statements and reports.',
    },
    {
      icon: 'shield',
      title: 'Secured & Audited',
      copy: 'Hashed credentials, role-checked use cases and a redacted trail of every change.',
    },
    {
      icon: 'billPayment',
      title: 'Bill Payment',
      copy: 'Settle a balance in full from the card, posted with the original type, category and source.',
    },
    {
      icon: 'batch',
      title: 'Batch Operations',
      copy: 'Run posting, interest, reports and statements, each with its completion code and rejects.',
    },
  ];
}

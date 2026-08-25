import { Routes } from '@angular/router';

import { adminGuard, anonymousGuard, authGuard } from './core/guards';
import { ShellComponent } from './layout/shell';

/**
 * Route table. Each protected route corresponds to one CICS transaction of the legacy
 * application; the shell provides the navigation the two BMS menus used to provide.
 */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [anonymousGuard],
    title: 'Sign on - CardDemo',
    loadComponent: () => import('./features/auth/login').then((m) => m.LoginComponent),
  },
  {
    path: 'signup',
    canActivate: [anonymousGuard],
    title: 'Sign up - CardDemo',
    loadComponent: () => import('./features/auth/signup').then((m) => m.SignupComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },

      {
        path: 'dashboard',
        title: 'Dashboard - CardDemo',
        loadComponent: () =>
          import('./features/dashboard/dashboard').then((m) => m.DashboardComponent),
      },

      // CM00 main menu and CA00 administrator menu
      {
        path: 'main-menu',
        data: { admin: false },
        title: 'Main menu - CardDemo',
        loadComponent: () => import('./features/menu/menu').then((m) => m.MenuComponent),
      },
      {
        path: 'admin-menu',
        data: { admin: true },
        canActivate: [adminGuard],
        title: 'Administrator menu - CardDemo',
        loadComponent: () => import('./features/menu/menu').then((m) => m.MenuComponent),
      },

      // CAVW account view and CAUP account update
      {
        path: 'accounts/view',
        title: 'View account - CardDemo',
        loadComponent: () =>
          import('./features/accounts/account-view').then((m) => m.AccountViewComponent),
      },
      {
        path: 'accounts/view/:accountId',
        title: 'View account - CardDemo',
        loadComponent: () =>
          import('./features/accounts/account-view').then((m) => m.AccountViewComponent),
      },
      {
        path: 'accounts/update',
        title: 'Update account - CardDemo',
        loadComponent: () =>
          import('./features/accounts/account-update').then((m) => m.AccountUpdateComponent),
      },
      {
        path: 'accounts/update/:accountId',
        title: 'Update account - CardDemo',
        loadComponent: () =>
          import('./features/accounts/account-update').then((m) => m.AccountUpdateComponent),
      },

      // CCLI card list, CCDL card view, CCUP card update
      {
        path: 'cards',
        title: 'Credit card list - CardDemo',
        loadComponent: () => import('./features/cards/card-list').then((m) => m.CardListComponent),
      },
      {
        path: 'cards/view',
        title: 'View card - CardDemo',
        loadComponent: () => import('./features/cards/card-view').then((m) => m.CardViewComponent),
      },
      {
        path: 'cards/view/:cardNumber',
        title: 'View card - CardDemo',
        loadComponent: () => import('./features/cards/card-view').then((m) => m.CardViewComponent),
      },
      {
        path: 'cards/update',
        title: 'Update card - CardDemo',
        loadComponent: () =>
          import('./features/cards/card-update').then((m) => m.CardUpdateComponent),
      },
      {
        path: 'cards/update/:cardNumber',
        title: 'Update card - CardDemo',
        loadComponent: () =>
          import('./features/cards/card-update').then((m) => m.CardUpdateComponent),
      },

      // CT00 transaction list, CT01 view, CT02 add
      {
        path: 'transactions',
        title: 'Transaction list - CardDemo',
        loadComponent: () =>
          import('./features/transactions/transaction-list').then((m) => m.TransactionListComponent),
      },
      {
        path: 'transactions/view',
        title: 'View transaction - CardDemo',
        loadComponent: () =>
          import('./features/transactions/transaction-view').then((m) => m.TransactionViewComponent),
      },
      {
        path: 'transactions/view/:transactionId',
        title: 'View transaction - CardDemo',
        loadComponent: () =>
          import('./features/transactions/transaction-view').then((m) => m.TransactionViewComponent),
      },
      {
        path: 'transactions/add',
        title: 'Add transaction - CardDemo',
        loadComponent: () =>
          import('./features/transactions/transaction-add').then((m) => m.TransactionAddComponent),
      },

      // CB00 bill payment
      {
        path: 'bill-payment',
        title: 'Bill payment - CardDemo',
        loadComponent: () =>
          import('./features/transactions/bill-payment').then((m) => m.BillPaymentComponent),
      },

      // CR00 report request and the statement library
      {
        path: 'reports',
        title: 'Transaction reports - CardDemo',
        loadComponent: () =>
          import('./features/reports/report-request').then((m) => m.ReportRequestComponent),
      },
      {
        path: 'statements',
        title: 'Statements - CardDemo',
        loadComponent: () =>
          import('./features/reports/statements').then((m) => m.StatementsComponent),
      },

      // Reference data browser
      {
        path: 'reference',
        title: 'Reference data - CardDemo',
        loadComponent: () =>
          import('./features/reference/reference').then((m) => m.ReferenceComponent),
      },

      { path: 'change-password',
        title: 'Change password - CardDemo',
        loadComponent: () =>
          import('./features/auth/change-password').then((m) => m.ChangePasswordComponent),
      },

      // Administrator area: CU00-CU03, CTLI/CTTU, batch and audit
      {
        path: 'admin/users',
        canActivate: [adminGuard],
        title: 'Security users - CardDemo',
        loadComponent: () => import('./features/admin/user-list').then((m) => m.UserListComponent),
      },
      {
        path: 'admin/users/add',
        canActivate: [adminGuard],
        title: 'Add user - CardDemo',
        loadComponent: () => import('./features/admin/user-add').then((m) => m.UserAddComponent),
      },
      {
        path: 'admin/users/update',
        canActivate: [adminGuard],
        title: 'Update user - CardDemo',
        loadComponent: () =>
          import('./features/admin/user-update').then((m) => m.UserUpdateComponent),
      },
      {
        path: 'admin/users/update/:userId',
        canActivate: [adminGuard],
        title: 'Update user - CardDemo',
        loadComponent: () =>
          import('./features/admin/user-update').then((m) => m.UserUpdateComponent),
      },
      {
        path: 'admin/users/delete',
        canActivate: [adminGuard],
        title: 'Delete user - CardDemo',
        loadComponent: () =>
          import('./features/admin/user-delete').then((m) => m.UserDeleteComponent),
      },
      {
        path: 'admin/users/delete/:userId',
        canActivate: [adminGuard],
        title: 'Delete user - CardDemo',
        loadComponent: () =>
          import('./features/admin/user-delete').then((m) => m.UserDeleteComponent),
      },
      {
        path: 'admin/transaction-types',
        canActivate: [adminGuard],
        title: 'Transaction types - CardDemo',
        loadComponent: () =>
          import('./features/admin/transaction-type-admin').then(
            (m) => m.TransactionTypeAdminComponent,
          ),
      },
      {
        path: 'admin/transaction-types/maintain',
        canActivate: [adminGuard],
        title: 'Transaction type maintenance - CardDemo',
        loadComponent: () =>
          import('./features/admin/transaction-type-admin').then(
            (m) => m.TransactionTypeAdminComponent,
          ),
      },
      {
        path: 'admin/batch',
        canActivate: [adminGuard],
        title: 'Batch operations - CardDemo',
        loadComponent: () =>
          import('./features/admin/batch-console').then((m) => m.BatchConsoleComponent),
      },
      {
        path: 'admin/audit',
        canActivate: [adminGuard],
        title: 'Audit trail - CardDemo',
        loadComponent: () => import('./features/admin/audit').then((m) => m.AuditComponent),
      },

      // Optional module that the source reports as not installed.
      {
        path: 'pending-authorizations',
        title: 'Pending authorizations - CardDemo',
        loadComponent: () =>
          import('./features/not-available/not-available').then((m) => m.NotAvailableComponent),
      },
    ],
  },

  { path: '**', redirectTo: 'dashboard' },
];

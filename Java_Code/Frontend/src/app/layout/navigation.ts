/**
 * The application's navigation model, as rendered by the sidebar.
 */

/** One destination in the sidebar. */
export interface NavItem {
  label: string;
  route: string;
  icon: string;
  /** CICS transaction the screen replaces, shown as a hint. */
  tran?: string;
  /**
   * Route to use instead when the signed-on user is an administrator. Set on the Menu entry so it
   * follows the {@code COSGN00C} routing: role A reaches the administrator menu, everyone else the
   * main menu.
   */
  adminRoute?: string;
}

/** A labelled group of sidebar destinations. */
export interface NavGroup {
  label: string;
  admin: boolean;
  items: NavItem[];
}

/**
 * Sidebar groups, arranged the way the two legacy menus arranged them: the regular-user functions
 * of {@code COMEN02Y} and, for an administrator, the security and reference functions of
 * {@code COADM02Y} together with the batch console.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    label: 'Overview',
    admin: false,
    items: [
      { label: 'Dashboard', route: '/dashboard', icon: 'dashboard' },
      { label: 'Menu', route: '/main-menu', adminRoute: '/admin-menu', icon: 'menu', tran: 'CM00' },
    ],
  },
  {
    label: 'Servicing',
    admin: false,
    items: [
      { label: 'Accounts', route: '/accounts/view', icon: 'account', tran: 'CAVW' },
      { label: 'Cards', route: '/cards', icon: 'card', tran: 'CCLI' },
      { label: 'Transactions', route: '/transactions', icon: 'transactions', tran: 'CT00' },
      { label: 'Bill payment', route: '/bill-payment', icon: 'billPayment', tran: 'CB00' },
    ],
  },
  {
    label: 'Reporting',
    admin: false,
    items: [
      { label: 'Reports', route: '/reports', icon: 'reports', tran: 'CR00' },
      { label: 'Statements', route: '/statements', icon: 'statements' },
      { label: 'Reference data', route: '/reference', icon: 'reference' },
    ],
  },
  {
    label: 'Administration',
    admin: true,
    items: [
      { label: 'Security users', route: '/admin/users', icon: 'users', tran: 'CU00' },
      { label: 'Transaction types', route: '/admin/transaction-types', icon: 'tag', tran: 'CTLI' },
      { label: 'Batch operations', route: '/admin/batch', icon: 'batch' },
      { label: 'Audit trail', route: '/admin/audit', icon: 'audit' },
    ],
  },
];

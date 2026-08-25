/**
 * The application's navigation model, shared by the sidebar and the navbar breadcrumb.
 */

/** One destination in the sidebar. */
export interface NavItem {
  label: string;
  route: string;
  icon: string;
  /** CICS transaction the screen replaces, shown as a hint. */
  tran?: string;
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
      { label: 'Menu', route: '/main-menu', icon: 'menu', tran: 'CM00' },
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

/** Where the navbar says the user is. */
export interface Breadcrumb {
  section: string;
  page: string;
  /** CICS transaction of the screen, when it replaces one. */
  tran?: string;
}

/**
 * Route to breadcrumb. Sub-routes such as {@code /admin/users/add} are listed in their own right so
 * an action screen names itself rather than inheriting its list screen's label.
 */
const BREADCRUMBS: Array<Breadcrumb & { path: string }> = [
  { path: '/dashboard', section: 'Overview', page: 'Portfolio overview' },
  { path: '/main-menu', section: 'Overview', page: 'Main menu', tran: 'CM00' },
  { path: '/admin-menu', section: 'Overview', page: 'Administrator menu', tran: 'CA00' },

  { path: '/accounts/view', section: 'Servicing', page: 'Account view', tran: 'CAVW' },
  { path: '/accounts/update', section: 'Servicing', page: 'Account update', tran: 'CAUP' },
  { path: '/cards/view', section: 'Servicing', page: 'Card view', tran: 'CCDL' },
  { path: '/cards/update', section: 'Servicing', page: 'Card update', tran: 'CCUP' },
  { path: '/cards', section: 'Servicing', page: 'Card list', tran: 'CCLI' },
  { path: '/transactions/view', section: 'Servicing', page: 'Transaction view', tran: 'CT01' },
  { path: '/transactions/add', section: 'Servicing', page: 'Transaction add', tran: 'CT02' },
  { path: '/transactions', section: 'Servicing', page: 'Transaction list', tran: 'CT00' },
  { path: '/bill-payment', section: 'Servicing', page: 'Bill payment', tran: 'CB00' },
  { path: '/pending-authorizations', section: 'Servicing', page: 'Pending authorizations' },

  { path: '/reports', section: 'Reporting', page: 'Transaction reports', tran: 'CR00' },
  { path: '/statements', section: 'Reporting', page: 'Card statements' },
  { path: '/reference', section: 'Reporting', page: 'Reference data' },

  { path: '/admin/users/add', section: 'Administration', page: 'Add security user', tran: 'CU01' },
  { path: '/admin/users/update', section: 'Administration', page: 'Update security user', tran: 'CU02' },
  { path: '/admin/users/delete', section: 'Administration', page: 'Delete security user', tran: 'CU03' },
  { path: '/admin/users', section: 'Administration', page: 'Security users', tran: 'CU00' },
  { path: '/admin/transaction-types', section: 'Administration', page: 'Transaction types', tran: 'CTLI' },
  { path: '/admin/batch', section: 'Administration', page: 'Batch operations' },
  { path: '/admin/audit', section: 'Administration', page: 'Audit trail' },

  { path: '/change-password', section: 'Account', page: 'Change password' },
];

/**
 * Resolves the breadcrumb for a URL by longest matching prefix, so a route carrying a record key
 * such as {@code /accounts/view/00000000001} still resolves to its screen.
 */
export function resolveBreadcrumb(url: string): Breadcrumb {
  const path = url.split('?')[0].split('#')[0];
  const match = BREADCRUMBS.filter((entry) => path === entry.path || path.startsWith(`${entry.path}/`))
    .sort((a, b) => b.path.length - a.path.length)[0];
  return match ?? { section: 'CardDemo', page: 'Home' };
}

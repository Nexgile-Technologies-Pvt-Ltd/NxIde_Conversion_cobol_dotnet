/**
 * Transport contracts of the CardDemo API.
 *
 * Each interface mirrors one Spring Boot DTO, which in turn mirrors one COBOL screen buffer or
 * record layout. Legacy identifiers stay strings so leading zeroes survive.
 */

export type UserRole = 'A' | 'U';

export interface UserProfile {
  userId: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  admin: boolean;
  /**
   * The menu the COBOL sign-on program would have transferred to: admin menu for role A, main
   * menu otherwise. Sign-on lands on the dashboard, so this drives the Menu destination rather
   * than the landing route.
   */
  menuScreen: string;
}

export interface LoginResponse {
  token: string;
  expiresInSeconds: number;
  user: UserProfile;
}

export interface AuthConfig {
  signupEnabled: boolean;
  minPasswordLength: number;
}

/** Keyset page envelope replacing the CICS browse state of the list screens. */
export interface PageResult<T> {
  rows: T[];
  firstKey: string | null;
  lastKey: string | null;
  pageNumber: number;
  hasNext: boolean;
  hasPrevious: boolean;
  message: string | null;
}

/* ------------------------------------------------------------------ menus */

export interface MenuOption {
  number: number;
  name: string;
  program: string;
  userType: UserRole;
  route: string;
  installed: boolean;
}

export interface MenuView {
  title: string;
  transactionId: string;
  programName: string;
  role: UserRole;
  options: MenuOption[];
}

/* ------------------------------------------------------------------ accounts */

export interface AccountDetail {
  accountId: string;
  activeStatus: string;
  currentBalance: number;
  creditLimit: number;
  cashCreditLimit: number;
  openDate: string;
  expirationDate: string;
  reissueDate: string;
  currentCycleCredit: number;
  currentCycleDebit: number;
  accountZip: string;
  groupId: string;
  accountVersion: number;

  customerId: string;
  firstName: string;
  middleName: string;
  lastName: string;
  ssn: string;
  dateOfBirth: string;
  ficoScore: number;
  addressLine1: string;
  addressLine2: string;
  city: string;
  stateCode: string;
  zipCode: string;
  countryCode: string;
  phone1: string;
  phone2: string;
  governmentIssuedId: string;
  eftAccountId: string;
  primaryCardHolderIndicator: string;
  customerVersion: number;

  cardNumber: string;
}

export interface AccountSummary {
  accountId: string;
  activeStatus: string;
  currentBalance: number;
  creditLimit: number;
  openDate: string;
  expirationDate: string;
  groupId: string;
  customerName: string;
}

export interface AccountUpdateRequest {
  accountId: string;
  activeStatus: string;
  openDate: string;
  creditLimit: string;
  expirationDate: string;
  cashCreditLimit: string;
  reissueDate: string;
  currentBalance: string;
  currentCycleCredit: string;
  currentCycleDebit: string;
  groupId: string;
  ssn: string;
  dateOfBirth: string;
  ficoScore: string;
  firstName: string;
  middleName: string;
  lastName: string;
  addressLine1: string;
  stateCode: string;
  addressLine2: string;
  zipCode: string;
  city: string;
  countryCode: string;
  phone1: string;
  phone2: string;
  governmentIssuedId: string;
  eftAccountId: string;
  primaryCardHolderIndicator: string;
  accountVersion: number;
  customerVersion: number;
}

/* ------------------------------------------------------------------ cards */

export interface CardRow {
  cardNumber: string;
  accountId: string;
  activeStatus: string;
  embossedName: string;
  expirationDate: string;
}

export interface CardDetail {
  cardNumber: string;
  accountId: string;
  embossedName: string;
  activeStatus: string;
  expirationDate: string;
  expirationMonth: string;
  expirationDay: string;
  expirationYear: string;
  version: number;
}

export interface CardUpdateRequest {
  accountId: string;
  cardNumber: string;
  embossedName: string;
  activeStatus: string;
  expirationMonth: string;
  expirationYear: string;
  version: number;
}

/* ------------------------------------------------------------------ transactions */

export interface TransactionRow {
  transactionId: string;
  date: string;
  description: string;
  amount: number;
}

export interface TransactionDetail {
  transactionId: string;
  cardNumber: string;
  typeCode: string;
  typeDescription: string;
  categoryCode: string;
  categoryDescription: string;
  source: string;
  description: string;
  amount: number;
  originDate: string;
  processDate: string;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantZip: string;
  accountId: string;
}

export interface TransactionAddRequest {
  accountId: string;
  cardNumber: string;
  typeCode: string;
  categoryCode: string;
  source: string;
  description: string;
  amount: string;
  originDate: string;
  processDate: string;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantZip: string;
  confirmed: boolean;
}

export interface TransactionPrefill {
  typeCode: string;
  categoryCode: string;
  source: string;
  description: string;
  amount: string;
  originDate: string;
  processDate: string;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantZip: string;
}

export interface TransactionWriteResult {
  transactionId: string;
  message: string;
  newBalance: number | null;
}

export interface BillPaymentView {
  accountId: string;
  currentBalance: number;
  cardNumber: string;
  payable: boolean;
  message: string | null;
}

/* ------------------------------------------------------------------ pending authorizations */

/** Account block of the {@code COPAU00} map, the IMS {@code PAUTSUM0} root segment. */
export interface PendingAuthSummaryView {
  accountId: string;
  customerId: string;
  customerName: string;
  /** The source labelled this field and never filled it; it comes from the account record here. */
  accountActiveStatus: string;
  authStatus: string;
  accountStatus: string;
  creditLimit: number;
  cashLimit: number;
  creditBalance: number;
  cashBalance: number;
  approvedAuthCount: number;
  declinedAuthCount: number;
  approvedAuthAmount: number;
  declinedAuthAmount: number;
  pendingCount: number;
  fraudCount: number;
}

/** One row of the authorization list. Every coded field arrives with its resolved text. */
export interface PendingAuthRow {
  authKey: string;
  authDate: string;
  authTime: string;
  cardNumber: string;
  transactionAmt: number;
  authRespCode: string;
  authRespText: string;
  matchStatus: string;
  matchStatusText: string;
  authFraud: string;
  fraudStatusText: string;
  merchantName: string;
}

/** Full authorization detail as the {@code COPAU01} map rendered it. */
export interface PendingAuthDetailView {
  accountId: string;
  authKey: string;
  cardNumber: string;
  authDate: string;
  authTime: string;
  authOrigDate: string;
  authOrigTime: string;
  authType: string;
  cardExpiryDate: string;
  messageType: string;
  messageSource: string;
  authIdCode: string;
  authRespCode: string;
  authRespText: string;
  authRespReason: string;
  authRespReasonText: string;
  processingCode: string;
  transactionAmt: number;
  approvedAmt: number;
  mccCode: string;
  acqrCountryCode: string;
  posEntryMode: string;
  posEntryModeText: string;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantState: string;
  merchantZip: string;
  transactionId: string;
  matchStatus: string;
  matchStatusText: string;
  authFraud: string;
  fraudStatusText: string;
  fraudRptDate: string;
  /** Key of the next authorization under the same account, absent at the end of the chain. */
  nextAuthKey?: string;
  /** Key of the previous authorization under the same account, absent at the start. */
  previousAuthKey?: string;
}

/** F5 on the detail screen: the intended state, not a blind toggle. */
export interface FraudMarkRequest {
  confirmed: boolean;
  note?: string;
}

export interface FraudMarkResult {
  message: string;
  detail: PendingAuthDetailView;
}

/* ------------------------------------------------------------------ users */

export interface UserRow {
  userId: string;
  firstName: string;
  lastName: string;
  userType: UserRole;
  active: boolean;
}

export interface UserDetail extends UserRow {
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface UserCreateRequest {
  firstName: string;
  lastName: string;
  userId: string;
  password: string;
  userType: UserRole;
}

export interface UserUpdateRequest {
  firstName: string;
  lastName: string;
  password: string;
  userType: UserRole;
  active: boolean;
  version: number;
}

/* ------------------------------------------------------------------ reference data */

export interface TransactionTypeDto {
  typeCode: string;
  description: string;
  version: number;
}

export interface TransactionCategoryDto {
  typeCode: string;
  categoryCode: string;
  description: string;
  version: number;
}

export interface DisclosureGroupDto {
  groupId: string;
  typeCode: string;
  categoryCode: string;
  interestRate: number;
}

export interface CategoryBalanceDto {
  accountId: string;
  typeCode: string;
  categoryCode: string;
  balance: number;
}

/* ------------------------------------------------------------------ reports and statements */

export type ReportType = 'MONTHLY' | 'YEARLY' | 'CUSTOM';

export interface ReportRequestInput {
  reportType: ReportType;
  startDate: string;
  endDate: string;
  confirmed: boolean;
}

export interface ReportRequestDto {
  id: number;
  reportType: ReportType;
  startDate: string;
  endDate: string;
  status: string;
  requestedBy: string;
  requestedAt: string;
  completedAt: string | null;
  lineCount: number;
}

export interface StatementDto {
  id: number;
  cardNumber: string;
  accountId: string;
  customerId: string;
  tranCount: number;
  totalAmount: number;
  generatedAt: string;
}

/* ------------------------------------------------------------------ batch and operations */

export interface BatchRunDto {
  id: number;
  jobName: string;
  parameters: string | null;
  status: string;
  returnCode: number;
  recordsRead: number;
  recordsAccepted: number;
  recordsRejected: number;
  message: string | null;
  startedBy: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export interface BatchRejectDto {
  recordNumber: number;
  transactionId: string;
  reasonCode: string;
  reasonText: string;
}

export interface MigrationLogDto {
  sourceFile: string;
  entity: string;
  codec: string;
  recordsRead: number;
  recordsLoaded: number;
  recordsFailed: number;
  detail: string | null;
  executedAt: string;
}

export interface AuditEventDto {
  id: number;
  actor: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  outcome: string;
  detail: string | null;
  createdAt: string;
}

export interface DashboardSummary {
  accountCount: number;
  customerCount: number;
  cardCount: number;
  transactionCount: number;
  /**
   * Security-file and batch figures. Omitted from the payload for a regular user, who can reach
   * neither the user list nor the batch console; the backend serialises with non-null inclusion,
   * so these arrive absent rather than null.
   */
  userCount?: number;
  pendingDailyTransactions?: number;
  totalBalance: number;
  totalCreditLimit: number;
  recentBatchRuns: BatchRunDto[];
  recentTransactions: TransactionRow[];
  transactionsByType: Record<string, number>;
}

/** Error envelope produced by the backend exception handler. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  field?: string;
  path: string;
}

import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import {
  AccountDetail,
  AccountSummary,
  AccountUpdateRequest,
  AuditEventDto,
  BatchRejectDto,
  BatchRunDto,
  BillPaymentView,
  CardDetail,
  CardRow,
  CardUpdateRequest,
  CategoryBalanceDto,
  DashboardSummary,
  DisclosureGroupDto,
  MenuView,
  MigrationLogDto,
  PageResult,
  ReportRequestDto,
  ReportRequestInput,
  StatementDto,
  TransactionAddRequest,
  TransactionCategoryDto,
  TransactionDetail,
  TransactionPrefill,
  TransactionRow,
  TransactionTypeDto,
  TransactionWriteResult,
  UserCreateRequest,
  UserDetail,
  UserRow,
  UserUpdateRequest,
} from './models';

/**
 * Every REST call the application makes, grouped by the COBOL transaction it replaces.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /* ------------------------------------------------------------------ menus (CM00 / CA00) */

  mainMenu(): Observable<MenuView> {
    return this.http.get<MenuView>(`${this.base}/menu/main`);
  }

  adminMenu(): Observable<MenuView> {
    return this.http.get<MenuView>(`${this.base}/menu/admin`);
  }

  /* ------------------------------------------------------------------ dashboard */

  dashboard(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.base}/dashboard`);
  }

  /* ------------------------------------------------------------------ accounts (CAVW / CAUP) */

  accounts(limit = 200): Observable<AccountSummary[]> {
    return this.http.get<AccountSummary[]>(`${this.base}/accounts`, {
      params: new HttpParams().set('limit', limit),
    });
  }

  account(accountId: string): Observable<AccountDetail> {
    return this.http.get<AccountDetail>(`${this.base}/accounts/${encodeURIComponent(accountId)}`);
  }

  updateAccount(accountId: string, payload: AccountUpdateRequest): Observable<AccountDetail> {
    return this.http.put<AccountDetail>(
      `${this.base}/accounts/${encodeURIComponent(accountId)}`,
      payload,
    );
  }

  /* ------------------------------------------------------------------ cards (CCLI / CCDL / CCUP) */

  cards(query: {
    accountId?: string;
    cardNumber?: string;
    cursor?: string | null;
    direction?: string;
    page?: number;
  }): Observable<PageResult<CardRow>> {
    let params = new HttpParams();
    if (query.accountId) {
      params = params.set('accountId', query.accountId);
    }
    if (query.cardNumber) {
      params = params.set('cardNumber', query.cardNumber);
    }
    if (query.cursor) {
      params = params.set('cursor', query.cursor);
    }
    if (query.direction) {
      params = params.set('direction', query.direction);
    }
    params = params.set('page', query.page ?? 1);
    return this.http.get<PageResult<CardRow>>(`${this.base}/cards`, { params });
  }

  cardsByAccount(accountId: string): Observable<CardRow[]> {
    return this.http.get<CardRow[]>(`${this.base}/cards/by-account/${encodeURIComponent(accountId)}`);
  }

  card(cardNumber: string): Observable<CardDetail> {
    return this.http.get<CardDetail>(`${this.base}/cards/${encodeURIComponent(cardNumber)}`);
  }

  cardForAccount(cardNumber: string, accountId: string): Observable<CardDetail> {
    return this.http.get<CardDetail>(
      `${this.base}/cards/${encodeURIComponent(cardNumber)}/for-account/${encodeURIComponent(accountId)}`,
    );
  }

  updateCard(cardNumber: string, payload: CardUpdateRequest): Observable<CardDetail> {
    return this.http.put<CardDetail>(
      `${this.base}/cards/${encodeURIComponent(cardNumber)}`,
      payload,
    );
  }

  /* ------------------------------------------------------------------ transactions (CT00 / CT01 / CT02) */

  transactions(query: {
    filter?: string;
    cursor?: string | null;
    direction?: string;
    page?: number;
  }): Observable<PageResult<TransactionRow>> {
    let params = new HttpParams();
    if (query.filter) {
      params = params.set('filter', query.filter);
    }
    if (query.cursor) {
      params = params.set('cursor', query.cursor);
    }
    if (query.direction) {
      params = params.set('direction', query.direction);
    }
    params = params.set('page', query.page ?? 1);
    return this.http.get<PageResult<TransactionRow>>(`${this.base}/transactions`, { params });
  }

  transaction(transactionId: string): Observable<TransactionDetail> {
    return this.http.get<TransactionDetail>(
      `${this.base}/transactions/${encodeURIComponent(transactionId)}`,
    );
  }

  transactionsByCard(cardNumber: string): Observable<TransactionRow[]> {
    return this.http.get<TransactionRow[]>(
      `${this.base}/transactions/by-card/${encodeURIComponent(cardNumber)}`,
    );
  }

  latestTransactionValues(): Observable<TransactionPrefill> {
    return this.http.get<TransactionPrefill>(`${this.base}/transactions/latest`);
  }

  addTransaction(payload: TransactionAddRequest): Observable<TransactionWriteResult> {
    return this.http.post<TransactionWriteResult>(`${this.base}/transactions`, payload);
  }

  /* ------------------------------------------------------------------ bill payment (CB00) */

  billPaymentView(accountId: string): Observable<BillPaymentView> {
    return this.http.get<BillPaymentView>(
      `${this.base}/transactions/bill-payment/${encodeURIComponent(accountId)}`,
    );
  }

  payBill(accountId: string, confirmed: boolean): Observable<TransactionWriteResult> {
    return this.http.post<TransactionWriteResult>(`${this.base}/transactions/bill-payment`, {
      accountId,
      confirmed,
    });
  }

  /* ------------------------------------------------------------------ user administration (CU00-CU03) */

  users(query: {
    filter?: string;
    cursor?: string | null;
    direction?: string;
    page?: number;
  }): Observable<PageResult<UserRow>> {
    let params = new HttpParams();
    if (query.filter) {
      params = params.set('filter', query.filter);
    }
    if (query.cursor) {
      params = params.set('cursor', query.cursor);
    }
    if (query.direction) {
      params = params.set('direction', query.direction);
    }
    params = params.set('page', query.page ?? 1);
    return this.http.get<PageResult<UserRow>>(`${this.base}/admin/users`, { params });
  }

  user(userId: string): Observable<UserDetail> {
    return this.http.get<UserDetail>(`${this.base}/admin/users/${encodeURIComponent(userId)}`);
  }

  createUser(payload: UserCreateRequest): Observable<UserDetail> {
    return this.http.post<UserDetail>(`${this.base}/admin/users`, payload);
  }

  updateUser(userId: string, payload: UserUpdateRequest): Observable<UserDetail> {
    return this.http.put<UserDetail>(
      `${this.base}/admin/users/${encodeURIComponent(userId)}`,
      payload,
    );
  }

  deleteUser(userId: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(
      `${this.base}/admin/users/${encodeURIComponent(userId)}`,
      { params: new HttpParams().set('confirm', true) },
    );
  }

  /* ------------------------------------------------------------------ reference data */

  transactionTypes(): Observable<TransactionTypeDto[]> {
    return this.http.get<TransactionTypeDto[]>(`${this.base}/reference/transaction-types`);
  }

  transactionCategories(): Observable<TransactionCategoryDto[]> {
    return this.http.get<TransactionCategoryDto[]>(`${this.base}/reference/transaction-categories`);
  }

  categoriesOfType(typeCode: string): Observable<TransactionCategoryDto[]> {
    return this.http.get<TransactionCategoryDto[]>(
      `${this.base}/reference/transaction-types/${encodeURIComponent(typeCode)}/categories`,
    );
  }

  disclosureGroups(): Observable<DisclosureGroupDto[]> {
    return this.http.get<DisclosureGroupDto[]>(`${this.base}/reference/disclosure-groups`);
  }

  categoryBalances(accountId?: string): Observable<CategoryBalanceDto[]> {
    let params = new HttpParams();
    if (accountId) {
      params = params.set('accountId', accountId);
    }
    return this.http.get<CategoryBalanceDto[]>(`${this.base}/reference/category-balances`, {
      params,
    });
  }

  /* ------------------------------------------------------------------ transaction type maintenance (CTLI / CTTU) */

  adminTransactionTypes(query: {
    typeCode?: string;
    description?: string;
    cursor?: string | null;
    direction?: string;
    page?: number;
  }): Observable<PageResult<TransactionTypeDto>> {
    let params = new HttpParams();
    if (query.typeCode) {
      params = params.set('typeCode', query.typeCode);
    }
    if (query.description) {
      params = params.set('description', query.description);
    }
    if (query.cursor) {
      params = params.set('cursor', query.cursor);
    }
    if (query.direction) {
      params = params.set('direction', query.direction);
    }
    params = params.set('page', query.page ?? 1);
    return this.http.get<PageResult<TransactionTypeDto>>(`${this.base}/admin/transaction-types`, {
      params,
    });
  }

  saveTransactionType(
    typeCode: string,
    description: string,
    version: number,
  ): Observable<TransactionTypeDto> {
    return this.http.put<TransactionTypeDto>(
      `${this.base}/admin/transaction-types/${encodeURIComponent(typeCode)}`,
      { description, version },
    );
  }

  deleteTransactionType(typeCode: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(
      `${this.base}/admin/transaction-types/${encodeURIComponent(typeCode)}`,
      { params: new HttpParams().set('confirm', true) },
    );
  }

  saveTransactionCategory(
    typeCode: string,
    categoryCode: string,
    description: string,
    version: number,
  ): Observable<TransactionCategoryDto> {
    return this.http.put<TransactionCategoryDto>(
      `${this.base}/admin/transaction-types/${encodeURIComponent(typeCode)}/categories/${encodeURIComponent(categoryCode)}`,
      { description, version },
    );
  }

  deleteTransactionCategory(typeCode: string, categoryCode: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(
      `${this.base}/admin/transaction-types/${encodeURIComponent(typeCode)}/categories/${encodeURIComponent(categoryCode)}`,
      { params: new HttpParams().set('confirm', true) },
    );
  }

  /* ------------------------------------------------------------------ reports and statements (CR00 / TRANREPT / CREASTMT) */

  submitReport(payload: ReportRequestInput): Observable<ReportRequestDto> {
    return this.http.post<ReportRequestDto>(`${this.base}/reports/requests`, payload);
  }

  reportRequests(all = false, limit = 50): Observable<ReportRequestDto[]> {
    return this.http.get<ReportRequestDto[]>(`${this.base}/reports/requests`, {
      params: new HttpParams().set('all', all).set('limit', limit),
    });
  }

  generateReport(id: number): Observable<ReportRequestDto> {
    return this.http.post<ReportRequestDto>(`${this.base}/reports/requests/${id}/generate`, {});
  }

  reportContent(id: number): Observable<string> {
    return this.http.get(`${this.base}/reports/requests/${id}/content`, { responseType: 'text' });
  }

  statements(limit = 50): Observable<StatementDto[]> {
    return this.http.get<StatementDto[]>(`${this.base}/reports/statements`, {
      params: new HttpParams().set('limit', limit),
    });
  }

  statementsByAccount(accountId: string): Observable<StatementDto[]> {
    return this.http.get<StatementDto[]>(
      `${this.base}/reports/statements/by-account/${encodeURIComponent(accountId)}`,
    );
  }

  statementText(id: number): Observable<string> {
    return this.http.get(`${this.base}/reports/statements/${id}/text`, { responseType: 'text' });
  }

  statementHtml(id: number): Observable<string> {
    return this.http.get(`${this.base}/reports/statements/${id}/html`, { responseType: 'text' });
  }

  /* ------------------------------------------------------------------ batch (POSTTRAN / INTCALC / TRANREPT / CREASTMT) */

  batchRuns(limit = 25): Observable<BatchRunDto[]> {
    return this.http.get<BatchRunDto[]>(`${this.base}/admin/batch/runs`, {
      params: new HttpParams().set('limit', limit),
    });
  }

  batchRejects(runId: number): Observable<BatchRejectDto[]> {
    return this.http.get<BatchRejectDto[]>(`${this.base}/admin/batch/runs/${runId}/rejects`);
  }

  pendingPostings(): Observable<{ pending: number }> {
    return this.http.get<{ pending: number }>(`${this.base}/admin/batch/posting/pending`);
  }

  runPosting(): Observable<BatchRunDto> {
    return this.http.post<BatchRunDto>(`${this.base}/admin/batch/posting`, {});
  }

  runInterest(cycleId: string): Observable<BatchRunDto> {
    return this.http.post<BatchRunDto>(`${this.base}/admin/batch/interest`, { cycleId });
  }

  runReports(): Observable<BatchRunDto> {
    return this.http.post<BatchRunDto>(`${this.base}/admin/batch/reports`, {});
  }

  runStatements(): Observable<BatchRunDto> {
    return this.http.post<BatchRunDto>(`${this.base}/admin/batch/statements`, {});
  }

  migrationLog(): Observable<MigrationLogDto[]> {
    return this.http.get<MigrationLogDto[]>(`${this.base}/admin/batch/migration`);
  }

  migrationCounts(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${this.base}/admin/batch/migration/counts`);
  }

  runMigration(): Observable<MigrationLogDto[]> {
    return this.http.post<MigrationLogDto[]>(`${this.base}/admin/batch/migration`, {});
  }

  /* ------------------------------------------------------------------ audit */

  auditEvents(limit = 100): Observable<AuditEventDto[]> {
    return this.http.get<AuditEventDto[]>(`${this.base}/admin/audit`, {
      params: new HttpParams().set('limit', limit),
    });
  }
}

/**
 * Extracts the backend message so a screen can show the same text the BMS ERRMSG field carried.
 */
export function errorMessage(error: unknown, fallback = 'Unable to complete the request ...'): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as { message?: string } | string | null;
    if (body && typeof body === 'object' && typeof body.message === 'string') {
      return body.message;
    }
    if (typeof body === 'string' && body.trim().length > 0) {
      return body;
    }
    if (error.status === 0) {
      return 'Unable to reach the CardDemo service. Please check that the backend is running.';
    }
  }
  return fallback;
}

/** Field name the backend flagged, so a screen can highlight the offending input. */
export function errorField(error: unknown): string | null {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as { field?: string } | null;
    if (body && typeof body === 'object' && typeof body.field === 'string') {
      return body.field;
    }
  }
  return null;
}

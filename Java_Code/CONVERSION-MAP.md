# COBOL → Java conversion map

Every artifact of [`../Cobol_Code`](../Cobol_Code) that carries behaviour, and where that behaviour
now lives. Paths are relative to `Java_Code/`.

---

## Online programs (CICS/BMS)

| COBOL program | Transaction | BMS map | Java service | Angular screen |
|---|---|---|---|---|
| `COSGN00C.cbl` | `CC00` | `COSGN00` | `service/AuthService.java` | `features/auth/login.ts` |
| `COMEN01C.cbl` | `CM00` | `COMEN01` | `service/MenuService.java` | `features/menu/menu.ts` |
| `COADM01C.cbl` | `CA00` | `COADM01` | `service/MenuService.java` | `features/menu/menu.ts` |
| `COACTVWC.cbl` | `CAVW` | `COACTVW` | `service/AccountService#view` | `features/accounts/account-view.ts` |
| `COACTUPC.cbl` | `CAUP` | `COACTUP` | `service/AccountService#update` | `features/accounts/account-update.ts` |
| `COCRDLIC.cbl` | `CCLI` | `COCRDLI` | `service/CardService#list` | `features/cards/card-list.ts` |
| `COCRDSLC.cbl` | `CCDL` | `COCRDSL` | `service/CardService#view` | `features/cards/card-view.ts` |
| `COCRDUPC.cbl` | `CCUP` | `COCRDUP` | `service/CardService#update` | `features/cards/card-update.ts` |
| `COTRN00C.cbl` | `CT00` | `COTRN00` | `service/TransactionService#list` | `features/transactions/transaction-list.ts` |
| `COTRN01C.cbl` | `CT01` | `COTRN01` | `service/TransactionService#view` | `features/transactions/transaction-view.ts` |
| `COTRN02C.cbl` | `CT02` | `COTRN02` | `service/TransactionService#add` | `features/transactions/transaction-add.ts` |
| `CORPT00C.cbl` | `CR00` | `CORPT00` | `batch/ReportService#submit` | `features/reports/report-request.ts` |
| `COBIL00C.cbl` | `CB00` | `COBIL00` | `service/BillPaymentService` | `features/transactions/bill-payment.ts` |
| `COUSR00C.cbl` | `CU00` | `COUSR00` | `service/UserAdminService#list` | `features/admin/user-list.ts` |
| `COUSR01C.cbl` | `CU01` | `COUSR01` | `service/UserAdminService#create` | `features/admin/user-add.ts` |
| `COUSR02C.cbl` | `CU02` | `COUSR02` | `service/UserAdminService#update` | `features/admin/user-update.ts` |
| `COUSR03C.cbl` | `CU03` | `COUSR03` | `service/UserAdminService#delete` | `features/admin/user-delete.ts` |
| `COTRTLIC.cbl` | `CTLI` | `COTRTLI` | `service/ReferenceDataService#listTypes` | `features/admin/transaction-type-admin.ts` |
| `COTRTUPC.cbl` | `CTTU` | `COTRTUP` | `service/ReferenceDataService#saveType` | `features/admin/transaction-type-admin.ts` |
| `COPAUS0C.cbl` | `CPVS` | `COPAU00` | `service/PendingAuthService#summary` + `#list` | `features/pending-auth/pending-auth-list.ts` |
| `COPAUS1C.cbl` | `CPVD` | `COPAU01` | `service/PendingAuthService#detail` | `features/pending-auth/pending-auth-detail.ts` |
| `COPAUS2C.cbl` | — | — | `service/PendingAuthService#markFraud` | (the fraud action of the detail screen) |

## Batch programs

| COBOL program | JCL job | Java implementation |
|---|---|---|
| `CBTRN02C.cbl` | `POSTTRAN` | `batch/PostingService` + `batch/PostingRecordProcessor` |
| `CBACT04C.cbl` | `INTCALC` | `batch/InterestService` + `batch/InterestAccountProcessor` |
| `CBTRN03C.cbl` | `TRANREPT` | `batch/ReportService#render` + `batch/ReportFormatter` |
| `CBSTM03A.CBL` / `CBSTM03B.CBL` | `CREASTMT` | `batch/StatementService` |
| `CBACT01C` / `CBACT02C` / `CBACT03C` / `CBCUS01C` | `READACCT` / `READCARD` / `READXREF` / `READCUST` | Diagnostic readers; replaced by the reference and list REST endpoints |
| `CBTRN01C.cbl` | — | Prototype only; posting is `CBTRN02C` |
| `CBEXPORT.cbl` / `CBIMPORT.cbl` | `CBEXPORT` / `CBIMPORT` | Interchange format is out of the online/batch scope of this conversion; the byte codec helpers it needs are in `common/CobolText` |
| `ACCTFILE` / `CARDFILE` / `CUSTFILE` / `XREFFILE` / `TRANTYPE` / `TRANCATG` / `DISCGRP` / `TCATBALF` / `DUSRSECJ` | load jobs | `migration/CobolDataMigrationService` |
| `COMBTRAN` / `TRANBKP` / `TRANIDX` | cycle jobs | Replaced by the relational transaction table and its indexes |
| `CLOSEFIL` / `OPENFIL` | operations | Replaced by transactional isolation; no file close is needed |

## Copybooks

### Record layouts

| Copybook | Data set | Java entity | Table |
|---|---|---|---|
| `CSUSR01Y.cpy` | `USRSEC` (80) | `domain/AppUser` | `app_user` |
| `CVCUS01Y.cpy` | `CUSTDAT` (500) | `domain/Customer` | `customer` |
| `CVACT01Y.cpy` | `ACCTDAT` (300) | `domain/Account` | `account` |
| `CVACT02Y.cpy` | `CARDDAT` (150) | `domain/Card` | `card` |
| `CVACT03Y.cpy` | `CCXREF` (50) | `domain/CardXref` | `card_xref` |
| `CVTRA05Y.cpy` | `TRANSACT` (350) | `domain/Transaction` | `transaction` |
| `CVTRA06Y.cpy` | `DALYTRAN` (350) | `domain/DailyTransaction` | `daily_transaction` |
| `CVTRA03Y.cpy` | `TRANTYPE` (60) | `domain/TransactionType` | `transaction_type` |
| `CVTRA04Y.cpy` | `TRANCATG` (60) | `domain/TransactionCategory` | `transaction_category` |
| `CVTRA01Y.cpy` | `TCATBALF` (50) | `domain/CategoryBalance` | `category_balance` |
| `CVTRA02Y.cpy` | `DISCGRP` (50) | `domain/DisclosureGroup` | `disclosure_group` |
| `CVTRA07Y.cpy` | report (133) | `batch/ReportFormatter` | — |
| `COSTM01.CPY` | statement work file | `batch/StatementService` | `account_statement` |
| `CIPAUSMY.cpy` | IMS `PAUTSUM0` (100) | `domain/PendingAuthSummary` | `pending_auth_summary` |
| `CIPAUDTY.cpy` | IMS `PAUTDTL1` (200) | `domain/PendingAuthDetail` | `pending_auth_detail` |
| `AUTHFRDS.dcl` | Db2 `CARDDEMO.AUTHFRDS` | `domain/AuthFraud` | `auth_fraud` |

### Logic and reference copybooks

| Copybook | Contents | Java counterpart |
|---|---|---|
| `CSUTLDPY.cpy` | Date component edits, century rule, leap rule, DOB reasonableness | `validation/CobolDateValidator` |
| `CSUTLDTC.cbl` | Callable date check | `validation/CobolDateValidator#isRealCalendarDate` |
| `CSLKPCDY.cpy` | Phone area codes, US state codes, state/ZIP combinations | `validation/LookupTables` + `resources/cobol-lookup/*.txt` |
| `COMEN02Y.cpy` | Eleven main-menu options | `service/MenuService.MAIN_MENU` |
| `COADM02Y.cpy` | Six administrator-menu options | `service/MenuService.ADMIN_MENU` |
| `COCOM01Y.cpy` | 160-byte COMMAREA, role values `A`/`U` | JWT claims plus `web/CurrentUser` |
| `CSMSG01Y.cpy` | Common messages | Message literals in the services and screens |
| `CSSTRPFY.cpy` | PF-key mapping | Function-key bar in each Angular screen |

The reusable field edits of `COACTUPC.cbl` (`1215-EDIT-MANDATORY` through `1265-EDIT-US-SSN`) are in
`validation/CobolFieldValidator`.

## Infrastructure replacements

| Legacy mechanism | Java replacement |
|---|---|
| CICS `XCTL` between programs | Angular router navigation |
| 160-byte COMMAREA | Signed JWT plus typed request payloads |
| BMS `SEND MAP` / `RECEIVE MAP` | Angular templates bound to typed DTOs |
| `STARTBR` / `READNEXT` / `READPREV` | Keyset paging queries returning `dto/PageResult` |
| `READ UPDATE` / `REWRITE` | JPA optimistic locking with `@Version` |
| VSAM alternate index `CXACAIX` | Indexed `card_xref.account_id` column |
| `WRITEQ TD JOBS` (report submission) | `report_request` table consumed by the report job |
| GDG reject data set `DALYREJS` | `batch_reject` table |
| JES job return codes | `batch_run` table with the same completion codes |
| `CEE3ABD` abend | Exception plus a failed `batch_run` row |
| IMS HIDAM database `DBPAUTP0`, root plus child segment | `pending_auth_summary` and `pending_auth_detail` joined by a foreign key |
| DL/I `GU` / `GNP` position and twin-chain scan | Ordered keyset reads on the nine's complement `auth_key` |
| IMS and Db2 committed together by `EXEC CICS SYNCPOINT` | One local transaction over both tables |
| IMS HD unload `AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat` | `migration/PendingAuthMigrationService` decoding it with `common/CobolBinary` |
| RACF / `RESSEC` / `CMDSEC` | Spring Security URL rules plus `@PreAuthorize` per use case |

## REST surface

| Area | Base path |
|---|---|
| Authentication | `/api/auth` |
| Menus | `/api/menu` |
| Accounts | `/api/accounts` |
| Cards | `/api/cards` |
| Transactions and bill payment | `/api/transactions` |
| Pending authorizations | `/api/pending-authorizations` |
| Reference data | `/api/reference` |
| Reports and statements | `/api/reports` |
| Dashboard | `/api/dashboard` |
| User administration | `/api/admin/users` |
| Transaction type maintenance | `/api/admin/transaction-types` |
| Batch operations and migration | `/api/admin/batch` |
| Audit trail | `/api/admin/audit` |

Full contract at `http://localhost:8080/swagger-ui.html`.

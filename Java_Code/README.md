# CardDemo — Java full-stack conversion

Java replacement for the **AWS Mainframe Modernization CardDemo** COBOL application that lives in
[`../Cobol_Code`](../Cobol_Code), built from the COBOL sources together with the specification in
[`../Documentation`](../Documentation).

```
Angular 20 frontend  ──HTTP/JWT──▶  Spring Boot 3.4 backend  ──JDBC──▶  PostgreSQL
   Frontend/                            Backend/                     cobol_to_java_app
```

The legacy estate was a CICS/BMS online application over VSAM files plus a z/OS batch estate. Every
screen became an Angular route, every COBOL program became a Spring service, every VSAM data set
became a PostgreSQL table, and the shipped fixture data was migrated into that database on first
start. There is **no hardcoded application data**: accounts, cards, customers, transactions, users,
reference tables and rates are all read from PostgreSQL through the REST API.

---

## Contents

| Path | What it is |
|---|---|
| `Backend/` | Spring Boot 3.4 service (Java 21, Maven) — REST API, business logic, batch jobs, data migration |
| `Frontend/` | Angular 20 application (standalone components, signals, lazy routes) |
| `CONVERSION-MAP.md` | Every COBOL program, copybook and JCL job mapped to its Java counterpart |
| `run-backend.ps1` / `run-frontend.ps1` | One-command start scripts |

---

## Prerequisites

| Tool | Version used | Notes |
|---|---|---|
| JDK | 21 | `JAVA_HOME` must point at it |
| Maven | 3.9+ | |
| Node.js | 20.19+ / 22.12+ / 24+ | Angular 20 requirement |
| PostgreSQL | 13+ | The configured instance is 18.2 |

## Database

The backend is configured for the supplied instance:

| Setting | Value |
|---|---|
| Host / port | `217.217.251.161:8100` |
| Database | `cobol_to_java_app` |
| User | `cobol_to_java_user` |

Credentials come from `Backend/src/main/resources/application.yml` and can be overridden with the
`DB_URL`, `DB_USER` and `DB_PASSWORD` environment variables. Flyway creates the schema
(`db/migration/V1__carddemo_schema.sql`) on first start; Hibernate then only validates it.

## Running

```powershell
# 1. Backend  (http://localhost:8080, Swagger at /swagger-ui.html)
cd Backend
mvn spring-boot:run

# 2. Frontend (http://localhost:4200)
cd Frontend
npm install
npm start
```

Or use the helper scripts from this folder: `.\run-backend.ps1` and `.\run-frontend.ps1`.

On the very first start the backend migrates the COBOL data sets into PostgreSQL and logs the
result. Subsequent starts detect the existing data and skip the migration.

## Signing on

The ten security users from `AWS.M2.CARDDEMO.USRSEC.PS` are migrated with their names and roles.
The legacy record held a recoverable eight-character plaintext password; that is **not** reproduced.
Every migrated account instead receives a bcrypt hash of the migration password configured by
`carddemo.migration.legacy-password` (default `PASSWORD1`), and users are expected to change it.

| User | Role | Lands on |
|---|---|---|
| `ADMIN001` … `ADMIN005` | `A` administrator | Administrator menu |
| `USER0001` … `USER0005` | `U` regular user | Main menu |

New users can also self-register through **Sign up**, which always creates a regular `U` user; the
administrator role can only be granted from the user administration screens.

---

## Screens

Seventeen base CICS screens plus the optional transaction-type module, each keeping its transaction
id and program name in the screen header.

| Route | Transaction | COBOL program | Purpose |
|---|---|---|---|
| `/login` | `CC00` | `COSGN00C` | Sign on |
| `/main-menu` | `CM00` | `COMEN01C` | Main menu (11 options from `COMEN02Y`) |
| `/admin-menu` | `CA00` | `COADM01C` | Administrator menu (6 options from `COADM02Y`) |
| `/accounts/view` | `CAVW` | `COACTVWC` | View account and customer |
| `/accounts/update` | `CAUP` | `COACTUPC` | Update account and customer |
| `/cards` | `CCLI` | `COCRDLIC` | Card list, 7 rows, F7/F8 paging |
| `/cards/view` | `CCDL` | `COCRDSLC` | View card |
| `/cards/update` | `CCUP` | `COCRDUPC` | Update card |
| `/transactions` | `CT00` | `COTRN00C` | Transaction list, 10 rows |
| `/transactions/view` | `CT01` | `COTRN01C` | View transaction |
| `/transactions/add` | `CT02` | `COTRN02C` | Add transaction |
| `/reports` | `CR00` | `CORPT00C` | Request a transaction report |
| `/bill-payment` | `CB00` | `COBIL00C` | Pay the full account balance |
| `/admin/users` | `CU00` | `COUSR00C` | Security user list |
| `/admin/users/add` | `CU01` | `COUSR01C` | Add security user |
| `/admin/users/update` | `CU02` | `COUSR02C` | Update security user |
| `/admin/users/delete` | `CU03` | `COUSR03C` | Delete security user |
| `/admin/transaction-types` | `CTLI` / `CTTU` | `COTRTLIC` / `COTRTUPC` | Transaction type maintenance |
| `/admin/batch` | — | `CBTRN02C` … | Batch operations console |
| `/statements` | — | `CBSTM03A` | Generated card statements |
| `/reference` | — | reference copybooks | Types, categories, rates, balances |
| `/admin/audit` | — | — | Audit trail |
| `/dashboard` | — | — | Portfolio overview |

## Batch jobs

Run from **Batch operations** (`/admin/batch`) or the REST API.

| Job | COBOL program | What it does |
|---|---|---|
| `POSTTRAN` | `CBTRN02C` | Posts the daily transaction file: validates card, account, credit limit and expiry in source order, then updates category balance, account and transaction master |
| `INTCALC` | `CBACT04C` | Monthly interest: `balance × rate ÷ 1200` per category, one system transaction each, then the account balance is updated and the cycle accumulators reset |
| `TRANREPT` | `CBTRN03C` | Renders queued report requests as fixed 133-column output |
| `CREASTMT` | `CBSTM03A` | Produces the 80-column text and escaped HTML statement for every card |

### Verified against the documented fixture oracle

The posting run over the supplied 300-record daily file reproduces
[the deterministic oracle](../Documentation/05-Batch-Processing.md#deterministic-fixture-oracle)
exactly:

| Measure | Documented | Produced |
|---|---:|---:|
| processed | 300 | 300 |
| accepted | 262 | 262 |
| rejected | 38 | 38 |
| reject reasons | all `0102` | all `0102` |
| accepted amount | 77,954.70 | 77,954.70 |
| cycle credit | 102,353.99 | 102,353.99 |
| cycle debit | -24,399.29 | -24,399.29 |
| final account-balance sum | 90,223.70 | 90,223.70 |
| transaction rows | 262 | 262 |
| category-balance rows | 100 | 100 |
| return code | 4 | 4 |

Representative account vectors match too:

| Account | Accepted | Purchase balance | Credit balance | Posted balance |
|---|---:|---:|---:|---:|
| `00000000001` | 4 | 1,164.87 | -70.77 | 1,288.10 |
| `00000000017` | 2 | 343.77 | -998.33 | -621.56 |
| `00000000030` | 2 | 29.44 | -930.33 | -898.89 |
| `00000000037` | 1 | 0.00 | -132.88 | -125.88 |
| `00000000050` | 6 | 1,501.75 | -47.88 | 1,945.87 |

---

## Data migration

`CobolDataMigrationService` reads the shipped data sets and writes them to PostgreSQL using the byte
offsets of each copybook. Provenance for every file is recorded in `migration_log` and shown on the
batch console.

| Source file | Copybook | Table | Records |
|---|---|---|---:|
| `EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS` | `CSUSR01Y` | `app_user` | 10 |
| `ASCII/custdata.txt` | `CVCUS01Y` | `customer` | 50 |
| `ASCII/acctdata.txt` | `CVACT01Y` | `account` | 50 |
| `ASCII/carddata.txt` | `CVACT02Y` | `card` | 50 |
| `ASCII/cardxref.txt` | `CVACT03Y` | `card_xref` | 50 |
| `ASCII/trantype.txt` | `CVTRA03Y` | `transaction_type` | 7 |
| `ASCII/trancatg.txt` | `CVTRA04Y` | `transaction_category` | 18 |
| `ASCII/discgrp.txt` | `CVTRA02Y` | `disclosure_group` | 51 |
| `ASCII/tcatbal.txt` | `CVTRA01Y` | `category_balance` | 50 |
| `ASCII/dailytran.txt` | `CVTRA06Y` | `daily_transaction` | 300 |

Codec facts the migration honours:

- **Overpunch signs.** `0000005047G` is `+504.77`, `0000009190}` is `-919.00`; `{`/`A`–`I` encode a
  positive final digit 0–9 and `}`/`J`–`R` a negative one.
- **Leading zeroes.** Identifiers stay `VARCHAR` at their exact COBOL width, so `00000000001` never
  becomes `1`.
- **Money.** Amounts are `NUMERIC(n,2)` and `BigDecimal`, never binary floating point.
- **Explicit encodings.** ASCII fixtures are read as ISO-8859-1, the EBCDIC user file as IBM037.
  The process default encoding never decides a record format.
- **Short records.** The ASCII cross-reference fixture omits the 14-byte filler, so records are
  treated as space padded.

The lookup tables of `CSLKPCDY.cpy` — 490 phone area codes, 410 general-purpose codes, 56 state
codes and 240 state/ZIP combinations — were extracted into
`Backend/src/main/resources/cobol-lookup/` and are loaded at startup rather than hardcoded.

---

## Security

The legacy application had no real security model: plaintext passwords in an 80-byte record, a
mutable 160-byte COMMAREA carrying the role, `RESSEC(NO)`/`CMDSEC(NO)` on every transaction, and
menus that trusted the arrival route. The Java target replaces that with:

- **bcrypt password hashes.** No recoverable credential is stored, returned, logged or displayed.
- **Signed JWT sessions.** Identity and role live in a token, not in editable screen state. The role
  is re-read from the database on every request, so a role change or disable takes effect at once.
- **Use-case authorisation.** Administrator endpoints carry `@PreAuthorize` in addition to the URL
  rule, so a hidden menu entry is never the only control.
- **Generic sign-on failure** plus failed-attempt counting and temporary lockout, replacing the
  source's distinct "User not found" and "Wrong Password" messages.
- **Audit events** for sign-on, sign-off, every user and reference mutation, account and card
  updates, transactions, payments and batch runs — with sensitive fields redacted.
- **Optimistic concurrency** on account, customer, card, user and reference rows.

## Source defects corrected

Behaviour observed in the COBOL that the Java target deliberately does not reproduce, each traced
to the requirement that calls for the correction:

| Source behaviour | Correction |
|---|---|
| Account update layout shifted the disclosure group into the ZIP bytes and blanked the true group | ZIP and group are stored in their own columns (FR-ACCT-009) |
| Card view/update validated the account then read by card key alone | The card must belong to the entered account (FR-CARD-004) |
| Card update wrote a "new CVV" field that was never assigned | CVV is preserved and never returned (FR-CARD-007) |
| Card expiry month/year changed without validating the retained hidden day | The complete date is validated before saving (FR-CARD-006) |
| Transaction view issued `READ UPDATE` on a read-only screen | The view takes no lock (FR-TRAN-003) |
| Transaction ids came from "highest id + 1" with no serialisation | An atomic allocator keeps the 16-character format without the race (FR-TRAN-009) |
| Validation was enforced by send-screen-then-`RETURN` control flow | Mutation is gated on an explicit validated-state check (FR-TRAN-007) |
| Bill payment continued after xref, browse and write errors | Insert and balance update are one atomic unit (FR-BILL-005) |
| Report request wrote user input into 80-byte JCL lines on a TDQ | A structured durable request row is stored instead (FR-RPT-003) |
| Custom report converted dates before validating them, and allowed a reversed range | Format is validated before conversion and `start ≤ end` is enforced (FR-RPT-002) |
| Card list look-ahead ignored the filters, so F8 could open an empty page | `hasNext` comes from the next matching row (FR-CARD-002) |
| User update PF3 saved and then navigated away even after a failed save | Save and Return are separate actions (FR-USER-005) |
| User delete had no confirmation, self-delete check or last-admin guard | All three are enforced (FR-USER-006) |
| Phone "all blank" test referenced part A where part C was intended | All three parts are checked explicitly |
| Posting applied three file changes non-atomically per record | One transaction per input row plus an idempotency ledger (FR-BATCH-005) |
| Interest never applied the final account at end of file | Every account is applied, including the last (FR-BATCH-007) |
| Report duplicated the last amount at EOF and omitted the final card total | Both corrected (FR-BATCH-009) |
| Statements capped at 51 cards × 10 transactions and emitted unescaped HTML | No cap; HTML is escaped (FR-BATCH-010) |

Behaviour deliberately **kept** from the source: the validation order and message text of every
screen, the option tables, the posting reject reasons and arithmetic, the interest formula and its
truncation, the bill-payment transaction literals, the report and statement layouts, and the
`A`/`U` role routing.

## Parity notes

- **Non-unique alternate index.** `CXACAIX` can return more than one card for an account and the
  source had no tie-break. The Java services use the lowest card number so the result is
  reproducible.
- **Fee calculation** stays an explicit no-op: `1400-COMPUTE-FEES` is an empty paragraph in the
  source and FR-BATCH-008 requires it to remain so.
- **Pending authorization view** (main-menu option 11) belongs to the optional IMS/Db2/MQ extension.
  The screen reports "not installed", which is the source navigation result (FR-OPT-017).

## Tests

```powershell
cd Backend
mvn test
```

44 tests characterise the COBOL codec and business rules against values taken from the shipped
fixtures: overpunch parsing and round-tripping, leading-zero preservation, the `CSUTLDPY` date and
leap rules, the `CSLKPCDY` lookup tables, the SSN/FICO/phone/state-ZIP edits, `NUMVAL-C` grammar,
the posting credit-limit arithmetic, the interest truncation, the transaction amount picture and
the 133-column report layout.

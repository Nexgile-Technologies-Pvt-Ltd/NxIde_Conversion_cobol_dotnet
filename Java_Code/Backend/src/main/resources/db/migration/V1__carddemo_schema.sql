-- =====================================================================================
-- CardDemo relational schema.
--
-- Every table below is the relational form of a COBOL copybook / VSAM data set from
-- Cobol_Code/aws-mainframe-modernization-carddemo. Legacy identifiers stay VARCHAR at
-- their exact COBOL width so leading zeroes survive (DATA-002); money is NUMERIC so
-- cents are exact (DATA-004).
--
--   app_user            <- CSUSR01Y.cpy  (USRSEC  , 80 bytes)
--   customer            <- CVCUS01Y.cpy  (CUSTDAT , 500 bytes)
--   account             <- CVACT01Y.cpy  (ACCTDAT , 300 bytes)
--   card                <- CVACT02Y.cpy  (CARDDAT , 150 bytes)
--   card_xref           <- CVACT03Y.cpy  (CCXREF  , 50 bytes)
--   transaction         <- CVTRA05Y.cpy  (TRANSACT, 350 bytes)
--   daily_transaction   <- CVTRA06Y.cpy  (DALYTRAN, 350 bytes)
--   transaction_type    <- CVTRA03Y.cpy  (TRANTYPE, 60 bytes)
--   transaction_category<- CVTRA04Y.cpy  (TRANCATG, 60 bytes)
--   category_balance    <- CVTRA01Y.cpy  (TCATBALF, 50 bytes)
--   disclosure_group    <- CVTRA02Y.cpy  (DISCGRP , 50 bytes)
-- =====================================================================================

-- ------------------------------------------------------------------ security users
-- COBOL: CSUSR01Y.cpy. SEC-USR-PWD X(8) plaintext is NOT reproduced; the safe target
-- stores only a hash (FR-AUTH-003).
CREATE TABLE app_user (
    user_id         VARCHAR(8)   PRIMARY KEY,
    first_name      VARCHAR(20)  NOT NULL,
    last_name       VARCHAR(20)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    user_type       VARCHAR(1)   NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP    NULL,
    last_login_at   TIMESTAMP    NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_app_user_type CHECK (user_type IN ('A', 'U'))
);

-- ------------------------------------------------------------------ customers
CREATE TABLE customer (
    customer_id           VARCHAR(9)  PRIMARY KEY,
    first_name            VARCHAR(25) NOT NULL DEFAULT '',
    middle_name           VARCHAR(25) NOT NULL DEFAULT '',
    last_name             VARCHAR(25) NOT NULL DEFAULT '',
    addr_line_1           VARCHAR(50) NOT NULL DEFAULT '',
    addr_line_2           VARCHAR(50) NOT NULL DEFAULT '',
    addr_line_3           VARCHAR(50) NOT NULL DEFAULT '',
    addr_state_cd         VARCHAR(2)  NOT NULL DEFAULT '',
    addr_country_cd       VARCHAR(3)  NOT NULL DEFAULT '',
    addr_zip              VARCHAR(10) NOT NULL DEFAULT '',
    phone_num_1           VARCHAR(15) NOT NULL DEFAULT '',
    phone_num_2           VARCHAR(15) NOT NULL DEFAULT '',
    ssn                   VARCHAR(9)  NOT NULL DEFAULT '',
    govt_issued_id        VARCHAR(20) NOT NULL DEFAULT '',
    date_of_birth         VARCHAR(10) NOT NULL DEFAULT '',
    eft_account_id        VARCHAR(10) NOT NULL DEFAULT '',
    pri_card_holder_ind   VARCHAR(1)  NOT NULL DEFAULT 'N',
    fico_score            INTEGER     NOT NULL DEFAULT 0,
    created_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX ix_customer_last_name ON customer (last_name);

-- ------------------------------------------------------------------ accounts
-- ACCT-ADDR-ZIP and ACCT-GROUP-ID are stored in their own correct columns
-- (FR-ACCT-009: the source update layout that shifts group into ZIP is never emitted).
CREATE TABLE account (
    account_id            VARCHAR(11)   PRIMARY KEY,
    active_status         VARCHAR(1)    NOT NULL DEFAULT 'Y',
    curr_bal              NUMERIC(14,2) NOT NULL DEFAULT 0,
    credit_limit          NUMERIC(14,2) NOT NULL DEFAULT 0,
    cash_credit_limit     NUMERIC(14,2) NOT NULL DEFAULT 0,
    open_date             VARCHAR(10)   NOT NULL DEFAULT '',
    expiration_date       VARCHAR(10)   NOT NULL DEFAULT '',
    reissue_date          VARCHAR(10)   NOT NULL DEFAULT '',
    curr_cyc_credit       NUMERIC(14,2) NOT NULL DEFAULT 0,
    curr_cyc_debit        NUMERIC(14,2) NOT NULL DEFAULT 0,
    addr_zip              VARCHAR(10)   NOT NULL DEFAULT '',
    group_id              VARCHAR(10)   NOT NULL DEFAULT '',
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_account_group ON account (group_id);

-- ------------------------------------------------------------------ cards
CREATE TABLE card (
    card_number     VARCHAR(16) PRIMARY KEY,
    account_id      VARCHAR(11) NOT NULL,
    cvv_code        VARCHAR(3)  NOT NULL DEFAULT '000',
    embossed_name   VARCHAR(50) NOT NULL DEFAULT '',
    expiration_date VARCHAR(10) NOT NULL DEFAULT '',
    active_status   VARCHAR(1)  NOT NULL DEFAULT 'Y',
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_card_account FOREIGN KEY (account_id) REFERENCES account (account_id)
);
CREATE INDEX ix_card_account ON card (account_id);

-- ------------------------------------------------------------------ card cross-reference
-- Primary key card number, non-unique alternate key account id (CICS path CXACAIX).
CREATE TABLE card_xref (
    card_number VARCHAR(16) PRIMARY KEY,
    customer_id VARCHAR(9)  NOT NULL,
    account_id  VARCHAR(11) NOT NULL,
    CONSTRAINT fk_xref_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id),
    CONSTRAINT fk_xref_account  FOREIGN KEY (account_id)  REFERENCES account (account_id)
);
CREATE INDEX ix_xref_account  ON card_xref (account_id);
CREATE INDEX ix_xref_customer ON card_xref (customer_id);

-- ------------------------------------------------------------------ transaction reference data
CREATE TABLE transaction_type (
    type_code   VARCHAR(2)  PRIMARY KEY,
    description VARCHAR(50) NOT NULL DEFAULT '',
    version     BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE transaction_category (
    type_code     VARCHAR(2)  NOT NULL,
    category_code VARCHAR(4)  NOT NULL,
    description   VARCHAR(50) NOT NULL DEFAULT '',
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_transaction_category PRIMARY KEY (type_code, category_code),
    CONSTRAINT fk_trancat_type FOREIGN KEY (type_code) REFERENCES transaction_type (type_code)
);

-- ------------------------------------------------------------------ transactions
CREATE TABLE transaction (
    transaction_id VARCHAR(16)   PRIMARY KEY,
    type_code      VARCHAR(2)    NOT NULL DEFAULT '',
    category_code  VARCHAR(4)    NOT NULL DEFAULT '',
    source         VARCHAR(10)   NOT NULL DEFAULT '',
    description    VARCHAR(100)  NOT NULL DEFAULT '',
    amount         NUMERIC(13,2) NOT NULL DEFAULT 0,
    merchant_id    VARCHAR(9)    NOT NULL DEFAULT '',
    merchant_name  VARCHAR(50)   NOT NULL DEFAULT '',
    merchant_city  VARCHAR(50)   NOT NULL DEFAULT '',
    merchant_zip   VARCHAR(10)   NOT NULL DEFAULT '',
    card_number    VARCHAR(16)   NOT NULL DEFAULT '',
    orig_ts        VARCHAR(26)   NOT NULL DEFAULT '',
    proc_ts        VARCHAR(26)   NOT NULL DEFAULT '',
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_transaction_card    ON transaction (card_number);
CREATE INDEX ix_transaction_proc_ts ON transaction (proc_ts);
CREATE INDEX ix_transaction_type    ON transaction (type_code, category_code);

-- ------------------------------------------------------------------ daily transaction staging
-- Input to CBTRN02C posting. Kept separate from the master exactly like DALYTRAN vs TRANSACT.
CREATE TABLE daily_transaction (
    id             BIGSERIAL     PRIMARY KEY,
    transaction_id VARCHAR(16)   NOT NULL,
    type_code      VARCHAR(2)    NOT NULL DEFAULT '',
    category_code  VARCHAR(4)    NOT NULL DEFAULT '',
    source         VARCHAR(10)   NOT NULL DEFAULT '',
    description    VARCHAR(100)  NOT NULL DEFAULT '',
    amount         NUMERIC(13,2) NOT NULL DEFAULT 0,
    merchant_id    VARCHAR(9)    NOT NULL DEFAULT '',
    merchant_name  VARCHAR(50)   NOT NULL DEFAULT '',
    merchant_city  VARCHAR(50)   NOT NULL DEFAULT '',
    merchant_zip   VARCHAR(10)   NOT NULL DEFAULT '',
    card_number    VARCHAR(16)   NOT NULL DEFAULT '',
    orig_ts        VARCHAR(26)   NOT NULL DEFAULT '',
    proc_ts        VARCHAR(26)   NOT NULL DEFAULT '',
    record_number  INTEGER       NOT NULL DEFAULT 0,
    processed      BOOLEAN       NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_daily_tran_processed ON daily_transaction (processed, record_number);

-- ------------------------------------------------------------------ category balances
CREATE TABLE category_balance (
    account_id    VARCHAR(11)   NOT NULL,
    type_code     VARCHAR(2)    NOT NULL,
    category_code VARCHAR(4)    NOT NULL,
    balance       NUMERIC(13,2) NOT NULL DEFAULT 0,
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_category_balance PRIMARY KEY (account_id, type_code, category_code)
);

-- ------------------------------------------------------------------ disclosure rates
CREATE TABLE disclosure_group (
    group_id      VARCHAR(10)  NOT NULL,
    type_code     VARCHAR(2)   NOT NULL,
    category_code VARCHAR(4)   NOT NULL,
    interest_rate NUMERIC(7,2) NOT NULL DEFAULT 0,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_disclosure_group PRIMARY KEY (group_id, type_code, category_code)
);

-- ------------------------------------------------------------------ transaction id allocator
-- FR-TRAN-009: replaces the legacy "browse to highest id and add one" race.
CREATE TABLE id_sequence (
    sequence_name VARCHAR(40) PRIMARY KEY,
    next_value    BIGINT      NOT NULL
);

-- ------------------------------------------------------------------ batch bookkeeping
CREATE TABLE batch_run (
    id                BIGSERIAL   PRIMARY KEY,
    job_name          VARCHAR(40) NOT NULL,
    parameters        TEXT        NULL,
    status            VARCHAR(20) NOT NULL,
    return_code       INTEGER     NOT NULL DEFAULT 0,
    records_read      INTEGER     NOT NULL DEFAULT 0,
    records_accepted  INTEGER     NOT NULL DEFAULT 0,
    records_rejected  INTEGER     NOT NULL DEFAULT 0,
    message           TEXT        NULL,
    started_by        VARCHAR(8)  NULL,
    started_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at       TIMESTAMP   NULL
);
CREATE INDEX ix_batch_run_job ON batch_run (job_name, started_at DESC);

-- Daily reject record: original 350-byte record + 4-byte reason + 76-byte description.
CREATE TABLE batch_reject (
    id            BIGSERIAL   PRIMARY KEY,
    batch_run_id  BIGINT      NOT NULL,
    record_number INTEGER     NOT NULL,
    raw_record    TEXT        NOT NULL,
    reason_code   VARCHAR(4)  NOT NULL,
    reason_text   VARCHAR(76) NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reject_run FOREIGN KEY (batch_run_id) REFERENCES batch_run (id) ON DELETE CASCADE
);
CREATE INDEX ix_batch_reject_run ON batch_reject (batch_run_id);

-- Ledger giving posting the unique (run, record) identity required by FR-BATCH-005.
CREATE TABLE posting_ledger (
    batch_run_id   BIGINT      NOT NULL,
    record_number  INTEGER     NOT NULL,
    transaction_id VARCHAR(16) NULL,
    outcome        VARCHAR(12) NOT NULL,
    CONSTRAINT pk_posting_ledger PRIMARY KEY (batch_run_id, record_number)
);

-- Unique (cycle, account, type, category) interest charge identity per FR-BATCH-005.
CREATE TABLE interest_charge (
    cycle_id       VARCHAR(10)   NOT NULL,
    account_id     VARCHAR(11)   NOT NULL,
    type_code      VARCHAR(2)    NOT NULL,
    category_code  VARCHAR(4)    NOT NULL,
    interest_amt   NUMERIC(13,2) NOT NULL,
    transaction_id VARCHAR(16)   NOT NULL,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_interest_charge PRIMARY KEY (cycle_id, account_id, type_code, category_code)
);

-- ------------------------------------------------------------------ report requests (CORPT00C)
-- FR-RPT-003: a durable structured request, never generated JCL text.
CREATE TABLE report_request (
    id            BIGSERIAL   PRIMARY KEY,
    report_type   VARCHAR(10) NOT NULL,
    start_date    VARCHAR(10) NOT NULL,
    end_date      VARCHAR(10) NOT NULL,
    status        VARCHAR(12) NOT NULL DEFAULT 'SUBMITTED',
    requested_by  VARCHAR(8)  NOT NULL,
    requested_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at  TIMESTAMP   NULL,
    line_count    INTEGER     NOT NULL DEFAULT 0,
    content       TEXT        NULL,
    CONSTRAINT ck_report_type CHECK (report_type IN ('MONTHLY', 'YEARLY', 'CUSTOM'))
);
CREATE INDEX ix_report_request_status ON report_request (status, requested_at DESC);

-- ------------------------------------------------------------------ statements (CBSTM03A)
CREATE TABLE account_statement (
    id            BIGSERIAL   PRIMARY KEY,
    batch_run_id  BIGINT      NULL,
    card_number   VARCHAR(16) NOT NULL,
    account_id    VARCHAR(11) NOT NULL,
    customer_id   VARCHAR(9)  NOT NULL,
    tran_count    INTEGER     NOT NULL DEFAULT 0,
    total_amount  NUMERIC(15,2) NOT NULL DEFAULT 0,
    text_content  TEXT        NOT NULL,
    html_content  TEXT        NOT NULL,
    generated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_statement_card ON account_statement (card_number);
CREATE INDEX ix_statement_run  ON account_statement (batch_run_id);

-- ------------------------------------------------------------------ audit (FR-USER-007)
CREATE TABLE audit_event (
    id          BIGSERIAL    PRIMARY KEY,
    actor       VARCHAR(8)   NULL,
    action      VARCHAR(60)  NOT NULL,
    target_type VARCHAR(40)  NULL,
    target_id   VARCHAR(40)  NULL,
    outcome     VARCHAR(20)  NOT NULL,
    detail      VARCHAR(500) NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_audit_created ON audit_event (created_at DESC);
CREATE INDEX ix_audit_actor   ON audit_event (actor);

-- ------------------------------------------------------------------ data-migration provenance (DATA-007)
CREATE TABLE migration_log (
    id             BIGSERIAL    PRIMARY KEY,
    source_file    VARCHAR(200) NOT NULL,
    entity         VARCHAR(40)  NOT NULL,
    codec          VARCHAR(20)  NOT NULL,
    records_read   INTEGER      NOT NULL DEFAULT 0,
    records_loaded INTEGER      NOT NULL DEFAULT 0,
    records_failed INTEGER      NOT NULL DEFAULT 0,
    detail         TEXT         NULL,
    executed_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

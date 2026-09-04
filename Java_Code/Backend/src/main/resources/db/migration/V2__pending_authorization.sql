-- =====================================================================================
-- Pending authorization module.
--
-- Relational form of the optional authorization extension in
-- Cobol_Code/aws-mainframe-modernization-carddemo/app/app-authorization-ims-db2-mq,
-- which the base conversion left as "not installed" (FR-OPT-017).
--
--   pending_auth_summary <- CIPAUSMY.cpy  IMS segment PAUTSUM0 (root , 100 bytes)
--   pending_auth_detail  <- CIPAUDTY.cpy  IMS segment PAUTDTL1 (child, 200 bytes)
--   auth_fraud           <- AUTHFRDS.ddl  Db2 table CARDDEMO.AUTHFRDS
--
-- The source keeps pending authorizations in an IMS HIDAM database (DBPAUTP0.dbd) and
-- the fraud state in Db2. Both become ordinary tables here: the IMS parent/child
-- hierarchy is a foreign key, and the DL/I GU / GNP cursor walk becomes an ordered
-- read of the child table. Money stays NUMERIC so cents are exact (DATA-004) and
-- legacy identifiers stay VARCHAR at their COBOL width so leading zeroes survive
-- (DATA-002).
-- =====================================================================================

-- ------------------------------------------------------------------ pending authorization summary
-- COBOL: CIPAUSMY.cpy. IMS root segment PAUTSUM0, sequence field ACCNTID = PA-ACCT-ID
-- (S9(11) COMP-3), so the account id is the whole key of the root.
CREATE TABLE pending_auth_summary (
    account_id           VARCHAR(11)   PRIMARY KEY,
    customer_id          VARCHAR(9)    NOT NULL DEFAULT '',
    -- PA-AUTH-STATUS X(1) and PA-ACCOUNT-STATUS X(2) OCCURS 5. The occurs block is kept
    -- as one opaque 10-character string: the source never indexes it on these screens.
    auth_status          VARCHAR(1)    NOT NULL DEFAULT '',
    account_status       VARCHAR(10)   NOT NULL DEFAULT '',
    credit_limit         NUMERIC(14,2) NOT NULL DEFAULT 0,
    cash_limit           NUMERIC(14,2) NOT NULL DEFAULT 0,
    credit_balance       NUMERIC(14,2) NOT NULL DEFAULT 0,
    cash_balance         NUMERIC(14,2) NOT NULL DEFAULT 0,
    approved_auth_count  INTEGER       NOT NULL DEFAULT 0,
    declined_auth_count  INTEGER       NOT NULL DEFAULT 0,
    approved_auth_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    declined_auth_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_pauth_summary_account FOREIGN KEY (account_id) REFERENCES account (account_id)
);

-- ------------------------------------------------------------------ pending authorization detail
-- COBOL: CIPAUDTY.cpy. IMS child segment PAUTDTL1, sequence field PAUT9CTS = the 8-byte
-- PA-AUTHORIZATION-KEY (PA-AUTH-DATE-9C S9(5) COMP-3 + PA-AUTH-TIME-9C S9(9) COMP-3).
--
-- Both halves are stored as NINE'S COMPLEMENT on the mainframe -- that is what the "9C"
-- in the field names means -- so that a forward DL/I GNP scan returns the newest
-- authorization first. auth_key preserves that exactly: it is the 5 complement date
-- digits followed by the 9 complement time digits, zero padded and fixed width, so
-- ORDER BY auth_key ASC reproduces the physical IMS order the COBOL relies on. The
-- decoded, human-readable values live alongside it in auth_julian_date / auth_time_value.
-- auth_key carries COLLATE "C" because the paging contract is byte ordinal comparison. The
-- key is always fourteen ASCII digits, so every collation happens to agree, but saying so
-- keeps a future locale change from quietly reordering the authorization list.
CREATE TABLE pending_auth_detail (
    account_id        VARCHAR(11)   NOT NULL,
    auth_key          VARCHAR(14)   COLLATE "C" NOT NULL,
    auth_julian_date  INTEGER       NOT NULL DEFAULT 0,
    auth_time_value   INTEGER       NOT NULL DEFAULT 0,
    auth_orig_date    VARCHAR(6)    NOT NULL DEFAULT '',
    auth_orig_time    VARCHAR(6)    NOT NULL DEFAULT '',
    card_number       VARCHAR(16)   NOT NULL DEFAULT '',
    auth_type         VARCHAR(4)    NOT NULL DEFAULT '',
    card_expiry_date  VARCHAR(4)    NOT NULL DEFAULT '',
    message_type      VARCHAR(6)    NOT NULL DEFAULT '',
    message_source    VARCHAR(6)    NOT NULL DEFAULT '',
    auth_id_code      VARCHAR(6)    NOT NULL DEFAULT '',
    auth_resp_code    VARCHAR(2)    NOT NULL DEFAULT '',
    auth_resp_reason  VARCHAR(4)    NOT NULL DEFAULT '',
    processing_code   VARCHAR(6)    NOT NULL DEFAULT '',
    transaction_amt   NUMERIC(14,2) NOT NULL DEFAULT 0,
    approved_amt      NUMERIC(14,2) NOT NULL DEFAULT 0,
    mcc_code          VARCHAR(4)    NOT NULL DEFAULT '',
    acqr_country_code VARCHAR(3)    NOT NULL DEFAULT '',
    pos_entry_mode    VARCHAR(2)    NOT NULL DEFAULT '',
    merchant_id       VARCHAR(15)   NOT NULL DEFAULT '',
    merchant_name     VARCHAR(22)   NOT NULL DEFAULT '',
    merchant_city     VARCHAR(13)   NOT NULL DEFAULT '',
    merchant_state    VARCHAR(2)    NOT NULL DEFAULT '',
    merchant_zip      VARCHAR(9)    NOT NULL DEFAULT '',
    transaction_id    VARCHAR(15)   NOT NULL DEFAULT '',
    -- PA-MATCH-STATUS: P pending, D declined, E pending expired, M matched with a transaction.
    match_status      VARCHAR(1)    NOT NULL DEFAULT '',
    -- PA-AUTH-FRAUD: F confirmed, R removed, blank never reported.
    auth_fraud        VARCHAR(1)    NOT NULL DEFAULT '',
    fraud_rpt_date    VARCHAR(8)    NOT NULL DEFAULT '',
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_pending_auth_detail PRIMARY KEY (account_id, auth_key),
    CONSTRAINT fk_pauth_detail_summary FOREIGN KEY (account_id)
        REFERENCES pending_auth_summary (account_id),
    CONSTRAINT ck_pauth_detail_match CHECK (match_status IN ('', 'P', 'D', 'E', 'M')),
    CONSTRAINT ck_pauth_detail_fraud CHECK (auth_fraud IN ('', 'F', 'R'))
);
-- The GNP walk within one parent, in physical order.
CREATE INDEX ix_pauth_detail_parent ON pending_auth_detail (account_id, auth_key);
CREATE INDEX ix_pauth_detail_card   ON pending_auth_detail (card_number);
CREATE INDEX ix_pauth_detail_fraud  ON pending_auth_detail (auth_fraud);

-- ------------------------------------------------------------------ fraud report state
-- COBOL: ddl/AUTHFRDS.ddl, Db2 table CARDDEMO.AUTHFRDS written by COPAUS2C. Column
-- widths and the (CARD_NUM, AUTH_TS) primary key are the Db2 declarations verbatim;
-- DECIMAL(12,2) is kept rather than widened because this table is a direct Db2 port.
CREATE TABLE auth_fraud (
    card_number       VARCHAR(16)   NOT NULL,
    auth_ts           VARCHAR(26)   NOT NULL,
    auth_type         VARCHAR(4)    NOT NULL DEFAULT '',
    card_expiry_date  VARCHAR(4)    NOT NULL DEFAULT '',
    message_type      VARCHAR(6)    NOT NULL DEFAULT '',
    message_source    VARCHAR(6)    NOT NULL DEFAULT '',
    auth_id_code      VARCHAR(6)    NOT NULL DEFAULT '',
    auth_resp_code    VARCHAR(2)    NOT NULL DEFAULT '',
    auth_resp_reason  VARCHAR(4)    NOT NULL DEFAULT '',
    processing_code   VARCHAR(6)    NOT NULL DEFAULT '',
    transaction_amt   NUMERIC(12,2) NOT NULL DEFAULT 0,
    approved_amt      NUMERIC(12,2) NOT NULL DEFAULT 0,
    mcc_code          VARCHAR(4)    NOT NULL DEFAULT '',
    acqr_country_code VARCHAR(3)    NOT NULL DEFAULT '',
    pos_entry_mode    SMALLINT      NOT NULL DEFAULT 0,
    merchant_id       VARCHAR(15)   NOT NULL DEFAULT '',
    merchant_name     VARCHAR(22)   NOT NULL DEFAULT '',
    merchant_city     VARCHAR(13)   NOT NULL DEFAULT '',
    merchant_state    VARCHAR(2)    NOT NULL DEFAULT '',
    merchant_zip      VARCHAR(9)    NOT NULL DEFAULT '',
    transaction_id    VARCHAR(15)   NOT NULL DEFAULT '',
    match_status      VARCHAR(1)    NOT NULL DEFAULT '',
    auth_fraud        VARCHAR(1)    NOT NULL DEFAULT '',
    fraud_rpt_date    VARCHAR(10)   NOT NULL DEFAULT '',
    account_id        VARCHAR(11)   NOT NULL DEFAULT '',
    customer_id       VARCHAR(9)    NOT NULL DEFAULT '',
    reported_by       VARCHAR(8)    NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_auth_fraud PRIMARY KEY (card_number, auth_ts)
);
CREATE INDEX ix_auth_fraud_account ON auth_fraud (account_id);

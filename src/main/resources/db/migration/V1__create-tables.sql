CREATE TABLE client
(
    id            VARCHAR(64)    NOT NULL,
    name          VARCHAR(255)   NOT NULL,
    password      VARCHAR(255)   NOT NULL,
    document_id   VARCHAR(14)    NOT NULL,
    document_type VARCHAR(4)     NOT NULL,
    plan_type     VARCHAR(10)    NOT NULL,
    balance       NUMERIC(15, 2) NOT NULL DEFAULT 0,
    credit_limit  NUMERIC(15, 2) NOT NULL DEFAULT 0,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_client PRIMARY KEY (id),
    CONSTRAINT uk_client_document_id UNIQUE (document_id),
    CONSTRAINT ck_client_document_type CHECK (document_type IN ('CPF', 'CNPJ')),
    CONSTRAINT ck_client_plan_type CHECK (plan_type IN ('PREPAID', 'POSTPAID')),
    CONSTRAINT ck_client_balance CHECK (balance >= 0),
    CONSTRAINT ck_client_credit_limit CHECK (credit_limit >= 0),
    CONSTRAINT ck_client_document_length CHECK (
        (document_type = 'CPF' AND char_length(document_id) = 11) OR
        (document_type = 'CNPJ' AND char_length(document_id) = 14))
);
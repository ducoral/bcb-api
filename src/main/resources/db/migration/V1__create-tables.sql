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
    monthly_limit NUMERIC(15, 2) NOT NULL DEFAULT 0,
    limit_period  DATE,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_client PRIMARY KEY (id),
    CONSTRAINT uk_client_document_id UNIQUE (document_id)
);

CREATE TABLE message
(
    id              VARCHAR(64)    NOT NULL,
    sender_id       VARCHAR(64)    NOT NULL,
    recipient_id    VARCHAR(64)    NOT NULL,
    content         VARCHAR(4000)  NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    priority        VARCHAR(10)    NOT NULL,
    status          VARCHAR(12)    NOT NULL,
    cost            NUMERIC(10, 2) NOT NULL,

    CONSTRAINT pk_message PRIMARY KEY (id),
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES client (id)
);

-- usado pela carga inicial da fila
CREATE INDEX idx_message_queued ON message (created_at) WHERE status = 'QUEUED';

-- índice para a listagem
CREATE INDEX idx_message_sender_created_at ON message (sender_id, created_at DESC);
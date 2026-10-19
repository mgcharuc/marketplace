CREATE SEQUENCE order_number_seq AS bigint START WITH 1;

CREATE TABLE orders (
  id             text          NOT NULL,
  tenant_id      text          NOT NULL,
  customer_id    text          NOT NULL,
  distributor_id text          NOT NULL,
  order_seq      bigint        NOT NULL,
  order_number   text          NOT NULL,
  quote_id       text          NOT NULL,
  status         text          NOT NULL CONSTRAINT orders_status_ck CHECK (status IN ('CONFIRMED')),
  total          numeric(19,4) NOT NULL,
  response_body  text          NOT NULL,
  confirmed_at   timestamptz   NOT NULL,
  CONSTRAINT orders_pk PRIMARY KEY (id),
  CONSTRAINT orders_quote_uk UNIQUE (quote_id),
  CONSTRAINT orders_number_uk UNIQUE (order_number),
  CONSTRAINT orders_quote_fk FOREIGN KEY (quote_id) REFERENCES quote (id),
  CONSTRAINT orders_customer_fk FOREIGN KEY (tenant_id, customer_id) REFERENCES customer (tenant_id, id),
  CONSTRAINT orders_distributor_fk FOREIGN KEY (tenant_id, distributor_id) REFERENCES distributor (tenant_id, id)
);
CREATE INDEX orders_history_ix ON orders (tenant_id, customer_id, confirmed_at DESC, order_seq DESC);

CREATE TABLE credit_movement (
  order_id       text          NOT NULL,
  tenant_id      text          NOT NULL,
  customer_id    text          NOT NULL,
  distributor_id text          NOT NULL,
  amount         numeric(19,4) NOT NULL CONSTRAINT credit_movement_amount_ck CHECK (amount > 0),
  created_at     timestamptz   NOT NULL,
  CONSTRAINT credit_movement_pk PRIMARY KEY (order_id),
  CONSTRAINT credit_movement_order_fk FOREIGN KEY (order_id) REFERENCES orders (id),
  CONSTRAINT credit_movement_account_fk FOREIGN KEY (tenant_id, customer_id, distributor_id)
    REFERENCES credit_account (tenant_id, customer_id, distributor_id)
);

CREATE TABLE outbox_event (
  id                 uuid        NOT NULL,
  tenant_id          text        NOT NULL,
  aggregate_id       text        NOT NULL,
  event_type         text        NOT NULL,
  destination        text        NOT NULL CONSTRAINT outbox_destination_ck CHECK (destination IN ('ERP', 'PUSH')),
  payload            jsonb       NOT NULL,
  status             text        NOT NULL CONSTRAINT outbox_status_ck
                                     CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DISCARDED')),
  attempts           int         NOT NULL DEFAULT 0,
  next_attempt_at    timestamptz NOT NULL,
  retry_window_start timestamptz NOT NULL,
  last_error         text,
  created_at         timestamptz NOT NULL,
  sent_at            timestamptz,
  CONSTRAINT outbox_event_pk PRIMARY KEY (id)
);
CREATE INDEX outbox_pending_ix ON outbox_event (status, next_attempt_at, id);

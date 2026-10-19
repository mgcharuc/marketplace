CREATE TABLE idempotency_record (
  tenant_id     text        NOT NULL,
  customer_id   text        NOT NULL,
  idem_key      text        NOT NULL,
  request_hash  text        NOT NULL,
  order_id      text,
  response_body text,
  created_at    timestamptz NOT NULL,
  expires_at    timestamptz NOT NULL,
  CONSTRAINT idempotency_record_pk PRIMARY KEY (tenant_id, customer_id, idem_key)
);

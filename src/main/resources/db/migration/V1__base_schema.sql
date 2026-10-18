CREATE TABLE country (
  code            text NOT NULL,
  currency_code   text NOT NULL,
  currency_scale  int  NOT NULL CONSTRAINT country_scale_ck CHECK (currency_scale BETWEEN 0 AND 4),
  rounding_mode   text NOT NULL CONSTRAINT country_rounding_ck CHECK (rounding_mode IN ('HALF_UP', 'HALF_EVEN')),
  gift_tax_policy text NOT NULL CONSTRAINT country_gift_policy_ck
                      CHECK (gift_tax_policy IN ('NONE', 'ABSORBED_BY_SELLER', 'CHARGED_TO_CUSTOMER')),
  CONSTRAINT country_pk PRIMARY KEY (code)
);

CREATE TABLE country_tax_class (
  country_code text         NOT NULL,
  code         text         NOT NULL,
  rate         numeric(5,4) NOT NULL CONSTRAINT country_tax_class_rate_ck CHECK (rate BETWEEN 0 AND 1),
  CONSTRAINT country_tax_class_pk PRIMARY KEY (country_code, code),
  CONSTRAINT country_tax_class_country_fk FOREIGN KEY (country_code) REFERENCES country (code)
);

CREATE TABLE tenant (
  id                      text NOT NULL,
  country_code            text NOT NULL,
  brand                   text NOT NULL,
  order_number_prefix     text NOT NULL,
  quote_ttl_seconds       int  NOT NULL CONSTRAINT tenant_quote_ttl_ck CHECK (quote_ttl_seconds > 0),
  idempotency_ttl_seconds int  NOT NULL CONSTRAINT tenant_idem_ttl_ck CHECK (idempotency_ttl_seconds > 0),
  CONSTRAINT tenant_pk PRIMARY KEY (id),
  CONSTRAINT tenant_prefix_uk UNIQUE (order_number_prefix),
  CONSTRAINT tenant_country_fk FOREIGN KEY (country_code) REFERENCES country (code)
);

CREATE TABLE distributor (
  tenant_id text NOT NULL,
  id        text NOT NULL,
  name      text NOT NULL,
  CONSTRAINT distributor_pk PRIMARY KEY (tenant_id, id),
  CONSTRAINT distributor_tenant_fk FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE TABLE customer (
  tenant_id text NOT NULL,
  id        text NOT NULL,
  name      text NOT NULL,
  CONSTRAINT customer_pk PRIMARY KEY (tenant_id, id),
  CONSTRAINT customer_tenant_fk FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE TABLE config_version (
  tenant_id    text        NOT NULL,
  version      int         NOT NULL CONSTRAINT config_version_number_ck CHECK (version > 0),
  published_at timestamptz NOT NULL,
  definition   jsonb       NOT NULL,
  is_current   boolean     NOT NULL,
  CONSTRAINT config_version_pk PRIMARY KEY (tenant_id, version),
  CONSTRAINT config_version_tenant_fk FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);
CREATE UNIQUE INDEX config_version_current_uk ON config_version (tenant_id) WHERE is_current;

CREATE TABLE credit_account (
  tenant_id      text          NOT NULL,
  customer_id    text          NOT NULL,
  distributor_id text          NOT NULL,
  credit_limit   numeric(19,4) NOT NULL,
  available      numeric(19,4) NOT NULL CONSTRAINT credit_account_available_ck CHECK (available >= 0),
  CONSTRAINT credit_account_pk PRIMARY KEY (tenant_id, customer_id, distributor_id),
  CONSTRAINT credit_account_customer_fk FOREIGN KEY (tenant_id, customer_id) REFERENCES customer (tenant_id, id),
  CONSTRAINT credit_account_distributor_fk FOREIGN KEY (tenant_id, distributor_id) REFERENCES distributor (tenant_id, id)
);

CREATE TABLE quote (
  id             text          NOT NULL,
  tenant_id      text          NOT NULL,
  customer_id    text          NOT NULL,
  distributor_id text          NOT NULL,
  config_version int           NOT NULL,
  payment_method text          NOT NULL CONSTRAINT quote_payment_ck CHECK (payment_method IN ('CASH', 'CREDIT')),
  total          numeric(19,4) NOT NULL CONSTRAINT quote_total_ck CHECK (total >= 0),
  snapshot       text          NOT NULL,
  created_at     timestamptz   NOT NULL,
  expires_at     timestamptz   NOT NULL,
  CONSTRAINT quote_pk PRIMARY KEY (id),
  CONSTRAINT quote_customer_fk FOREIGN KEY (tenant_id, customer_id) REFERENCES customer (tenant_id, id),
  CONSTRAINT quote_distributor_fk FOREIGN KEY (tenant_id, distributor_id) REFERENCES distributor (tenant_id, id),
  CONSTRAINT quote_config_fk FOREIGN KEY (tenant_id, config_version) REFERENCES config_version (tenant_id, version)
);

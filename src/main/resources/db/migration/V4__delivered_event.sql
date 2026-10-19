-- Sin FK hacia outbox_event, porque la fila se inserta en otra transacción mientras el evento está bloqueado.
CREATE TABLE delivered_event (
  event_id     uuid        NOT NULL,
  destination  text        NOT NULL,
  delivered_at timestamptz NOT NULL,
  CONSTRAINT delivered_event_pk PRIMARY KEY (event_id, destination)
);

package com.marketplace.support;

import com.marketplace.application.port.DeliveryException;
import com.marketplace.application.port.PushClient;
import java.util.ArrayList;
import java.util.List;

public class FakePushProvider implements PushClient {

    public record Sent(String payload, String collapseKey) {}

    private final List<Sent> sent = new ArrayList<>();
    private boolean available = true;

    @Override
    public synchronized void send(String payload, String collapseKey) throws DeliveryException {
        if (!available) {
            throw new DeliveryException("Proveedor de notificaciones no disponible");
        }
        sent.add(new Sent(payload, collapseKey));
    }

    public synchronized List<Sent> sent() {
        return List.copyOf(sent);
    }

    public synchronized void setAvailable(boolean available) {
        this.available = available;
    }

    synchronized void reset() {
        sent.clear();
        available = true;
    }
}

package com.marketplace.adapters.system;

import com.marketplace.application.port.IdGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class RandomIdGenerator implements IdGenerator {

    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int ULID_LENGTH = 26;
    private static final int BITS_PER_CHAR = 5;
    private static final long UUID_VERSION_7 = 0x7000L;
    private static final long UUID_VARIANT = 0x8000_0000_0000_0000L;
    private static final int COUNTER_BITS = 12;
    private static final long COUNTER_MASK = (1L << COUNTER_BITS) - 1;

    private final Clock clock;
    private final Random random;
    private final AtomicLong lastEvent = new AtomicLong();

    public RandomIdGenerator() {
        this(Clock.systemUTC(), drbg());
    }

    RandomIdGenerator(Clock clock, Random random) {
        this.clock = clock;
        this.random = random;
    }

    // NativePRNG lee /dev/urandom dentro de un bloque synchronized y fija el hilo virtual.
    private static SecureRandom drbg() {
        try {
            return SecureRandom.getInstance("DRBG");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("El JDK no ofrece DRBG", e);
        }
    }

    @Override
    public String quoteId() {
        return "q_" + ulid();
    }

    @Override
    public String orderId() {
        return "o_" + ulid();
    }

    // Los 12 bits después de los milisegundos son un contador, para que dos ids del mismo
    // milisegundo queden en el orden en que se crearon.
    @Override
    public UUID eventId() {
        long stamp =
                lastEvent.updateAndGet(
                        previous -> Math.max(previous + 1, clock.millis() << COUNTER_BITS));
        long msb = (stamp >>> COUNTER_BITS) << 16 | UUID_VERSION_7 | stamp & COUNTER_MASK;
        long lsb = random.nextLong() >>> 2 | UUID_VARIANT;
        return new UUID(msb, lsb);
    }

    // 48 bits de milisegundos seguidos de 80 aleatorios, en grupos de 5 bits desde el más alto.
    private String ulid() {
        long high = clock.millis() << 16 | random.nextInt(1 << 16);
        long low = random.nextLong();
        var text = new char[ULID_LENGTH];
        for (int i = 0; i < ULID_LENGTH; i++) {
            int position = (ULID_LENGTH - 1 - i) * BITS_PER_CHAR;
            text[i] = CROCKFORD[fiveBits(high, low, position)];
        }
        return new String(text);
    }

    private static int fiveBits(long high, long low, int position) {
        long bits;
        if (position >= Long.SIZE) {
            bits = high >>> (position - Long.SIZE);
        } else if (position + BITS_PER_CHAR <= Long.SIZE) {
            bits = low >>> position;
        } else {
            bits = low >>> position | high << (Long.SIZE - position);
        }
        return (int) (bits & 0b11111);
    }
}

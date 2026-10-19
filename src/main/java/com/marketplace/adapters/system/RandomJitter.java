package com.marketplace.adapters.system;

import com.marketplace.application.port.Jitter;
import java.time.Duration;
import java.util.Random;
import java.util.random.RandomGenerator;

public class RandomJitter implements Jitter {

    private final RandomGenerator random;

    public RandomJitter() {
        this(new Random());
    }

    RandomJitter(RandomGenerator random) {
        this.random = random;
    }

    // Hasta un 20 % de la espera.
    @Override
    public Duration apply(Duration interval) {
        long bound = interval.toMillis() / 5;
        return Duration.ofMillis(random.nextLong(bound + 1));
    }
}

package com.marketplace.support;

import com.marketplace.application.seed.LoadSeeds;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public class DatabaseCleaner {

    private static final String TABLES =
            """
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'
              AND table_name <> 'flyway_schema_history'
            """;

    private final JdbcTemplate jdbc;
    private final LoadSeeds loadSeeds;
    private final FixedTimeSource time;
    private final SequentialIdGenerator ids;
    private final FailingOutbox outbox;
    private final FakeErp erp;
    private final FakePushProvider push;
    private final FixedJitter jitter;

    DatabaseCleaner(
            JdbcTemplate jdbc,
            LoadSeeds loadSeeds,
            FixedTimeSource time,
            SequentialIdGenerator ids,
            FailingOutbox outbox,
            FakeErp erp,
            FakePushProvider push,
            FixedJitter jitter) {
        this.jdbc = jdbc;
        this.loadSeeds = loadSeeds;
        this.time = time;
        this.ids = ids;
        this.outbox = outbox;
        this.erp = erp;
        this.push = push;
        this.jitter = jitter;
    }

    public void clean() {
        List<String> tables = jdbc.queryForList(TABLES, String.class);
        jdbc.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        time.reset();
        ids.reset();
        outbox.reset();
        erp.reset();
        push.reset();
        jitter.reset();
        loadSeeds.handle();
    }
}

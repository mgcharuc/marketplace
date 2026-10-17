package com.marketplace.pricing;

import com.marketplace.domain.config.ScaleMode;
import com.marketplace.domain.config.ScaleRule;
import com.marketplace.domain.config.Tier;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class ScaleWalk {

    private final Map<LineState, BigDecimal> exactByLine = new LinkedHashMap<>();
    private final Map<LineState, Integer> unitsByLine = new LinkedHashMap<>();
    private final TreeMap<Integer, TierTotal> tiers = new TreeMap<>();

    private ScaleWalk() {}

    static ScaleWalk over(ScaleRule rule, List<LineState> orderedLines) {
        var walk = new ScaleWalk();
        int eligibleUnits = orderedLines.stream().mapToInt(LineState::eligible).sum();
        Tier reached = highestTierReached(rule.tiers(), eligibleUnits);
        int position = 0;
        for (LineState line : orderedLines) {
            for (int unit = 0; unit < line.eligible(); unit++) {
                position++;
                Tier tier =
                        rule.mode() == ScaleMode.MARGINAL
                                ? highestTierReached(rule.tiers(), position)
                                : reached;
                if (tier != null) {
                    walk.discount(line, tier);
                }
            }
        }
        return walk;
    }

    Map<LineState, BigDecimal> exactByLine() {
        return exactByLine;
    }

    Map<LineState, Integer> unitsByLine() {
        return unitsByLine;
    }

    List<TierTotal> tiers() {
        return List.copyOf(tiers.values());
    }

    private void discount(LineState line, Tier tier) {
        BigDecimal discount = line.unitPrice().times(tier.percent().movePointLeft(2));
        exactByLine.merge(line, discount, BigDecimal::add);
        unitsByLine.merge(line, 1, Integer::sum);
        tiers.computeIfAbsent(tier.fromQty(), fromQty -> new TierTotal(tier)).add(discount);
    }

    private static Tier highestTierReached(List<Tier> tiers, int units) {
        Tier reached = null;
        for (Tier tier : tiers) {
            if (tier.fromQty() <= units) {
                reached = tier;
            }
        }
        return reached;
    }

    static final class TierTotal {

        private final Tier tier;
        private BigDecimal exact = BigDecimal.ZERO;
        private int units;

        private TierTotal(Tier tier) {
            this.tier = tier;
        }

        Tier tier() {
            return tier;
        }

        BigDecimal exact() {
            return exact;
        }

        int units() {
            return units;
        }

        private void add(BigDecimal discount) {
            exact = exact.add(discount);
            units++;
        }
    }
}

package com.marketplace.adapters.json;

import com.marketplace.domain.config.ComboComponent;
import com.marketplace.domain.money.Money;
import com.marketplace.pricing.AppliedDiscount;
import com.marketplace.pricing.Breakdown;
import com.marketplace.pricing.ComboDiscount;
import com.marketplace.pricing.LineAmount;
import com.marketplace.pricing.PricedLine;
import com.marketplace.pricing.RuleAmount;
import com.marketplace.pricing.ScaleDiscount;
import com.marketplace.pricing.TierAmount;
import com.marketplace.pricing.Totals;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public class BreakdownJson {

    private final JsonMapper mapper;

    public BreakdownJson(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode write(Breakdown breakdown) {
        ObjectNode node = mapper.createObjectNode();
        node.put("configVersion", breakdown.configVersion());
        node.putObject("currency")
                .put("code", breakdown.currency().code())
                .put("scale", breakdown.currency().scale())
                .put("roundingMode", breakdown.currency().roundingMode().name());
        ArrayNode lines = node.putArray("lines");
        breakdown.lines().forEach(line -> writeLine(lines.addObject(), line));
        ArrayNode discounts = node.putArray("discounts");
        breakdown.discounts().forEach(discount -> writeDiscount(discounts.addObject(), discount));
        node.putArray("gifts");
        writeTotals(node.putObject("totals"), breakdown.totals());
        node.putNull("credit");
        return node;
    }

    private static void writeLine(ObjectNode node, PricedLine line) {
        node.put("lineId", line.lineId());
        node.put("sku", line.sku().value());
        node.put("name", line.name());
        node.put("quantity", line.quantity());
        putAmount(node, "unitPrice", line.unitPrice());
        putAmount(node, "grossAmount", line.grossAmount());
        writeRuleAmounts(node.putArray("scaleDiscounts"), line.scaleDiscounts());
        writeRuleAmounts(node.putArray("taxBaseAllocations"), line.taxBaseAllocations());
        putAmount(node, "taxableBase", line.taxableBase());
        node.put("taxRate", line.taxRate().toPlainString());
        putAmount(node, "taxAmount", line.taxAmount());
        putAmount(node, "netAmount", line.netAmount());
    }

    private static void writeRuleAmounts(ArrayNode array, List<RuleAmount> amounts) {
        amounts.forEach(
                amount ->
                        putAmount(
                                array.addObject().put("ruleId", amount.ruleId()),
                                "amount",
                                amount.amount()));
    }

    private static void writeDiscount(ObjectNode node, AppliedDiscount discount) {
        switch (discount) {
            case ComboDiscount combo -> writeCombo(node, combo);
            case ScaleDiscount scale -> writeScale(node, scale);
        }
    }

    private static void writeHeader(ObjectNode node, String type, AppliedDiscount discount) {
        node.put("ruleId", discount.ruleId());
        node.put("type", type);
        node.put("origin", type + ":" + discount.ruleId());
        node.put("name", discount.name());
    }

    private static void writeCombo(ObjectNode node, ComboDiscount combo) {
        writeHeader(node, ConfigSchemas.COMBO, combo);
        node.put("applications", combo.applications());
        ArrayNode components = node.putArray("components");
        for (ComboComponent component : combo.components()) {
            components.addObject().put("sku", component.sku().value()).put("qty", component.qty());
        }
        putAmount(node, "listValue", combo.listValue());
        putAmount(node, "comboPrice", combo.comboPrice());
        putAmount(node, "amount", combo.amount());
        writeLineAmounts(node.putArray("lineAmounts"), combo.lineAmounts());
        node.putNull("mode");
        node.putNull("tiers");
    }

    private static void writeScale(ObjectNode node, ScaleDiscount scale) {
        writeHeader(node, ConfigSchemas.SCALE, scale);
        node.putNull("applications");
        node.putNull("components");
        node.putNull("listValue");
        node.putNull("comboPrice");
        putAmount(node, "amount", scale.amount());
        writeLineAmounts(node.putArray("lineAmounts"), scale.lineAmounts());
        node.put("mode", scale.mode().name());
        ArrayNode tiers = node.putArray("tiers");
        for (TierAmount tier : scale.tiers()) {
            ObjectNode tierNode =
                    tiers.addObject()
                            .put("fromQty", tier.fromQty())
                            .put("percent", tier.percent().toPlainString())
                            .put("units", tier.units());
            putAmount(tierNode, "amount", tier.amount());
        }
    }

    private static void writeLineAmounts(ArrayNode array, List<LineAmount> amounts) {
        amounts.forEach(
                amount ->
                        putAmount(
                                array.addObject().put("lineId", amount.lineId()),
                                "amount",
                                amount.amount()));
    }

    private static void writeTotals(ObjectNode node, Totals totals) {
        putAmount(node, "grossSubtotal", totals.grossSubtotal());
        putAmount(node, "discountTotal", totals.discountTotal());
        putAmount(node, "netSubtotal", totals.netSubtotal());
        putAmount(node, "taxableBase", totals.taxableBase());
        putAmount(node, "taxTotal", totals.taxTotal());
        putAmount(node, "total", totals.total());
    }

    private static void putAmount(ObjectNode node, String field, Money amount) {
        node.put(field, amount.amount().toPlainString());
    }
}

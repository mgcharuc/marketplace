package com.marketplace.pricing;

import com.marketplace.domain.config.Stage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula el desglose de un carrito ejecutando las etapas en el orden que define la versión
 * comercial. Una etapa sin implementación registrada no cambia el resultado.
 *
 * <p>Una unidad recibe como máximo un descuento. El combo se aplica tantas veces como conjuntos
 * completos quedan y reparte su descuento entre las líneas en proporción al valor de lista que
 * consumió. La escala cuenta posiciones solo sobre las unidades que siguen elegibles, ordenadas de
 * la más cara a la más barata, así que los tramos altos caen en las unidades más baratas. Los
 * descuentos de escala y los impuestos se redondean una vez por línea.
 */
public class PricingEngine {

    private final Map<Stage, PricingStage> stages = new EnumMap<>(Stage.class);

    public PricingEngine() {
        List.of(new ComboStage(), new ScaleStage(), new TaxStage())
                .forEach(stage -> stages.put(stage.stage(), stage));
    }

    public Breakdown price(PricingInput input) {
        var context = new PricingContext(input);
        for (Stage stage : input.config().pipeline()) {
            PricingStage implementation = stages.get(stage);
            if (implementation != null) {
                implementation.apply(context);
            }
        }
        return context.breakdown();
    }
}

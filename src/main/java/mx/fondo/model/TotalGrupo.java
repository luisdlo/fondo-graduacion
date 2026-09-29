package mx.fondo.model;

import java.math.BigDecimal;

/** Renglón de la vista v_total_grupo. */
public record TotalGrupo(String grupoId, String nombre, String vocal,
                         BigDecimal ingresos, BigDecimal gastos, BigDecimal total) {
}

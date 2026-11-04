package mx.fondo.model;

import java.math.BigDecimal;

/** Renglón de la vista v_total_evento. */
public record TotalEvento(String evento, BigDecimal ingresos, BigDecimal gastos, BigDecimal total) {
}

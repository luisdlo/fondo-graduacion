package mx.fondo.model;

import java.math.BigDecimal;

/** Renglón de la vista v_total_nino. */
public record TotalNino(String grupoId, Integer ninoId, String nombre, long pagos, BigDecimal total) {
}

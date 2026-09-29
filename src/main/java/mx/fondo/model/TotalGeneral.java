package mx.fondo.model;

import java.math.BigDecimal;

/** Vista v_total_general: suma de grupos menos pagos del fondo común. */
public record TotalGeneral(BigDecimal totalGrupos, BigDecimal pagosGraduacion, BigDecimal totalGeneral) {
}

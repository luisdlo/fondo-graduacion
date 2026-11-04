package mx.fondo.model;

import java.math.BigDecimal;

/** Vista v_total_general: grupos + eventos − pagos de la graduación. */
public record TotalGeneral(BigDecimal totalGrupos, BigDecimal totalEventos, BigDecimal pagosGraduacion,
                           BigDecimal totalGeneral) {
}

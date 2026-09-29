package mx.fondo.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un renglón de la tabla movimiento, con los nombres de grupo y niño ya resueltos. */
public record Movimiento(
        Integer id,
        String tipo,              // INGRESO | EGRESO
        String grupoId,           // null = fondo común
        String grupoNombre,
        String nino,
        String concepto,
        BigDecimal monto,
        LocalDate fecha,
        String registradoPor,
        String comprobante,
        boolean cancelado,
        String motivoCancelacion,
        String canceladoPor) {

    public boolean esIngreso() {
        return "INGRESO".equals(tipo);
    }

    public String grupoEtiqueta() {
        return grupoId == null ? "Fondo común" : grupoNombre;
    }

    public String pillClass() {
        return "g-" + (grupoId == null ? "COMUN" : grupoId);
    }
}

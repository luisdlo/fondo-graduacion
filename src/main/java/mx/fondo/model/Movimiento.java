package mx.fondo.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un renglón de la tabla movimiento, con los nombres de grupo y niño ya resueltos. */
public record Movimiento(
        Integer id,
        String tipo,              // INGRESO | EGRESO
        String grupoId,           // null = evento o pago de la graduación
        String grupoNombre,
        String nino,
        String evento,            // bolsa Eventos (ingreso o gasto)
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

    public boolean esEvento() {
        return evento != null;
    }

    public String grupoEtiqueta() {
        return esEvento() ? "Eventos" : grupoId == null ? "Pago graduación" : grupoNombre;
    }

    public String pillClass() {
        return "g-" + (esEvento() ? "EVENTO" : grupoId == null ? "COMUN" : grupoId);
    }
}

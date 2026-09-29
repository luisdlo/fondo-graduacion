package mx.fondo.repository;

import mx.fondo.model.Grupo;
import mx.fondo.model.Movimiento;
import mx.fondo.model.Nino;
import mx.fondo.model.TotalGeneral;
import mx.fondo.model.TotalGrupo;
import mx.fondo.model.TotalNino;
import mx.fondo.model.Vocal;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a la BD con JdbcTemplate.
 * Los totales salen de las vistas definidas en schema.sql.
 */
@Repository
public class FondoRepository {

    private static final RowMapper<Movimiento> MOVIMIENTO = new DataClassRowMapper<>(Movimiento.class);

    private static final String SELECT_MOVIMIENTOS = """
            SELECT m.id, m.tipo, m.grupo_id, g.nombre AS grupo_nombre, n.nombre AS nino,
                   m.concepto, m.monto, m.fecha, m.registrado_por, m.comprobante,
                   m.cancelado, m.motivo_cancelacion, m.cancelado_por
              FROM movimiento m
              LEFT JOIN grupo g ON g.id = m.grupo_id
              LEFT JOIN nino  n ON n.id = m.nino_id
             WHERE 1 = 1
            """;

    private final JdbcTemplate jdbc;

    public FondoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Grupo> grupos() {
        return jdbc.query("SELECT id, nombre, vocal FROM grupo ORDER BY id",
                new DataClassRowMapper<>(Grupo.class));
    }

    public List<TotalGrupo> totalesPorGrupo() {
        return jdbc.query("SELECT * FROM v_total_grupo ORDER BY grupo_id",
                new DataClassRowMapper<>(TotalGrupo.class));
    }

    public TotalGeneral totalGeneral() {
        return jdbc.queryForObject("SELECT * FROM v_total_general",
                new DataClassRowMapper<>(TotalGeneral.class));
    }

    public List<TotalNino> totalesPorNino(String grupoId) {
        return jdbc.query("SELECT * FROM v_total_nino WHERE grupo_id = ? ORDER BY nombre",
                new DataClassRowMapper<>(TotalNino.class), grupoId);
    }

    /**
     * @param tipo              INGRESO, EGRESO o null para ambos
     * @param grupo             A, B, C, COMUN (fondo común) o null para todos
     * @param incluirCancelados si false, oculta los cancelados
     * @param limite            máximo de renglones o null para todos
     */
    public List<Movimiento> movimientos(String tipo, String grupo, boolean incluirCancelados, Integer limite) {
        StringBuilder sql = new StringBuilder(SELECT_MOVIMIENTOS);
        List<Object> args = new ArrayList<>();

        if (tipo != null) {
            sql.append(" AND m.tipo = ?");
            args.add(tipo);
        }
        if ("COMUN".equals(grupo)) {
            sql.append(" AND m.grupo_id IS NULL");
        } else if (grupo != null) {
            sql.append(" AND m.grupo_id = ?");
            args.add(grupo);
        }
        if (!incluirCancelados) {
            sql.append(" AND m.cancelado = FALSE");
        }
        sql.append(" ORDER BY m.fecha DESC, m.id DESC");
        if (limite != null) {
            sql.append(" LIMIT ?");
            args.add(limite);
        }
        return jdbc.query(sql.toString(), MOVIMIENTO, args.toArray());
    }

    /** Vocal por usuario; solo los que ya tienen password capturado pueden entrar. */
    public Optional<Vocal> vocalPorUsuario(String usuario) {
        return jdbc.query("""
                        SELECT id AS grupo_id, nombre AS grupo_nombre, vocal AS nombre, usuario, password
                          FROM grupo
                         WHERE usuario = ? AND password IS NOT NULL
                        """, new DataClassRowMapper<>(Vocal.class), usuario)
                .stream().findFirst();
    }

    public List<Nino> ninosDelGrupo(String grupoId) {
        return jdbc.query("SELECT id, nombre FROM nino WHERE grupo_id = ? ORDER BY nombre",
                new DataClassRowMapper<>(Nino.class), grupoId);
    }

    public void insertarMovimiento(String tipo, String grupoId, Integer ninoId, String concepto,
                                   BigDecimal monto, LocalDate fecha, String registradoPor, String comprobante) {
        jdbc.update("""
                INSERT INTO movimiento (tipo, grupo_id, nino_id, concepto, monto, fecha, registrado_por, comprobante)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, tipo, grupoId, ninoId, concepto, monto, fecha, registradoPor, comprobante);
    }
}

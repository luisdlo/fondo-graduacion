package mx.fondo.service;

import mx.fondo.model.Grupo;
import mx.fondo.model.Movimiento;
import mx.fondo.model.Nino;
import mx.fondo.model.TotalEvento;
import mx.fondo.model.TotalGeneral;
import mx.fondo.model.TotalGrupo;
import mx.fondo.model.Vocal;
import mx.fondo.repository.FondoRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FondoService {

    private static final Set<String> TIPOS = Set.of("INGRESO", "EGRESO");

    /** Montos sugeridos en el combo; también se puede capturar "otra cantidad". */
    public static final List<BigDecimal> MONTOS = List.of(new BigDecimal("500"), new BigDecimal("1000"));

    /** Carpeta de comprobantes junto a la BD (ruta absoluta para que el upload no caiga en /tmp). */
    private static final Path COMPROBANTES = Path.of("data", "comprobantes").toAbsolutePath().normalize();

    private final FondoRepository repository;
    private final PasswordEncoder passwordEncoder;

    public FondoService(FondoRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Grupo> grupos() {
        return repository.grupos();
    }

    public List<TotalGrupo> totalesPorGrupo() {
        return repository.totalesPorGrupo();
    }

    public List<TotalEvento> totalesPorEvento() {
        return repository.totalesPorEvento();
    }

    /** Eventos y conceptos salen de las tablas evento y concepto (se dan de alta a mano en la BD). */
    public List<String> eventos() {
        return repository.eventos();
    }

    public List<String> conceptos(String tipo, String bolsa) {
        return repository.conceptos(tipo, bolsa);
    }

    public TotalGeneral totalGeneral() {
        return repository.totalGeneral();
    }

    public List<Movimiento> movimientos(String tipo, String grupo, String evento, boolean incluirCancelados) {
        String tipoValido = (tipo != null && TIPOS.contains(tipo)) ? tipo : null;
        String grupoValido = (grupo == null || grupo.isBlank()) ? null : grupo;
        // Ojo: List.of(...).contains(null) lanza NPE, por eso se revisa null primero
        String eventoValido = (evento != null && eventos().contains(evento)) ? evento : null;
        return repository.movimientos(tipoValido, grupoValido, eventoValido, incluirCancelados, null);
    }

    // ---------- Registro (el vocal sale del login) ----------

    public Vocal vocal(String usuario) {
        return repository.vocalPorUsuario(usuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuario));
    }

    public List<Vocal> vocales() {
        return repository.vocales();
    }

    public List<Nino> ninosDelGrupo(String grupoId) {
        return repository.ninosDelGrupo(grupoId);
    }

    /**
     * Ingreso: aportación de un alumno (al grupo del vocal) o de un evento (bolsa Eventos,
     * cualquier vocal). El concepto debe estar en el catálogo de esa bolsa.
     */
    public void registrarIngreso(String usuario, boolean esEvento, Integer ninoId, String evento,
                                 String concepto, BigDecimal monto) {
        Vocal vocal = vocal(usuario);
        if (esEvento) {
            validarEvento(evento);
            ninoId = null;
        } else {
            if (ninoId == null) {
                throw new IllegalArgumentException("Elige el niño.");
            }
            evento = null;
        }
        validarConcepto(concepto, "INGRESO", esEvento ? "EVENTO" : "GRUPO");
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero.");
        }
        repository.insertarMovimiento("INGRESO", esEvento ? null : vocal.grupoId(), ninoId, evento, concepto,
                monto, LocalDate.now(), vocal.nombre(), null);
    }

    /**
     * Gasto del grupo del vocal, de un evento (cualquier vocal) o pago de la graduación
     * (fondoComun; preparado, todavía no se usa en pantalla).
     */
    public void registrarEgreso(String usuario, boolean fondoComun, String evento, String concepto, BigDecimal monto,
                                LocalDate fecha, MultipartFile comprobante) throws IOException {
        Vocal vocal = vocal(usuario);
        String bolsa;
        if (fondoComun) {
            bolsa = "COMUN";
            evento = null;
        } else if (evento != null && !evento.isBlank()) {
            bolsa = "EVENTO";
            validarEvento(evento);
        } else {
            bolsa = "GRUPO";
            evento = null;
        }
        validarConcepto(concepto, "EGRESO", bolsa);
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero.");
        }
        String archivo = guardarComprobante(comprobante);
        repository.insertarMovimiento("EGRESO", "GRUPO".equals(bolsa) ? vocal.grupoId() : null, null, evento,
                concepto, monto, fecha != null ? fecha : LocalDate.now(), vocal.nombre(), archivo);
    }

    private void validarEvento(String evento) {
        // Ojo: List.contains(null) puede lanzar NPE, por eso se revisa null primero
        if (evento == null || !eventos().contains(evento)) {
            throw new IllegalArgumentException("Elige el evento.");
        }
    }

    private void validarConcepto(String concepto, String tipo, String bolsa) {
        if (concepto == null || !conceptos(tipo, bolsa).contains(concepto)) {
            throw new IllegalArgumentException("Elige un concepto válido.");
        }
    }

    /**
     * Cambia la contraseña del vocal. Verifica la actual y guarda la nueva con bcrypt
     * (el DelegatingPasswordEncoder acepta el hash o texto plano al iniciar sesión).
     */
    public void cambiarPassword(String usuario, String actual, String nueva, String confirmar) {
        if (nueva == null || nueva.length() < 6) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres.");
        }
        if (!nueva.equals(confirmar)) {
            throw new IllegalArgumentException("La confirmación no coincide con la nueva contraseña.");
        }
        Vocal vocal = vocal(usuario);
        if (actual == null || !passwordEncoder.matches(actual, vocal.password())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta.");
        }
        repository.actualizarPassword(vocal.grupoId(), passwordEncoder.encode(nueva));
    }

    /**
     * Borra un movimiento (DELETE en la BD). Los de eventos los puede borrar cualquier vocal;
     * los del grupo solo su vocal; los pagos de la graduación solo quien los registró.
     * Regresa el filtro (A/B/C, EVENTOS o COMUN) para volver a la lista.
     */
    public String borrarMovimiento(String usuario, int id) {
        Vocal vocal = vocal(usuario);
        Movimiento m = repository.movimientoPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado."));
        if (m.esEvento()) {
            repository.borrar(id);
            return "EVENTOS";
        }
        if (m.grupoId() == null) {
            if (!vocal.nombre().equals(m.registradoPor())) {
                throw new IllegalStateException("Solo quien registró el pago del fondo común puede borrarlo.");
            }
        } else if (!m.grupoId().equals(vocal.grupoId())) {
            throw new IllegalStateException("Solo el vocal del grupo puede borrar este movimiento.");
        }
        repository.borrar(id);
        return m.grupoId() == null ? "COMUN" : m.grupoId();
    }

    /** Regresa el archivo del comprobante, o null si no existe o el nombre intenta salirse de la carpeta. */
    public Resource comprobante(String nombre) {
        Path archivo = COMPROBANTES.resolve(nombre).normalize();
        if (!archivo.startsWith(COMPROBANTES) || !Files.isRegularFile(archivo)) {
            return null;
        }
        return new FileSystemResource(archivo);
    }

    private String guardarComprobante(MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            return null;
        }
        String original = archivo.getOriginalFilename() == null ? "comprobante" : archivo.getOriginalFilename();
        String nombre = UUID.randomUUID().toString().substring(0, 8) + "-" + original.replaceAll("[^A-Za-z0-9._-]", "_");
        Files.createDirectories(COMPROBANTES);
        archivo.transferTo(COMPROBANTES.resolve(nombre));
        return nombre;
    }
}

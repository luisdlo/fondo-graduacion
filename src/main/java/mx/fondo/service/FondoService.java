package mx.fondo.service;

import mx.fondo.model.Grupo;
import mx.fondo.model.Movimiento;
import mx.fondo.model.Nino;
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

    public static final String CONCEPTO = "Fondo de graduación";
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

    public TotalGeneral totalGeneral() {
        return repository.totalGeneral();
    }

    public List<Movimiento> movimientos(String tipo, String grupo, boolean incluirCancelados) {
        String tipoValido = (tipo != null && TIPOS.contains(tipo)) ? tipo : null;
        String grupoValido = (grupo == null || grupo.isBlank()) ? null : grupo;
        return repository.movimientos(tipoValido, grupoValido, incluirCancelados, null);
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

    /** Ingreso al fondo del grupo del vocal. El niño es opcional. */
    public void registrarIngreso(String usuario, Integer ninoId, BigDecimal monto) {
        Vocal vocal = vocal(usuario);
        if (monto == null || MONTOS.stream().noneMatch(m -> m.compareTo(monto) == 0)) {
            throw new IllegalArgumentException("El monto solo puede ser $500 o $1,000.");
        }
        repository.insertarMovimiento("INGRESO", vocal.grupoId(), ninoId, CONCEPTO,
                monto, LocalDate.now(), vocal.nombre(), null);
    }

    /** Gasto del grupo del vocal o pago de la graduación desde el fondo común. */
    public void registrarEgreso(String usuario, boolean fondoComun, String concepto, BigDecimal monto,
                                LocalDate fecha, MultipartFile comprobante) throws IOException {
        Vocal vocal = vocal(usuario);
        if (concepto == null || concepto.isBlank()) {
            throw new IllegalArgumentException("Escribe el concepto del gasto.");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero.");
        }
        String archivo = guardarComprobante(comprobante);
        repository.insertarMovimiento("EGRESO", fondoComun ? null : vocal.grupoId(), null, concepto.trim(),
                monto, fecha != null ? fecha : LocalDate.now(), vocal.nombre(), archivo);
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
     * Borra un movimiento (DELETE en la BD). Solo el vocal del grupo puede borrar sus movimientos;
     * en fondo común solo quien lo registró. Regresa el grupo (A/B/C o COMUN) para el filtro.
     */
    public String borrarMovimiento(String usuario, int id) {
        Vocal vocal = vocal(usuario);
        Movimiento m = repository.movimientoPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado."));
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

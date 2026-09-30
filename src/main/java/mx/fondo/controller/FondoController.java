package mx.fondo.controller;

import mx.fondo.model.Vocal;
import mx.fondo.service.FondoService;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;

/**
 * "/" regresa la página completa; las pestañas y la barra de totales son pedazos de HTML que HTMX
 * inserta en la página. Registrar pide login: el vocal (y su grupo) salen de la sesión.
 */
@Controller
public class FondoController {

    private final FondoService service;

    public FondoController(FondoService service) {
        this.service = service;
    }

    // ---------- Páginas ----------

    @GetMapping("/")
    public String index(@RequestParam(required = false) String tab,
                        @RequestParam(required = false) String grupo,
                        Principal principal, Model model) {
        if (principal != null) {
            model.addAttribute("vocal", service.vocal(principal.getName()));
        }
        String panelUrl = "/resumen";
        String activeTab = "resumen";
        if ("movimientos".equals(tab)) {
            activeTab = "movimientos";
            panelUrl = "/movimientos?cancelados=true"
                    + (grupo != null && !grupo.isBlank() ? "&grupo=" + grupo : "");
        }
        model.addAttribute("panelUrl", panelUrl);
        model.addAttribute("activeTab", activeTab);
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** Después de login, mandar al vocal a Movimientos filtrado por su grupo. */
    @GetMapping("/registro/tras-login")
    public String trasLogin(Principal principal) {
        return "redirect:/?tab=movimientos&grupo=" + service.vocal(principal.getName()).grupoId();
    }

    // ---------- Consulta (público) ----------

    @GetMapping("/resumen")
    public String resumen(Model model) {
        model.addAttribute("grupos", service.totalesPorGrupo());
        model.addAttribute("general", service.totalGeneral());
        return "resumen";
    }

    @GetMapping("/movimientos")
    public String movimientos(@RequestParam(required = false) String tipo,
                              @RequestParam(required = false) String grupo,
                              @RequestParam(defaultValue = "false") boolean cancelados,
                              Principal principal,
                              Model model) {
        model.addAttribute("movimientos", service.movimientos(tipo, grupo, cancelados));
        model.addAttribute("grupos", service.grupos());
        model.addAttribute("tipo", tipo);
        model.addAttribute("grupo", grupo);
        model.addAttribute("cancelados", cancelados);
        if (principal != null) {
            model.addAttribute("vocal", service.vocal(principal.getName()));
        }
        return "movimientos";
    }

    @GetMapping("/totales")
    public String totales(Model model) {
        model.addAttribute("grupos", service.totalesPorGrupo());
        model.addAttribute("general", service.totalGeneral());
        return "totales";
    }

    @GetMapping("/comprobantes/{nombre}")
    public ResponseEntity<Resource> comprobante(@PathVariable String nombre) {
        Resource archivo = service.comprobante(nombre);
        if (archivo == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType tipo = MediaTypeFactory.getMediaType(archivo).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(tipo).body(archivo);
    }

    // ---------- Registro (requiere login de vocal) ----------

    @GetMapping("/registro/ingreso")
    public String formIngreso(Principal principal, Model model) {
        Vocal vocal = service.vocal(principal.getName());
        model.addAttribute("vocal", vocal);
        model.addAttribute("ninos", service.ninosDelGrupo(vocal.grupoId()));
        model.addAttribute("montos", FondoService.MONTOS);
        model.addAttribute("concepto", FondoService.CONCEPTO);
        return "ingreso-form";
    }

    @PostMapping("/ingresos")
    public String registrarIngreso(Principal principal,
                                   @RequestParam(required = false) Integer ninoId,
                                   @RequestParam BigDecimal monto,
                                   RedirectAttributes redirect) {
        try {
            service.registrarIngreso(principal.getName(), ninoId, monto);
            redirect.addFlashAttribute("ok", "Ingreso registrado.");
            return "redirect:/?tab=movimientos";
        } catch (IllegalArgumentException | DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", mensaje(e));
            return "redirect:/";
        }
    }

    @GetMapping("/registro/egreso")
    public String formEgreso(Principal principal, Model model) {
        model.addAttribute("vocal", service.vocal(principal.getName()));
        model.addAttribute("hoy", LocalDate.now());
        return "egreso-form";
    }

    @PostMapping("/egresos")
    public String registrarEgreso(Principal principal,
                                  @RequestParam String concepto,
                                  @RequestParam BigDecimal monto,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                  @RequestParam(required = false) MultipartFile comprobante,
                                  RedirectAttributes redirect) {
        try {
            service.registrarEgreso(principal.getName(), false, concepto, monto, fecha, comprobante);
            redirect.addFlashAttribute("ok", comprobante == null || comprobante.isEmpty()
                    ? "Gasto registrado sin comprobante." : "Gasto registrado.");
            return "redirect:/?tab=movimientos";
        } catch (IllegalArgumentException | DataIntegrityViolationException | IOException e) {
            redirect.addFlashAttribute("error", mensaje(e));
            return "redirect:/";
        }
    }

    @GetMapping("/registro/cambiar-password")
    public String formCambiarPassword(Principal principal, Model model) {
        model.addAttribute("vocal", service.vocal(principal.getName()));
        return "cambiar-password-form";
    }

    @PostMapping("/registro/cambiar-password")
    public String cambiarPassword(Principal principal,
                                  @RequestParam String actual,
                                  @RequestParam String nueva,
                                  @RequestParam String confirmar,
                                  RedirectAttributes redirect) {
        try {
            service.cambiarPassword(principal.getName(), actual, nueva, confirmar);
            redirect.addFlashAttribute("ok", "Contraseña actualizada.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/registro/borrar/{id}")
    public String borrarMovimiento(@PathVariable int id,
                                   Principal principal,
                                   RedirectAttributes redirect) {
        try {
            String grupo = service.borrarMovimiento(principal.getName(), id);
            redirect.addFlashAttribute("ok", "Movimiento borrado.");
            return "redirect:/?tab=movimientos&grupo=" + grupo;
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/?tab=movimientos";
        }
    }

    private String mensaje(Exception e) {
        return e instanceof IllegalArgumentException
                ? e.getMessage()
                : "No se pudo guardar, revisa los datos.";
    }
}

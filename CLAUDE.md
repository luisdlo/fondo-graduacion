# Fondo de Graduación — contexto para Claude Code

Sistemita **pequeño** para llevar el control transparente de ingresos y gastos del fondo de una
fiesta de graduación escolar. Lo usan 3 vocales (uno por grupo) y lo consultan los papás.
**Regla #1: lo más sencillo posible.** Si una solución necesita una capa, librería o patrón
nuevo, casi seguro está sobrada.

## Cómo trabajar en este proyecto

- Generar código directo; no usar Plan Mode ni planes largos para cambios chicos.
- Cambios mínimos y localizados. No refactorizar lo que no se pidió.
- Nada de sobre-ingeniería: sin interfaces para una sola implementación, sin DTOs/mappers extra,
  sin patrones (hexagonal, CQRS, etc.), sin tests elaborados salvo que se pidan.
- Todo el texto de la UI, comentarios y mensajes en **español**.
- Si algo se puede resolver en SQL (vista o CHECK), preferir SQL antes que lógica en Java.

## Stack

- Java 17, Spring Boot 3.3.x, Maven
- Spring MVC + **Thymeleaf** (server-side) + **HTMX** (por CDN en `index.html`)
- **Spring JDBC** (`JdbcTemplate` + `DataClassRowMapper` sobre `record`s). **Sin JPA, sin Lombok.**
- **H2 en modo archivo** (portable)
- **Spring Security** con form login (usuarios en la tabla `grupo`)

## Arquitectura: solo 3 capas

```
controller  →  service  →  repository  →  H2
```

- **controller** (`FondoController`): recibe la petición, llama al service, pone datos en el
  `Model` y regresa la plantilla. Sin lógica de negocio ni SQL.
- **service** (`FondoService`, `VocalService`): reglas de negocio y validaciones.
  `VocalService` implementa `UserDetailsService` para el login.
- **repository** (`FondoRepository`): todo el SQL con `JdbcTemplate`. Nada de reglas de negocio.
- **model**: solo `record`s de datos (no es una capa, son los objetos que viajan entre capas).
- **config** (`SeguridadConfig`): configuración de Spring Security. No meter otra cosa ahí.

No agregar capas nuevas (ni "domain", ni "adapter", ni "dto", ni "mapper"). Si crece algo, se agrega
un método en la clase que corresponde.

## Estructura

```
src/main/java/mx/fondo/
  FondoApplication.java
  config/SeguridadConfig.java
  controller/FondoController.java
  service/FondoService.java
  service/VocalService.java
  repository/FondoRepository.java
  model/  Grupo, Nino, Movimiento, Vocal, TotalGrupo, TotalGeneral, TotalNino (records)
src/main/resources/
  application.properties
  schema.sql                 ← se ejecuta en cada arranque (idempotente)
  static/css/app.css
  templates/
    index.html               ← página completa (encabezado, sesión, pestañas, barra de totales)
    login.html               ← página completa
    resumen.html, movimientos.html, ninos.html, totales.html   ← fragmentos HTMX
    ingreso-form.html, egreso-form.html                        ← fragmentos HTMX (formularios)
    fragments.html           ← piezas reutilizables: money(v) y mov(m)
db/datos-ejemplo.sql         ← datos de prueba; se corre UNA vez a mano en la consola H2
```

## Modelo de datos (schema.sql)

- **grupo**: `id` ('A','B','C'), `nombre`, `vocal`, `usuario`, `password`.
  Un vocal por grupo; el login vive aquí (no hay tabla de usuarios).
- **nino**: `id`, `nombre`, `grupo_id`.
- **movimiento**: ingresos y gastos en una sola tabla.
  - `tipo`: `INGRESO` | `EGRESO`
  - `grupo_id`: grupo del movimiento; **NULL = fondo común** (pagos de la graduación)
  - `nino_id`: opcional, solo para ingresos, debe ser del mismo grupo (FK compuesta)
  - `concepto`: en ingresos siempre `'Fondo de graduación'`; en gastos texto libre
  - `monto`, `fecha`, `registrado_por` (nombre del vocal), `comprobante` (nombre de archivo)
  - `cancelado`, `motivo_cancelacion`, `cancelado_por`
- **Vistas** (excluyen cancelados): `v_total_grupo`, `v_total_general`, `v_total_nino`.

`schema.sql` debe seguir siendo **idempotente**: `CREATE TABLE IF NOT EXISTS`,
`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, `CREATE OR REPLACE VIEW`. Nunca `DROP` ni datos ahí.

## Reglas de negocio

1. **Todo es visible para todos** (transparencia). Consultar no pide login.
2. **Un vocal por grupo.** El vocal logueado solo registra en su grupo; su nombre y grupo salen de
   la sesión (`Principal`), nunca de un campo del formulario.
3. **Ingresos**: concepto fijo "Fondo de graduación", monto **solo $500 o $1,000**
   (`FondoService.MONTOS`, y CHECK en BD), niño opcional, fecha = hoy.
4. **Gastos**: del grupo del vocal o del **fondo común** (pagos de la graduación). Concepto libre,
   monto > 0, fecha, comprobante opcional (PDF o foto).
5. **Totales**: total por grupo = ingresos − gastos del grupo.
   Total general = suma de grupos − pagos del fondo común.
6. **Nada se borra**: un movimiento se cancela (`cancelado = TRUE` + motivo + quién). Nunca `DELETE`.
7. Cómo se reparte el costo entre grupos **no es tema del sistema**: solo registra y suma.

## Seguridad

- Públicas: `/`, `/resumen`, `/movimientos`, `/ninos`, `/totales`, `/comprobantes/**`, `/login`, `/css/**`, `/h2/**`.
- Requieren login: `/registro/**`, `/ingresos`, `/egresos`.
- Password en **texto plano** por ahora (lo captura el admin en la BD). El `PasswordEncoder` es
  delegante con NoOp por default: si en la columna se guarda `{bcrypt}$2a$...` también funciona.
- Usar `AntPathRequestMatcher.antMatcher(...)` en las reglas (hay dos servlets: MVC + consola H2).
- La consola H2 va fuera de CSRF y con `frameOptions sameOrigin`.

## UI (Thymeleaf + HTMX)

- `index.html` es la única página completa (más `login.html`). Las pestañas hacen `hx-get` a un
  endpoint que regresa **un fragmento** y lo ponen en `#panel`.
- La barra fija de totales (`.totbar`) se carga con `hx-get="/totales"` y se refresca cada 60 s.
- Los formularios se cargan por HTMX pero se envían **normal** (POST + redirect a `/` con flash
  `ok`/`error`). Así Thymeleaf agrega el token CSRF solo (`th:action`).
- Montos con el fragmento `fragments :: money(v)`; movimientos con `fragments :: mov(m)`.
- Ojo: `th:each` + `th:replace` en el mismo tag no funciona; usar
  `<th:block th:each="..."><li th:replace="~{fragments :: mov(${m})}"></li></th:block>`.
- CSS simple en `static/css/app.css` con variables y modo oscuro. Sin frameworks de CSS/JS.

## Configuración y ejecución

`application.properties`:
```properties
spring.datasource.url=jdbc:h2:file:~/fondo-graduacion-db/fondo;AUTO_SERVER=TRUE
spring.datasource.username=sa
spring.datasource.password=
spring.sql.init.mode=always
spring.h2.console.enabled=true
spring.h2.console.path=/h2
```

- Correr: `mvn spring-boot:run` → http://localhost:8080
- Consola H2: http://localhost:8080/h2 con **la misma JDBC URL** del properties
  (la consola trae `jdbc:h2:~/test` por default y hay que cambiarla).
- Comprobantes: se guardan en `./data/comprobantes` (ruta absoluta en `FondoService.COMPROBANTES`)
  y se sirven en `/comprobantes/{nombre}`.
- Alta de vocales (manual en la consola):
  `UPDATE grupo SET usuario = 'laura', password = 'cambiar123' WHERE id = 'A';`

## Pendientes / ideas (solo si se piden)

- Cancelar movimientos desde la pantalla (solo el vocal del grupo; en fondo común, quien lo registró).
- Mover los comprobantes junto a la BD (`~/fondo-graduacion-db/comprobantes`).
- Bajar `htmx.min.js` a `static/js` para que funcione sin internet.
- Passwords con bcrypt.

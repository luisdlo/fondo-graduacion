# Deploy en VPS (Vultr) con Docker + Caddy + Dozzle

Guía práctica para levantar el sistema en un VPS. Está pensada para el
setup mínimo: la app en el puerto 80 detrás de Caddy, Dozzle para leer
los logs de todos los contenedores, y la BD H2 en un archivo dentro de
`./data` para poder bajarla a local con un simple `scp`.

## Arquitectura

```
Internet → :80 Caddy ─┬─► /logs* → Dozzle (basic auth)
                      └─► /*      → App Spring Boot :8080
                                     └─► /opt/fondo/data (bind mount)
                                          ├─ fondo.mv.db   (BD H2 completa)
                                          ├─ fondo.trace.db
                                          └─ comprobantes/ (PDFs y fotos)
```

Solo Caddy tiene puertos publicados hacia fuera. La app y Dozzle son
visibles únicamente por la red interna de Docker (`fondo`).

## Archivos del deploy

| Archivo | Qué hace |
|---|---|
| `Dockerfile` | Build multi-stage con Maven, runtime con Temurin JRE 17 |
| `.dockerignore` | Excluye `target/`, `data/`, `.idea/`, etc. del contexto de build |
| `docker-compose.yml` | Los 3 servicios (app, caddy, dozzle) + red + volumen para certificados |
| `Caddyfile` | Reverse proxy: `/logs*` → Dozzle con auth, resto → app |

## Prerrequisitos en el VPS

Ubuntu/Debian recientes:

```bash
apt-get update
apt-get install -y docker.io docker-compose-plugin git
systemctl enable --now docker
```

## Deploy paso a paso

```bash
# 1) Clona el repo en el VPS
git clone https://github.com/luisdlo/fondo-graduacion.git /opt/fondo
cd /opt/fondo

# 2) Genera el hash de tu password para Dozzle
docker run --rm caddy caddy hash-password --plaintext "elpasswordquequieras"
#   Salida: $2a$14$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 3) Pega el hash en Caddyfile reemplazando el placeholder
nano Caddyfile
# La linea:  admin $2a$14$PONER_AQUI_HASH_GENERADO...
# Cambiala por:  admin $2a$14$tuHashRealDelPaso2

# 4) Levanta todo (build + arranque)
docker compose up -d --build

# 5) Verifica
docker compose ps
curl -I http://localhost/           # 200 OK  (app)
curl -I http://localhost/logs/      # 401 Unauthorized  (Dozzle pide auth → correcto)
```

Desde el navegador:

- **App:** `http://TU_IP/`
- **Dozzle (logs):** `http://TU_IP/logs/` (usuario `admin` + tu password)

## Firewall de Vultr

En el panel de Vultr → **Firewall**, deja abiertos únicamente:

- **22** — SSH
- **80** — HTTP (Caddy)
- **443** — cuando actives HTTPS con dominio

**No abras el 8080.** El contenedor de la app no expone puerto al host de
todos modos, pero un firewall bien apretado es mejor por defecto.

## Passwords iniciales

`src/main/resources/schema.sql` deja los 3 vocales listos cuando la BD
está vacía (primer arranque):

| Usuario | Password | Grupo |
|---|---|---|
| `grupoa` | `default123` | A (Idalia)  |
| `grupob` | `default123` | B (Diana)   |
| `grupoc` | `default123` | C (Jessica) |

Cada vocal entra la primera vez con `default123` y **cambia su password
desde el botón 🔒 Cambiar contraseña**. Queda cifrado con bcrypt en la
columna `grupo.password`.

## Backup / bajar la BD a local

Como `./data` es **bind mount** (no volumen oculto de Docker), los
archivos viven directamente en el filesystem del VPS.

### Bajar solo la BD

```bash
# Desde tu Mac local:
scp root@TU_IP:/opt/fondo/data/fondo.mv.db ~/Downloads/backup-$(date +%F).mv.db
```

### Bajar BD + comprobantes

```bash
# Con rsync (incremental, solo baja lo que cambió):
rsync -avz root@TU_IP:/opt/fondo/data/ ~/backups/fondo-$(date +%F)/
```

### Restaurar en tu local para inspeccionar

```bash
# Copia el archivo a la ruta que espera la app local:
cp ~/Downloads/backup-2026-09-30.mv.db ~/fondo-graduacion-db/fondo.mv.db

# Arranca la app local:
mvn spring-boot:run
```

Verás exactamente los mismos datos del VPS. **Un snapshot binario del
`.mv.db` es la base entera** — H2 en modo archivo no requiere export
SQL ni nada intermedio.

### Backup automático diario en el VPS

En el VPS, `crontab -e`:

```cron
# Backup diario a las 3am; retiene 14 días
0 3 * * * tar czf /root/backups/fondo-$(date +\%F).tar.gz -C /opt/fondo data && find /root/backups -mtime +14 -delete
```

## HTTPS con dominio propio

Cuando tengas dominio apuntado con un A record al IP del VPS:

1. En `Caddyfile`, cambia `:80 {` por `fondo.tudominio.mx {` (sin el `:80`).
2. En `docker-compose.yml`, descomenta la línea `- "443:443"` bajo el
   servicio `caddy`.
3. Abre el puerto **443** en el firewall de Vultr.
4. Reinicia: `docker compose up -d`.

Caddy pide el certificado a Let's Encrypt automáticamente y lo renueva
para siempre. Cero configuración adicional.

## Consola H2 en producción

Está **apagada por defecto** en el compose
(`SPRING_H2_CONSOLE_ENABLED=false`) porque expone la BD a quien tenga
la URL. Si necesitas correr SQL directo puntualmente:

Opción A — encender temporal:

```bash
# En docker-compose.yml pon SPRING_H2_CONSOLE_ENABLED: "true"
docker compose up -d app
# Accede vía SSH tunnel para no exponer al mundo:
ssh -L 8080:localhost:8080 root@TU_IP
# En tu navegador local: http://localhost:8080/h2
# JDBC URL: jdbc:h2:file:/opt/fondo/data/fondo
# User: sa   Password: (vacío)
# Al terminar, vuelve a false y reinicia.
```

Opción B — bajar la BD a local (más seguro) y usar la consola H2 local.

## Comandos útiles

```bash
# Ver logs en vivo (o mejor, entra a Dozzle en /logs/)
docker compose logs -f app

# Reiniciar solo la app tras un git pull
git pull
docker compose up -d --build app

# Reiniciar todo el stack
docker compose restart

# Parar todo (los datos en ./data se quedan)
docker compose down

# Detener y borrar volúmenes de Caddy (certs quedan perdidos)
docker compose down -v

# Ver uso de recursos
docker stats
```

## Actualizaciones

```bash
cd /opt/fondo
git pull
docker compose up -d --build app
```

El `--build app` solo reconstruye la imagen de la app. Caddy y Dozzle
no necesitan rebuild.

## Seguridad — cosas a revisar antes de producción real

- **`remember-me` key** en `SeguridadConfig.java` está hardcodeada
  (`fondo-graduacion-remember-me-2026`). Para un despliegue serio,
  muévela a variable de entorno y ponla en el compose:
  ```yaml
  environment:
    APP_REMEMBER_ME_KEY: ${REMEMBER_ME_KEY}
  ```
  y en `SeguridadConfig` lee con `@Value("${app.remember-me.key}")`.

- **HTTPS**: nunca dejes esto en HTTP puro en producción real — las
  cookies de sesión y remember-me viajan en claro. Monta el dominio +
  Let's Encrypt cuanto antes.

- **Backup**: haz que el cron backup funcione y prueba una restauración
  al menos una vez antes de confiar en él.

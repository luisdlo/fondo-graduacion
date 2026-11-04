# Deploy en VPS (Vultr) con Docker + Caddy + Dozzle

Guía práctica para levantar el sistema en un VPS. Está pensada para el
setup mínimo: la app en el puerto 80 detrás de Caddy, Dozzle para leer
los logs de todos los contenedores, y la BD H2 en un archivo dentro de
`./data` para poder bajarla a local con un simple `scp`.

## Arquitectura

```
Internet → :80 Caddy ─┬─► /logs* → Dozzle (basic auth)
                      └─► /*      → App Spring Boot :8080
                                     └─► /root/proyect/fondo-graduacion/data (bind mount; en el contenedor /opt/fondo/data)
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
git clone https://github.com/luisdlo/fondo-graduacion.git /root/proyect/fondo-graduacion
cd /root/proyect/fondo-graduacion

# 2) Genera el hash de tu password para Dozzle
docker run --rm caddy caddy hash-password --plaintext "N4LO66vLWky"
#   Salida: $2a$14$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 3) Pega el hash en Caddyfile reemplazando el placeholder
nano Caddyfile
# La linea:  admin $2a$14$PONER_AQUI_HASH_GENERADO...
#admin $2a$14$2n.OKNNrsnNKmpBROGnMMOcA9GGo1lMywmBlXjmX8JEQWOe7AiGvq
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
scp root@TU_IP:/root/proyect/fondo-graduacion/data/fondo.mv.db ~/Downloads/backup-$(date +%F).mv.db
```

### Bajar BD + comprobantes

```bash
# Con rsync (incremental, solo baja lo que cambió):
rsync -avz root@TU_IP:/root/proyect/fondo-graduacion/data/ ~/backups/fondo-$(date +%F)/
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
0 3 * * * tar czf /root/backups/fondo-$(date +\%F).tar.gz -C /root/proyect/fondo-graduacion data && find /root/backups -mtime +14 -delete
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

## Acceso a la BD en producción

Las tres opciones usan la **misma BD real** (salvo la C). Lo que se cambie se aplica en
producción: respalda antes de editar.

**Opción A — Consola H2 en el navegador** (ya encendida, protegida por Caddy con basic auth):

```
https://fondo-ingles.ddns.net/h2        (usuario admin + password de Dozzle)
JDBC URL: jdbc:h2:file:/opt/fondo/data/fondo   (ruta dentro del contenedor, no del VPS)
User: sa   Password: (vacío)
```

**Opción B — IntelliJ / DataGrip en vivo** (servidor TCP de H2 en el 9092, solo en localhost del VPS):

```bash
# En tu Mac, deja esta terminal abierta mientras uses IntelliJ
ssh -N -L 9092:localhost:9092 root@TU_IP
# IntelliJ → Data Source H2 → Connection type: Remote
# URL:  jdbc:h2:tcp://localhost:9092//opt/fondo/data/fondo   (doble / antes de opt)
# User: sa   Password: (vacío)   Driver: H2 2.2.224 (la misma versión que la app)
```

El servidor TCP lo levanta la app (`FondoApplication`) solo si existe `FONDO_H2_TCP_PORT`
(definido en `docker-compose.yml`). El puerto se publica como `127.0.0.1:9092:9092`:
**nunca** quitar el `127.0.0.1`, la BD usa `sa` sin password y quedaría abierta a internet.
Para verificarlo en el VPS: `ss -ltnp | grep 9092` debe mostrar `127.0.0.1:9092`.

**Opción C — Bajar una copia a local** (sin riesgo; ver "Backup / bajar la BD a local").

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

## Proteger tu configuración local del `git pull`

Al desplegar editas dos archivos con datos específicos del VPS:

- `Caddyfile` — dominio real y hash bcrypt del password de Dozzle
- `docker-compose.yml` — puerto 443 descomentado

Si estos archivos están versionados en git y haces `git pull`, git te va
a marcar merge conflicts o te va a sobrescribir tu config. Para evitarlo
márcalos como "asume sin cambios" **una sola vez** después del deploy
inicial:

```bash
cd /root/proyect/fondo-graduacion
git update-index --assume-unchanged Caddyfile docker-compose.yml
```

De ahora en adelante `git pull` los ignora — jala todo lo demás
(código, `schema.sql`, `Dockerfile`, etc.) pero deja tus dos archivos
locales intactos.

Si algún día quieres que git los vuelva a ver (para hacer un commit
desde el VPS o resincronizar con lo del repo):

```bash
git update-index --no-assume-unchanged Caddyfile docker-compose.yml
```

## Actualizaciones (deploy nueva versión del código)

Ya con la protección anterior, actualizar es una línea:

```bash
cd /root/proyect/fondo-graduacion
git pull
docker compose up -d --build app
```

El `--build app` solo reconstruye la imagen de la app. Caddy y Dozzle
no necesitan rebuild (usan imágenes públicas que se descargan).

Si querés forzar que Caddy y Dozzle también refresquen a sus últimas
imágenes públicas:

```bash
docker compose pull
docker compose up -d --build app
```

## Redeploy desde cero (borrar todo y empezar limpio)

Útil si algo se corrompió, la BD quedó mal o querés probar el flujo
completo. **Antes de nada, backup:**

```bash
cd /root/proyect/fondo-graduacion
tar czf /root/backup-antes-de-redeploy-$(date +%F-%H%M).tar.gz -C /root/proyect/fondo-graduacion data
ls -lh /root/backup-antes-de-redeploy-*.tar.gz   # verifica que se creó
```

### Opción A — Redeploy conservando la BD

```bash
cd /root/proyect/fondo-graduacion

# 1) Bajar todos los contenedores (los datos en ./data se quedan)
docker compose down

# 2) Traer el código más reciente
git pull

# 3) Reconstruir imagen y arrancar
docker compose up -d --build

# 4) Ver logs de la app para confirmar arranque limpio
docker compose logs -f app
```

Los grupos, niños, movimientos y comprobantes siguen porque `./data`
no se toca.

### Opción B — Redeploy borrando la BD (nuclear, empieza en cero)

⚠️ Esto **borra todos los movimientos, passwords cambiados, etc.**
`schema.sql` va a recrear los 3 grupos con `default123` como si fuera
la primera vez.

```bash
cd /root/proyect/fondo-graduacion

# 1) Backup por si acaso (ya lo hiciste arriba pero por si)
tar czf /root/backup-nuclear-$(date +%F-%H%M).tar.gz -C /root/proyect/fondo-graduacion data

# 2) Bajar contenedores y borrar volúmenes de Caddy
docker compose down -v

# 3) Borrar todos los datos de la BD y comprobantes
rm -rf data/*.db data/comprobantes/*

# 4) Traer código y levantar de cero
git pull
docker compose up -d --build

# 5) Verificar que schema.sql corrió y creó las tablas + datos iniciales
docker compose logs app | grep -i "schema\|hikari\|started"
```

En el primer arranque después de esto, `schema.sql` mete los 3 grupos
con `default123` y los 56 niños. HTTPS sigue funcionando porque los
volúmenes de Caddy (`caddy-data`, `caddy-config`) se recrean y Caddy
pide un cert nuevo al detectar el dominio.

### Opción C — Reinstalar desde otro VPS (mudanza completa)

```bash
# En el VPS actual, backup y bajarlo a tu Mac:
ssh root@VIEJO_VPS
cd /root/proyect/fondo-graduacion
docker compose down
tar czf /tmp/fondo-full-backup.tar.gz -C /root/proyect/fondo-graduacion data Caddyfile docker-compose.yml
exit
scp root@VIEJO_VPS:/tmp/fondo-full-backup.tar.gz .

# En el VPS nuevo, después de instalar docker y clonar el repo:
scp fondo-full-backup.tar.gz root@NUEVO_VPS:/tmp/
ssh root@NUEVO_VPS
cd /root/proyect/fondo-graduacion
tar xzf /tmp/fondo-full-backup.tar.gz -C /root/proyect/fondo-graduacion/
git update-index --assume-unchanged Caddyfile docker-compose.yml
docker compose up -d --build
```

En No-IP cambia el A record del hostname al IP del nuevo VPS. Caddy
va a renovar el cert automáticamente cuando el DNS propague.

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

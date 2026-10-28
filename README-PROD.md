# Despliegue en producción

Cómo está montado el sistema en el VPS y cómo subir una versión nueva.
Para la guía larga (instalación desde cero, mudanza de VPS, etc.) ver `README-DEPLOY.md`.

## Cómo está montado

```
Internet ─► fondo-ingles.ddns.net :80/:443 ─► Caddy (HTTPS automático, Let's Encrypt)
                                               ├─ /logs*  → Dozzle    (basic auth)
                                               ├─ /h2*    → Consola H2 (basic auth)
                                               └─ /*      → App Spring Boot :8080
                                                             └─ ./data  (bind mount)
                                                                 ├─ fondo.mv.db    ← la BD completa
                                                                 └─ comprobantes/  ← PDFs y fotos
```

| Qué | Dónde |
|---|---|
| Código en el VPS | `/opt/fondo` (clon de `github.com/luisdlo/fondo-graduacion`) |
| BD y comprobantes | `/opt/fondo/data` (fuera del contenedor, sobrevive a rebuilds) |
| App | https://fondo-ingles.ddns.net |
| Logs (Dozzle) | https://fondo-ingles.ddns.net/logs/ |
| Consola H2 | https://fondo-ingles.ddns.net/h2 |

No hay GitHub Actions: **un `git push` no despliega nada**. El deploy es manual por SSH.

## Subir una versión nueva

Desde tu máquina, sube los cambios:

```bash
git push origin master
```

En el VPS:

```bash
ssh root@TU_IP
cd /opt/fondo

# 1) Respaldo de la BD y comprobantes (por si acaso)
mkdir -p /root/backups
tar czf /root/backups/fondo-antes-deploy-$(date +%F-%H%M).tar.gz -C /opt/fondo data

# 2) Traer el código nuevo
git pull

# 3) Reconstruir y reiniciar solo la app (Caddy y Dozzle siguen igual)
docker compose up -d --build app

# 4) Confirmar que arrancó bien
docker compose logs -f app      # buscar "Started FondoApplication"; Ctrl+C para salir
```

Listo. Los datos no se tocan: `schema.sql` corre en cada arranque pero solo crea lo que
falta (tablas, columnas, vistas) y los datos iniciales solo se insertan si la BD está vacía.

### Si cambió el `Caddyfile` o el `docker-compose.yml`

```bash
docker compose up -d --build          # recrea lo que haya cambiado
```

### Si `git pull` se queja de cambios locales

Puede pasar si en el VPS se marcaron `Caddyfile` / `docker-compose.yml` con
`assume-unchanged` o se editaron a mano:

```bash
git status
git update-index --no-assume-unchanged Caddyfile docker-compose.yml
git diff                              # revisa qué cambiaste en el VPS
git stash && git pull && git stash pop
```

## Lo que borra datos (cuidado)

- `rm` sobre `/opt/fondo/data` o sobre `fondo.mv.db`.
- Correr `docker compose` desde **otra carpeta**: `./data` apuntaría a otro lado y la app
  arrancaría con una BD vacía.

`docker compose down` y `docker compose down -v` **no** borran la BD (es bind mount, no
volumen). `down -v` solo borra los certificados de Caddy, que se vuelven a pedir solos.

## Accesos

**Basic auth de Caddy** (para `/logs` y `/h2`): usuario `admin` y la contraseña cuyo hash
está en el `Caddyfile`. La contraseña **no va en el repo**.

Para cambiarla:

```bash
docker run --rm caddy caddy hash-password --plaintext 'NUEVA_CONTRASEÑA'
# Pega el hash en las dos líneas "admin $2a$14$..." del Caddyfile y:
docker compose restart caddy
```

**Consola H2** (después del basic auth):

| Campo | Valor |
|---|---|
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:file:/opt/fondo/data/fondo` |
| User Name | `sa` |
| Password | *(vacío)* |

Ojo: la consola trae prellenada `jdbc:h2:~/test`; hay que cambiarla o dirá que la BD no existe.

**Vocales**: entran en la app con su usuario de grupo (`grupoa`, `grupob`, `grupoc`).
Si alguno olvidó su contraseña, en la consola H2:

```sql
UPDATE grupo SET password = 'temporal123' WHERE usuario = 'grupoa';
```

y que la cambie desde **🔒 Cambiar contraseña** al entrar.

## Respaldos

Bajar todo a tu máquina (BD + comprobantes):

```bash
rsync -avz root@TU_IP:/opt/fondo/data/ ~/backups/fondo-$(date +%F)/
```

Para verla en local, copia el `fondo.mv.db` a `~/fondo-graduacion-db/fondo.mv.db` y corre
`mvn spring-boot:run`.

Respaldo diario automático en el VPS (`crontab -e`):

```cron
0 3 * * * tar czf /root/backups/fondo-$(date +\%F).tar.gz -C /opt/fondo data && find /root/backups -mtime +14 -delete
```

## Comandos útiles

```bash
docker compose ps                 # qué está corriendo
docker compose logs -f app        # logs de la app
docker compose restart app        # reiniciar sin reconstruir
docker stats                      # memoria / CPU
```

## Dominio (No-IP)

`fondo-ingles.ddns.net` es un hostname gratis de No-IP: hay que **confirmarlo cada 30 días**
desde el correo que manda No-IP, o deja de resolver y la app no abre.
Si cambia la IP del VPS, actualiza el registro en No-IP; Caddy saca el certificado nuevo solo.

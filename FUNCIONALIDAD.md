# Fondo de Graduación — qué hace el sistema

## Para qué sirve

Es un sistema sencillo para llevar el control del dinero que juntan los papás de tres grupos
(A, B y C) para pagar la **fiesta de graduación**. Registra cada peso que entra y cada peso que sale,
y lo muestra a todos para que las cuentas sean **transparentes**.

El sistema **solo registra y suma**. No decide cuánto le toca pagar a cada grupo ni cómo se reparten
los gastos; eso lo acuerdan los papás fuera del sistema.

## Quién lo usa

| Quién | Qué puede hacer | ¿Necesita entrar? |
|---|---|---|
| **Papás y cualquier persona con el link** | Ver todo: totales, movimientos, aportaciones por niño y comprobantes | No |
| **Vocal de un grupo** (uno por grupo) | Todo lo anterior, más registrar ingresos y gastos de **su** grupo y pagos de la graduación | Sí, con usuario y contraseña |
| **Administrador** | Da de alta grupos, vocales, contraseñas y niños, y cancela movimientos | Directo en la base de datos |

Cuando un vocal entra, el sistema ya sabe **quién es y de qué grupo es**. No tiene que escribir su
nombre ni elegir grupo: todo lo que registre queda a su nombre y en su grupo.

## Lo que se ve en pantalla

### Siempre visible: barra de totales
Abajo de la pantalla, en todas las pestañas, hay una barra fija con:
- **Total general**: lo que hay disponible en el fondo.
- **Total del Grupo A, B y C**: lo que ha juntado cada grupo menos lo que ha gastado.

Se actualiza sola cada minuto.

### Pestaña Resumen
- **Fondo de graduación · disponible**: el total general en grande, con el total de los grupos y lo
  que ya se ha pagado de la graduación.
- **Una tarjeta por grupo**: vocal, ingresos, gastos del grupo y cuánto aporta al fondo.
- **Pagos de la graduación**: la lista de pagos hechos desde el fondo común (salón, fotógrafo, etc.).
- **Últimos movimientos**: los 5 registros más recientes.

### Pestaña Movimientos
La lista completa de todo lo que ha entrado y salido, del más reciente al más antiguo. Se puede filtrar por:
- Tipo: ingresos, gastos o ambos.
- Grupo: A, B, C o fondo común.
- Mostrar u ocultar los cancelados.

Cada movimiento muestra:
- Grupo o fondo común, concepto, monto y fecha.
- Niño, si aplica.
- Quién lo registró.
- En los gastos, el comprobante (se puede abrir) o un aviso de **"Sin comprobante"**.

### Pestaña Por niño
Para cada grupo, la lista de niños con **cuántos pagos** ha hecho su familia y el **total aportado**.

## Cómo se registra (solo vocales)

### Entrar
Botón **Entrar (vocales)** → usuario y contraseña. Arriba aparece el nombre del vocal y su grupo, con
la opción **Salir**.

### + Ingreso
Para registrar dinero que entra al fondo del grupo.
- **Concepto**: siempre "Fondo de graduación" (no se puede cambiar).
- **Niño**: opcional; se elige de los niños del grupo del vocal.
- **Monto**: solo **$500** o **$1,000**.
- **Fecha**: se pone sola, la de hoy.

### − Gasto / pago
Para registrar dinero que sale.
- **¿De dónde sale?**
  - **Gasto del grupo**: se descuenta del total del grupo del vocal.
  - **Pago de la graduación (fondo común)**: se descuenta del total general, no de un grupo en particular.
- **Concepto**: texto libre, por ejemplo anticipo del salón, invitaciones o fotógrafo.
- **Monto** y **fecha**.
- **Comprobante**: PDF o foto (opcional, pero si falta se marca "Sin comprobante" a la vista de todos).

Después de guardar aparece un aviso de confirmación, o de error si algo no es válido.

## Cómo se calculan los totales

- **Total de un grupo** = ingresos del grupo − gastos del grupo.
- **Total general** = suma de los tres grupos − pagos de la graduación (fondo común).
- Los movimientos **cancelados no cuentan** en ningún total, pero siguen visibles.

## Reglas para que las cuentas sean confiables

- **Todo es visible para todos**: ingresos, gastos, quién registró y comprobantes.
- **Nada se borra.** Si un registro está mal, se **cancela**: queda tachado y visible, con el motivo y
  quién lo canceló.
- Cada vocal solo puede registrar en **su** grupo.
- Los ingresos solo aceptan $500 o $1,000. La base de datos también lo valida, así que ni capturando
  a mano se puede meter otro monto.
- Un niño solo puede tener ingresos en el grupo al que pertenece.

## Lo que todavía NO hace

- **Cancelar desde la pantalla**: por ahora se cancela directo en la base de datos.
- **Dar de alta niños o vocales desde la pantalla**: se hace en la base de datos.
- **Cambiar contraseña**: la pone el administrador en la base de datos (hoy en texto plano).
- **Reportes para imprimir o exportar** (PDF/Excel).
- **Funcionar sin internet**: la página carga una librería (HTMX) desde internet.

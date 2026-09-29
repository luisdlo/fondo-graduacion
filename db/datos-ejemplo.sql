-- Registros de ejemplo y plantillas para capturar a mano (consola H2).
-- Córrelo UNA sola vez; no está en data.sql para que no se duplique en cada arranque.

-- Grupos y vocales
INSERT INTO grupo (id, nombre, vocal) VALUES
  ('A', 'Grupo A', 'Laura Méndez'),
  ('B', 'Grupo B', 'Karla Ruiz'),
  ('C', 'Grupo C', 'Paola Herrera');

-- Usuarios de los vocales (el password lo defines tú; texto plano por ahora)
UPDATE grupo SET usuario = 'laura', password = 'cambiar123' WHERE id = 'A';
UPDATE grupo SET usuario = 'karla', password = 'cambiar123' WHERE id = 'B';
UPDATE grupo SET usuario = 'paola', password = 'cambiar123' WHERE id = 'C';

-- Niños
INSERT INTO nino (nombre, grupo_id) VALUES
  ('Chuchito R.', 'A'), ('Meganita L.', 'A'), ('Peregrinito S.', 'A'),
  ('Valentina P.', 'B'), ('Mateo C.', 'B'),
  ('Renata O.', 'C'), ('Iker D.', 'C');

-- ===== Ingreso (aportación de un niño) =====
-- Concepto y fecha se llenan solos; monto solo 500 o 1000.
INSERT INTO movimiento (tipo, grupo_id, nino_id, monto, registrado_por)
VALUES ('INGRESO', 'A', (SELECT id FROM nino WHERE nombre = 'Chuchito R.'), 1000, 'Laura Méndez');

INSERT INTO movimiento (tipo, grupo_id, nino_id, monto, registrado_por)
VALUES ('INGRESO', 'C', (SELECT id FROM nino WHERE nombre = 'Iker D.'), 500, 'Paola Herrera');

-- ===== Gasto de un grupo =====
INSERT INTO movimiento (tipo, grupo_id, concepto, monto, registrado_por, comprobante)
VALUES ('EGRESO', 'A', 'Invitaciones impresas del grupo', 600, 'Laura Méndez', 'ticket-imprenta.jpg');

-- ===== Pago de la graduación desde el fondo común (grupo_id NULL) =====
INSERT INTO movimiento (tipo, grupo_id, concepto, monto, registrado_por, comprobante)
VALUES ('EGRESO', NULL, 'Anticipo del salón de fiestas', 5000, 'Laura Méndez', 'anticipo-salon.pdf');

-- ===== Cancelar (nunca DELETE) =====
-- UPDATE movimiento
--    SET cancelado = TRUE, motivo_cancelacion = 'Se registró dos veces', cancelado_por = 'Laura Méndez'
--  WHERE id = 1;

-- ===== Consultas =====
-- SELECT * FROM v_total_grupo;
-- SELECT * FROM v_total_general;
-- SELECT * FROM v_total_nino WHERE grupo_id = 'A';
-- SELECT * FROM movimiento ORDER BY fecha DESC, id DESC;

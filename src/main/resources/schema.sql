-- Fondo de graduación · H2 (modo archivo)
-- Seguro de correr en cada arranque de Spring Boot: no borra ni duplica nada.

CREATE TABLE IF NOT EXISTS grupo (
  id      CHAR(1)      PRIMARY KEY,          -- 'A', 'B', 'C'
  nombre  VARCHAR(50)  NOT NULL,
  vocal   VARCHAR(100) NOT NULL              -- un vocal por grupo
);

-- Login: cada vocal entra con el usuario/password de su grupo (se capturan a mano en la BD).
-- El password puede ir en texto plano por ahora, o como {bcrypt}... más adelante.
ALTER TABLE grupo ADD COLUMN IF NOT EXISTS usuario  VARCHAR(50);
ALTER TABLE grupo ADD COLUMN IF NOT EXISTS password VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS uq_grupo_usuario ON grupo(usuario);

CREATE TABLE IF NOT EXISTS nino (
  id        INT          AUTO_INCREMENT PRIMARY KEY,
  nombre    VARCHAR(100) NOT NULL,
  grupo_id  CHAR(1)      NOT NULL REFERENCES grupo(id),
  CONSTRAINT uq_nino_grupo UNIQUE (id, grupo_id)   -- permite validar que el niño sea del mismo grupo del movimiento
);

-- Ingresos y gastos en una sola tabla.
-- grupo_id NULL = fondo común (pagos de la graduación).
-- Nunca se borra: se cancela con UPDATE (cancelado, motivo, cancelado_por).
CREATE TABLE IF NOT EXISTS movimiento (
  id               INT           AUTO_INCREMENT PRIMARY KEY,
  tipo             VARCHAR(7)    NOT NULL,
  grupo_id         CHAR(1)       REFERENCES grupo(id),
  nino_id          INT,
  concepto         VARCHAR(150)  NOT NULL DEFAULT 'Fondo de graduación',
  monto            DECIMAL(10,2) NOT NULL,
  fecha            DATE          NOT NULL DEFAULT CURRENT_DATE,
  registrado_por   VARCHAR(100)  NOT NULL,
  comprobante      VARCHAR(255),                 -- ruta o nombre del PDF/foto
  cancelado        BOOLEAN       NOT NULL DEFAULT FALSE,
  motivo_cancelacion VARCHAR(255),
  cancelado_por    VARCHAR(100),

  CONSTRAINT ck_tipo        CHECK (tipo IN ('INGRESO', 'EGRESO')),
  CONSTRAINT ck_monto       CHECK (monto > 0),
  -- Ingresos: siempre de un grupo, concepto fijo y solo $500 o $1,000
  CONSTRAINT ck_ingreso     CHECK (tipo = 'EGRESO' OR (grupo_id IS NOT NULL AND concepto = 'Fondo de graduación' AND monto IN (500, 1000))),
  -- El niño solo aplica a ingresos
  CONSTRAINT ck_nino        CHECK (nino_id IS NULL OR tipo = 'INGRESO'),
  -- Cancelar exige motivo y quién
  CONSTRAINT ck_cancelacion CHECK (cancelado = FALSE OR (motivo_cancelacion IS NOT NULL AND cancelado_por IS NOT NULL)),
  -- El niño debe ser del mismo grupo que el movimiento
  CONSTRAINT fk_nino_grupo  FOREIGN KEY (nino_id, grupo_id) REFERENCES nino(id, grupo_id)
);

-- ---------- Totales (solo movimientos no cancelados) ----------

-- Total por grupo: lo que juntó menos lo que gastó
CREATE OR REPLACE VIEW v_total_grupo AS
SELECT g.id AS grupo_id,
       g.nombre,
       g.vocal,
       COALESCE(SUM(CASE WHEN m.tipo = 'INGRESO' THEN m.monto END), 0) AS ingresos,
       COALESCE(SUM(CASE WHEN m.tipo = 'EGRESO'  THEN m.monto END), 0) AS gastos,
       COALESCE(SUM(CASE WHEN m.tipo = 'INGRESO' THEN m.monto ELSE -m.monto END), 0) AS total
FROM grupo g
LEFT JOIN movimiento m ON m.grupo_id = g.id AND m.cancelado = FALSE
GROUP BY g.id, g.nombre, g.vocal;

-- Total general: suma de los grupos menos los pagos del fondo común
CREATE OR REPLACE VIEW v_total_general AS
SELECT (SELECT COALESCE(SUM(total), 0) FROM v_total_grupo)                                   AS total_grupos,
       (SELECT COALESCE(SUM(monto), 0) FROM movimiento WHERE grupo_id IS NULL AND cancelado = FALSE) AS pagos_graduacion,
       (SELECT COALESCE(SUM(total), 0) FROM v_total_grupo)
     - (SELECT COALESCE(SUM(monto), 0) FROM movimiento WHERE grupo_id IS NULL AND cancelado = FALSE) AS total_general;

-- Aportación por niño
CREATE OR REPLACE VIEW v_total_nino AS
SELECT n.grupo_id, n.id AS nino_id, n.nombre,
       COUNT(m.id)                AS pagos,
       COALESCE(SUM(m.monto), 0)  AS total
FROM nino n
LEFT JOIN movimiento m ON m.nino_id = n.id AND m.cancelado = FALSE
GROUP BY n.grupo_id, n.id, n.nombre;

-- ============================================================================
-- Datos iniciales (solo se cargan si la tabla está vacía; idempotente).
-- En una BD nueva (VPS o local recién clonado) llegan solos en el 1er arranque.
-- Si la tabla ya tiene datos, estas sentencias no hacen nada.
-- ============================================================================

-- Los 3 grupos con vocal, usuario y password inicial "default123".
-- Cada vocal cambia su password desde la UI al primer login (queda en {bcrypt}...).
INSERT INTO grupo (id, nombre, vocal, usuario, password)
SELECT * FROM (VALUES
  ('A', 'Grupo A', 'Idalia',  'grupoa', 'default123'),
  ('B', 'Grupo B', 'Diana',   'grupob', 'default123'),
  ('C', 'Grupo C', 'Jessica', 'grupoc', 'default123')
) AS x(id, nombre, vocal, usuario, password)
WHERE NOT EXISTS (SELECT 1 FROM grupo);

-- Los 56 niños de los tres grupos de 6°: 21 A + 18 B + 17 C.
INSERT INTO nino (nombre, grupo_id)
SELECT * FROM (VALUES
  ('Aranda Perez Jazdhra',              'A'),
  ('Balam Navarro Korra Sofia',         'A'),
  ('Balmes Molina Olivia',              'A'),
  ('Brisson Pacheco Samantha Patricia', 'A'),
  ('Cano Maza Maya',                    'A'),
  ('Castillo Martinez Emiliano',        'A'),
  ('Cruz Jara Cristian Alejandro',      'A'),
  ('Cruz Rosaldo Crista Isabella',      'A'),
  ('Flores Lozano Luca Marcelo',        'A'),
  ('Garcia Vives Livia',                'A'),
  ('Hernandez Pech Matias Omar',        'A'),
  ('Hernandez Torres Yeiden Roberto',   'A'),
  ('Jaime Lares Estefanía',             'A'),
  ('Loeza Constantino Victor Joshua',   'A'),
  ('Martinez Lopez Cloe',               'A'),
  ('Najera Contreras Gael',             'A'),
  ('Pat Azcorra Dante Akim',            'A'),
  ('Rocha Flores Antonella',            'A'),
  ('Sansores Antonio Frida Paola',      'A'),
  ('Vargas Lievin Chloe',               'A'),
  ('Yepez Sandria Corina',              'A'),
  ('Alamilla Garcia Alexa Nicole',      'B'),
  ('Balmes Molina Pablo',               'B'),
  ('Davids Plaate Micaela Guadalupe',   'B'),
  ('Franco Gutierrez Carlo Mateo',      'B'),
  ('Guerrero Ledesma Daniela',          'B'),
  ('Iglesias Solano Dominica',          'B'),
  ('Jaime Lares Valeria',               'B'),
  ('Ku Romero Nicte Esperanza',         'B'),
  ('Lara Foyo Renata',                  'B'),
  ('Linche Torres Camilo',              'B'),
  ('Madala Flores Allegra Ines',        'B'),
  ('Mar Vazquez Alaia Valeria',         'B'),
  ('Marquez Castillo Selva Victoria',   'B'),
  ('Martinez Martinez Mia Alejandra',   'B'),
  ('Reynal Bustamante Emiliano',        'B'),
  ('Ruiz Suárez Maria',                 'B'),
  ('Vallejo Gonzalez Valentina',        'B'),
  ('Vazquez Delgadillo Mia Livier',     'B'),
  ('Arteaga Lopez Sofia Victoria',      'C'),
  ('Gentile Sandoval Camille Giuliette','C'),
  ('Gonzalez Molina Santiago Javier',   'C'),
  ('Hernandez Cabrera Sofia',           'C'),
  ('Hernandez Lopez Yaretzi',           'C'),
  ('Jimenez Mata Sofia',                'C'),
  ('Lopez Lucario Arturo Said',         'C'),
  ('Monfil Olivares Jazmin',            'C'),
  ('Morentin Aguirre Alejandro',        'C'),
  ('Paret Rodriguez Emilio',            'C'),
  ('Patrick Levi Sevag',                'C'),
  ('Peraza Lopez Lyam Alexander',       'C'),
  ('Perez Ortega Samuel David',         'C'),
  ('Real Ramos Renata',                 'C'),
  ('Rodriguez Rivera Luna Isabella',    'C'),
  ('Verdalet Lopez Matias Olmar',       'C'),
  ('Vidales Pimentel Leon Emilio',      'C')
) AS x(nombre, grupo_id)
WHERE NOT EXISTS (SELECT 1 FROM nino);

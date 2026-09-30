-- Niños de los tres grupos de 6°. Correr UNA sola vez en la consola H2.
-- Grupo A: 21 niños · Grupo B: 18 niños · Grupo C: 17 niños (los #8 y #17 del listado C estaban tachados).
--
-- Si ya corriste datos-ejemplo.sql, borra primero los niños de prueba
-- (los que no tienen ingresos asociados) para no dejar registros de mentira:
--   DELETE FROM nino WHERE nombre IN (
--     'Chuchito R.', 'Meganita L.', 'Peregrinito S.',
--     'Valentina P.', 'Mateo C.',
--     'Renata O.', 'Iker D.');

-- ===== Grupo A =====
INSERT INTO nino (nombre, grupo_id) VALUES
  ('Aranda Perez Jazdhra',             'A'),
  ('Balam Navarro Korra Sofia',        'A'),
  ('Balmes Molina Olivia',             'A'),
  ('Brisson Pacheco Samantha Patricia','A'),
  ('Cano Maza Maya',                   'A'),
  ('Castillo Martinez Emiliano',       'A'),
  ('Cruz Jara Cristian Alejandro',     'A'),
  ('Cruz Rosaldo Crista Isabella',     'A'),
  ('Flores Lozano Luca Marcelo',       'A'),
  ('Garcia Vives Livia',               'A'),
  ('Hernandez Pech Matias Omar',       'A'),
  ('Hernandez Torres Yeiden Roberto',  'A'),
  ('Jaime Lares Estefanía',            'A'),
  ('Loeza Constantino Victor Joshua',  'A'),
  ('Martinez Lopez Cloe',              'A'),
  ('Najera Contreras Gael',            'A'),
  ('Pat Azcorra Dante Akim',           'A'),
  ('Rocha Flores Antonella',           'A'),
  ('Sansores Antonio Frida Paola',     'A'),
  ('Vargas Lievin Chloe',              'A'),
  ('Yepez Sandria Corina',             'A');

-- ===== Grupo B =====
INSERT INTO nino (nombre, grupo_id) VALUES
  ('Alamilla Garcia Alexa Nicole',     'B'),
  ('Balmes Molina Pablo',              'B'),
  ('Davids Plaate Micaela Guadalupe',  'B'),
  ('Franco Gutierrez Carlo Mateo',     'B'),
  ('Guerrero Ledesma Daniela',         'B'),
  ('Iglesias Solano Dominica',         'B'),
  ('Jaime Lares Valeria',              'B'),
  ('Ku Romero Nicte Esperanza',        'B'),
  ('Lara Foyo Renata',                 'B'),
  ('Linche Torres Camilo',             'B'),
  ('Madala Flores Allegra Ines',       'B'),
  ('Mar Vazquez Alaia Valeria',        'B'),
  ('Marquez Castillo Selva Victoria',  'B'),
  ('Martinez Martinez Mia Alejandra',  'B'),
  ('Reynal Bustamante Emiliano',       'B'),
  ('Ruiz Suárez Maria',                'B'),
  ('Vallejo Gonzalez Valentina',       'B'),
  ('Vazquez Delgadillo Mia Livier',    'B');

-- ===== Grupo C =====
INSERT INTO nino (nombre, grupo_id) VALUES
  ('Arteaga Lopez Sofia Victoria',       'C'),
  ('Gentile Sandoval Camille Giuliette', 'C'),
  ('Gonzalez Molina Santiago Javier',    'C'),
  ('Hernandez Cabrera Sofia',            'C'),
  ('Hernandez Lopez Yaretzi',            'C'),
  ('Jimenez Mata Sofia',                 'C'),
  ('Lopez Lucario Arturo Said',          'C'),
  ('Monfil Olivares Jazmin',             'C'),
  ('Morentin Aguirre Alejandro',         'C'),
  ('Paret Rodriguez Emilio',             'C'),
  ('Patrick Levi Sevag',                 'C'),
  ('Peraza Lopez Lyam Alexander',        'C'),
  ('Perez Ortega Samuel David',          'C'),
  ('Real Ramos Renata',                  'C'),
  ('Rodriguez Rivera Luna Isabella',     'C'),
  ('Verdalet Lopez Matias Olmar',        'C'),
  ('Vidales Pimentel Leon Emilio',       'C');

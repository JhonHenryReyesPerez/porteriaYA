-- Guarda el texto extraido del documento, en bloques con su pagina.
--
-- §11 dice que PostgreSQL es la fuente de verdad y que Qdrant es una copia
-- derivada que `reindexar` puede reconstruir. Sin el contenido aqui eso no era
-- posible: del documento solo quedaba el hash, asi que reconstruir la coleccion
-- habria exigido volver a subir todos los archivos a mano.
--
-- Se guarda como JSON [{"pagina":1,"texto":"..."}] y no como un unico bloque
-- para no perder los numeros de pagina, que son lo que permite que la cita diga
-- «Reglamento, p. 7» en vez de solo «Reglamento».

ALTER TABLE documento ADD COLUMN contenido TEXT;

COMMENT ON COLUMN documento.contenido IS
    'Bloques de texto extraidos, en JSON. Permite reindexar sin volver a subir el archivo';

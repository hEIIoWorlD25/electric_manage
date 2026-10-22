-- Ejecutar como root desde MySQL Workbench. Cambia la contraseña ANTES de ejecutar.
CREATE DATABASE IF NOT EXISTS electricis_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'electricis_app'@'localhost' IDENTIFIED BY 'REEMPLAZAR_CON_TU_CLAVE';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES, INDEX ON electricis_manager.* TO 'electricis_app'@'localhost';
-- Las tablas se crean con: python manage.py init-db
-- No se usa root desde la aplicación ni se abre el puerto 3306 al teléfono.

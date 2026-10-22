# Verificación técnica — 21 de octubre de 2026

## API: pruebas automáticas de lógica
Se ejecutó `python -m pytest tests -q` con Python 3.12.
Resultado: **5 pruebas aprobadas**, que agrupan verificaciones de:
- bloqueo de acceso sin sesión y credenciales incorrectas;
- revocación de token al cerrar sesión;
- permisos de administrador y técnico;
- creación de proyectos y actividades;
- avance derivado de actividades completadas;
- autor e historial de cambios;
- registro de informes con autor tomado de la sesión;
- bloqueo de cambios de tareas en proyectos finalizados;
- rechazo de fechas, estados y responsables inválidos;
- prohibición de actualizar tareas de otro técnico;
- rechazo de cambios de fechas que dejen actividades fuera del proyecto;
- rechazo de informes futuros y de autor enviado por el cliente.

Estas cinco pruebas usan una base SQLite temporal aislada para la lógica del servidor.
No cambian la base configurada ni sustituyen MySQL en la aplicación.
Se observó una advertencia de obsolescencia interna de Starlette/AnyIO, sin fallos de pruebas.

## Integración real con MySQL
Se ejecutó una comprobación adicional con **MySQL Community Server 8.4.0**,
PyMySQL 1.1.1 y SQLAlchemy 2.0.40 en un servidor temporal local Linux.
La API utilizó un usuario propio con los permisos especificados en el script SQL.
Resultados comprobados:

1. Creación de las seis tablas y respuesta health con database=mysql.
2. Creación de usuarios con contraseñas derivadas mediante scrypt.
3. Login y rechazo de creación de proyectos por un técnico.
4. Registro de proyecto con caracteres acentuados.
5. Registro de actividad, actualización por técnico asignado e historial.
6. Cálculo de avance al 100 % al completar la única actividad.
7. Registro y consulta de informe; consulta SQL confirma su persistencia.
8. Indicadores del panel y finalización del proyecto.
9. Cierre de sesión y rechazo del token revocado.

Salida de la comprobación:
```text
MYSQL 8.4.0
PASS health y creación de tablas MySQL
PASS login, roles, proyecto, actividad, historial y avance
PASS informe persistido, panel, finalización y revocación de sesión
```

El servidor temporal se detuvo después de las pruebas. No se usaron datos reales.
La instalación en Windows debe verificarse siguiendo GUIA_INSTALACION.html.

## Android
Se compiló el proyecto completo con `:app:assembleDebug`.
Resultado: **BUILD SUCCESSFUL**, 35 tareas ejecutadas.
Entorno de compilación: JDK 17, Gradle 8.11.1, AGP 8.9.2,
Kotlin 2.1.20, SDK Platform 35 y Build-Tools 35.0.0.
Se generó `apk/ElectricisManager-0.7.0-debug.apk`.

La compilación valida dependencias, recursos, manifiestos y código Kotlin.
No equivale a una prueba visual ni a una prueba de conexión desde el teléfono.
El APK es de depuración, firmado con una clave de desarrollo; no es una versión de tienda.

## Lo que no se ha probado
- Recorrido visual en un emulador o teléfono físico.
- Instalación en la computadora de la estudiante.
- Conexión completa teléfono → API → MySQL en su red local.
- Accesibilidad con lector de pantalla, carga concurrente y rendimiento de campo.
- Recuperación de copias de seguridad o despliegue público.

Las pruebas de aceptación para completar esa verificación están en API_Y_PRUEBAS.md.
Este registro distingue código implementado, pruebas ejecutadas y validación pendiente.

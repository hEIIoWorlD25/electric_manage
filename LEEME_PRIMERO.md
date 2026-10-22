# Electricis Manager — entrega académica 0.7.0

Aplicación Android nativa para Mantenimiento Ingeniería y Proyectos GTR S.A.C.
Base central: **MySQL 8.4**. Interfaz: Kotlin + Jetpack Compose. API: Python + FastAPI.

1. Abre **GUIA_INSTALACION.html** con doble clic. Explica cada conexión en Windows.
2. Instala MySQL, Python 3.12 y Android Studio siguiendo la guía.
3. Configura `database/01_create_database.sql` y `backend/.env`.
4. Crea tablas y usuarios; inicia la API.
5. Abre **la carpeta android** desde Android Studio y ejecuta la app.

No necesitas copiar archivos Kotlin uno a uno: abre el proyecto completo para
conservar la estructura y las dependencias.

## Carpetas
- apk/: APK de depuración compilado para pruebas; requiere la API y MySQL activos.
- android/: proyecto Android Studio y Gradle Wrapper.
- backend/: API, configuración, comandos de inicio y pruebas.
- database/: creación de base/usuario y esquema de tablas de referencia.
- docs/: alcance, arquitectura, endpoints y resultados de verificación.

## Alcance
Login y roles, panel, proyectos, actividades con historial, informes y avance básico.
El 70 % representa un hito propuesto de funcionalidades, no una nota ni certificación.
Consulta `docs/ALCANCE_Y_ARQUITECTURA.md` para los pendientes.
Consulta `docs/VERIFICACION.md` para conocer exactamente qué se ejecutó y qué no.

No hay contraseñas predeterminadas. Se crean localmente mediante `manage.py create-user`.
Los datos opcionales DEMO son ficticios. No contienen información real de la empresa.

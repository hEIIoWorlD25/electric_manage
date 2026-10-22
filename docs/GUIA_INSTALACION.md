# Electricis Manager
## Guía de instalación y conexión · Windows · versión 0.7.0

La instalación tiene tres partes: **MySQL guarda los datos; la API procesa las solicitudes; Android muestra las pantallas**. Deben estar funcionando MySQL y la API antes de iniciar sesión desde el teléfono. La computadora hará de servidor durante la demostración.

## 1. Descomprimir y reconocer los archivos

Descarga el ZIP, pulsa botón derecho → Extraer todo. Coloca la carpeta resultante en `C:\Proyectos\ElectricisManager`. Si el descompresor crea dos carpetas con el mismo nombre, usa la que contiene `backend`, `android` y `database`.

No ejecutes archivos dentro del ZIP. No copies archivos Kotlin de forma individual: Android Studio necesita abrir toda la carpeta `android`.

| Archivo o carpeta | Para qué sirve | Dónde se usa |
|---|---|---|
| database/01_create_database.sql | Crea base y usuario MySQL | MySQL Workbench |
| backend/.env.example | Modelo de configuración | Se copia como backend/.env |
| backend/manage.py | Crea tablas, usuarios y datos opcionales | Terminal, dentro de backend |
| backend/start_windows.bat | Enciende la API local | Doble clic |
| android/settings.gradle.kts | Identifica el proyecto Android | Abrir carpeta android |
| docs/VERIFICACION.md | Pruebas realizadas y límites | Editor de texto |

## 2. Instalar los programas

Instala **MySQL Community Server 8.4 LTS** y **MySQL Workbench** desde los sitios oficiales. Durante MySQL Configurator selecciona una instalación de desarrollo, puerto `3306` y servicio de Windows. Define y conserva la contraseña de `root`. Comprueba que el servicio queda iniciado. No es necesario habilitar acceso remoto al puerto 3306.

- MySQL Server: [Descarga oficial](https://dev.mysql.com/downloads/mysql/) (elige la rama 8.4 LTS y Windows).
- Workbench: [Descarga oficial](https://dev.mysql.com/downloads/workbench/)
- Python 3.12: [Descarga oficial](https://www.python.org/downloads/windows/) (instala Python Launcher y marca Add Python to PATH).
- Android Studio: [Descarga oficial](https://developer.android.com/studio)

No necesitas XAMPP. Este proyecto utiliza MySQL Community; algunas distribuciones de XAMPP incluyen MariaDB, que no es la base solicitada.

En Android Studio instala Android SDK Platform 35 y Build-Tools 35.0.0 desde SDK Manager. En Device Manager crea un dispositivo virtual, por ejemplo Pixel con Android API 35. Activa virtualización en Windows/BIOS si el emulador la solicita. El proyecto requiere JDK 17 para Gradle y utiliza Gradle 8.11.1 / Android Gradle Plugin 8.9.2. No aceptes actualizar automáticamente las versiones antes de probar esta entrega.

## 3. Crear la base de datos MySQL

Abre Workbench y conecta con `root` a `127.0.0.1:3306`. Usa File → Open SQL Script y abre `database/01_create_database.sql`.

Antes de ejecutarlo, reemplaza `REEMPLAZAR_CON_TU_CLAVE` por una contraseña propia para `electricis_app`. Para evitar errores al escribir el script, usa al menos 16 caracteres entre letras, números y símbolos que no incluyan comillas simples ni barra invertida. Esta es la contraseña de la base, distinta de la cuenta para entrar a la app.

Ejecuta el script con el botón del rayo. Debe aparecer el esquema `electricis_manager` al actualizar Schemas. Las tablas se crearán en el paso 5.

Si ya habías ejecutado el script, cambiar el texto de CREATE USER IF NOT EXISTS no cambia la contraseña existente. Para cambiarla ejecuta explícitamente, con tu clave real:

```sql
ALTER USER 'electricis_app'@'localhost' IDENTIFIED BY 'TU_NUEVA_CLAVE';
```

## 4. Instalar la API y configurar la conexión

En `backend`, haz doble clic en `setup_windows.bat`. Espera a que termine. Creará un entorno Python `.venv`, instalará las dependencias y copiará `.env.example` a `.env` si este último no existe.

Abre `backend/.env` con Bloc de notas. Debe quedar así, sustituyendo únicamente la contraseña:

```ini
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=electricis_manager
DB_USER=electricis_app
DB_PASSWORD=TU_CLAVE_DE_MYSQL
```

Si tu contraseña contiene `#` o espacios, escribe su valor entre comillas dobles en `.env`. Activa Ver → Mostrar → Extensiones de nombre de archivo en el Explorador. El archivo debe llamarse `.env`, no `.env.txt`.

Esta es la conexión **API → MySQL**. Ningún archivo Android necesita la contraseña de MySQL.

## 5. Crear tablas y cuentas

Abre la carpeta `backend` en el Explorador, escribe `cmd` en la barra de direcciones y presiona Enter. Se abrirá una terminal en esa carpeta. Ejecuta, uno por uno:

```bat
.venv\Scripts\python.exe manage.py init-db
.venv\Scripts\python.exe manage.py create-user
```

El primer comando crea las tablas sin borrar las existentes. El segundo pregunta nombre, correo, rol y contraseña. Crea primero tu cuenta con rol `admin`. La contraseña debe tener al menos 12 caracteres. Mientras escribes la contraseña no se muestran caracteres: es normal.

Repite el segundo comando para crear un `supervisor` y un `tecnico`. Escribe los roles exactamente en minúsculas y sin tilde. Conserva los correos y las contraseñas: los usarás en la app. No se crean cuentas con contraseñas predeterminadas.

Opcional, solo para practicar con información ficticia:

```bat
.venv\Scripts\python.exe manage.py demo
```

Crea un proyecto DEMO con tres tareas si aún no hay proyectos. No es información real de GTR.

En Workbench verifica:

```sql
USE electricis_manager;
SHOW TABLES;
SELECT id, name, email, role FROM users;
```

Deberás ver users, sessions, projects, tasks, task_history y reports. El archivo `02_schema_reference.sql` documenta las tablas; no necesitas ejecutarlo si usaste `init-db`.

## 6. Encender y comprobar la API

Haz doble clic en `backend/start_windows.bat`. Mantén abierta esa ventana. Debe indicar que Uvicorn está ejecutándose en `http://127.0.0.1:8000`.

Abre en el navegador de la computadora:

- http://127.0.0.1:8000/health — debe mostrar `{"status":"ok","database":"mysql"}`.
- http://127.0.0.1:8000/docs — muestra la documentación interactiva de la API.

Si health no funciona, resuelve ese error antes de abrir Android. Para probar el login en docs, abre POST /auth/login → Try it out e ingresa tu correo y contraseña en el JSON. Copia el campo token de la respuesta; pulsa Authorize e introduce solo ese token. Después podrás probar GET /projects.

## 7. Abrir y ejecutar Android

En Android Studio selecciona Open y elige `C:\Proyectos\ElectricisManager\android`. Espera a que Gradle descargue las dependencias; la primera vez necesita Internet.

En Settings → Build, Execution, Deployment → Build Tools → Gradle selecciona un Gradle JDK 17. Puedes usar Download JDK para instalarlo si no aparece. El Gradle Wrapper ya está incluido.

Si pide SDK, selecciona el instalado por Android Studio. `local.properties` se genera para tu computadora y no se incluye con una ruta ajena. Selecciona el emulador en la barra superior y pulsa Run ▶ sobre la configuración app.

En la pantalla de acceso, deja esta URL:

```text
http://10.0.2.2:8000/
```

Es la conexión **Android emulado → API de tu computadora**. No pongas `localhost` ni `127.0.0.1` en el emulador: esas direcciones apuntarían al propio dispositivo virtual. Introduce el correo y la contraseña creados con `manage.py create-user`.

La URL se guarda localmente con DataStore. El token no se guarda en disco: si Android termina el proceso, tendrás que iniciar sesión de nuevo. La aplicación necesita conexión con la API para leer y guardar información.

## 7.1. Alternativa: instalar el APK ya compilado

El paquete incluye `apk/ElectricisManager-0.7.0-debug.apk`. Es la misma versión del código fuente, compilada para pruebas. Puedes instalarlo por USB, con el teléfono conectado y autorizado, desde la carpeta principal del proyecto:

```bat
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r apk\ElectricisManager-0.7.0-debug.apk
```

Este comando se ejecuta en CMD. Si quieres recompilar o modificar la app, sigue el paso 7 de Android Studio. Si después instalas una compilación con otra firma y Android rechaza la actualización, desinstala la app anterior antes de instalar la nueva; perderás la URL guardada, pero no los datos de MySQL. El APK no incluye el servidor ni la base: completa siempre los pasos 2 a 6 y la conexión del paso 8 o 9.

## 8. Usar un teléfono físico por USB

Esta es la alternativa más sencilla para la práctica local:

1. En el teléfono activa Opciones de desarrollador y Depuración USB.
2. Conéctalo por USB y acepta la huella de la computadora.
3. Selecciona ese teléfono en Android Studio y ejecuta la app.
4. Abre la terminal de Android Studio y ejecuta:

```bat
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000
```

Si la terminal es PowerShell, usa:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000
```

Si instalaste el SDK en otra ubicación, reemplaza la ruta por la que aparece en SDK Manager. Si hay varios dispositivos conectados, desconecta los demás o añade `-s SERIAL` después de adb.exe.

En el teléfono cambia la URL de la app a `http://127.0.0.1:8000/`. El comando reverse permite llegar a la API local a través del cable. Debes repetirlo si se pierde la conexión USB. La ventana de la API debe seguir abierta.

## 9. Alternativa: teléfono por Wi-Fi

Usa esta opción solo en una red privada de práctica. Detén la API con Ctrl+C y, desde backend, inicia:

```bat
.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Ejecuta `ipconfig` y busca la IPv4 del adaptador Wi-Fi, por ejemplo `192.168.1.50`. Conecta el teléfono a la misma red y configura la URL `http://192.168.1.50:8000/`. La IP es solo un ejemplo: usa la de tu computadora.

Si Windows solicita acceso de red, permite la API solo en redes privadas. No desactives el firewall completo y no abras puertos en el router. MySQL continúa escuchando localmente; el teléfono se comunica únicamente con el puerto 8000 de la API.

## 10. Recorrido de comprobación

1. Entra como admin y crea un proyecto con nombre, ubicación, responsable y fechas.
2. Abre el proyecto, registra una actividad y asígnala al técnico.
3. Coloca una fecha dentro del rango del proyecto y selecciona prioridad.
4. Cierra sesión; entra como técnico y abre ese proyecto.
5. Cambia la tarea asignada a en proceso y luego completada. No debe poder editar una tarea ajena.
6. Abre Historial y comprueba el autor y los cambios.
7. Registra un informe con trabajo realizado, incidencias y observaciones.
8. Entra como supervisor, actualiza y verifica el informe y el avance.
9. Con una tarea total completada debe verse 100 %. Sin tareas se muestra 0 %.
10. Finaliza el proyecto; sus actividades quedarán bloqueadas hasta reabrirlo.

Para comprobar que se guardó en MySQL, ejecuta en Workbench:

```sql
SELECT * FROM projects;
SELECT * FROM tasks;
SELECT * FROM reports;
SELECT * FROM task_history;
```

## 11. Iniciar el proyecto otro día

No reinstales todo ni vuelvas a crear usuarios. Inicia el servicio MySQL si está detenido, abre `start_windows.bat`, comprueba health y ejecuta Android. Si usas teléfono USB, repite adb reverse. Si usas Wi-Fi, revisa si cambió la IP.

Para detener el servidor presiona Ctrl+C en su terminal. Los datos permanecen en MySQL. Cerrar Android Studio no borra la base.

## 12. Problemas habituales

| Mensaje o síntoma | Qué revisar |
|---|---|
| py no se reconoce | Reinstalar Python 3.12 incluyendo Python Launcher; abrir otra terminal. |
| Access denied for user | Clave y usuario de .env; si el usuario ya existía, usar ALTER USER. |
| Can't connect to MySQL | Servicio MySQL iniciado, puerto correcto y DB_HOST=127.0.0.1. |
| Unknown database | Ejecutar primero 01_create_database.sql. |
| Table doesn't exist | Ejecutar manage.py init-db. |
| Puerto 8000 ocupado | Cerrar la API anterior; no abrir varias copias. |
| No se pudo conectar desde Android | Revisar health en PC, URL, emulador o adb reverse. |
| Credenciales incorrectas | Usar cuenta creada con create-user; no la de root ni electricis_app. |
| Sesión vencida | Volver a ingresar; dura hasta ocho horas. |
| Error 403 | El rol no permite esa acción o la actividad pertenece a otro técnico. |
| Error 409 al finalizar | Debe haber al menos una actividad y todas deben estar completas. |
| Error 422 | Revisar campos, fechas AAAA-MM-DD y rango del proyecto. |
| Gradle falla por JDK | Elegir JDK 17, no Java 8/11. |
| SDK Platform 35 no instalado | Instalarlo desde SDK Manager junto con Build-Tools 35.0.0. |
| Gradle no descarga dependencias | Comprobar Internet/proxy de Android Studio; volver a Sync Project. |
| Cleartext HTTP no permitido | Ejecutar variante debug para práctica. Release requiere HTTPS. |
| Windows oculta .env | Activar extensiones; comprobar que no sea .env.txt. |

## 13. Qué está listo y qué falta

La entrega implementa los módulos prioritarios y un seguimiento básico. El “70 %” es una ponderación propuesta del alcance, explicada en `docs/ALCANCE_Y_ARQUITECTURA.md`; no equivale a una certificación de producción ni a cumplir el 70 % exacto de la rúbrica del docente.

Faltan mapas, fotos, sincronización offline/Room, exportación de reportes, módulo de materiales/ODS 12, componente Flutter, integración Firebase complementaria y publicación. También faltan pruebas con usuarios reales, respaldo, migraciones y endurecimiento de seguridad para producción. No se incluye eliminación de proyectos ni modificación de informes para conservar las evidencias en esta versión.

La base principal será MySQL. Firebase no reemplaza MySQL en este proyecto. El HTTP local solo está habilitado para depuración; un despliegue real requiere HTTPS.

## 14. Referencias técnicas oficiales

Consultadas para la configuración de esta entrega:

- MySQL 8.4 en Windows: https://dev.mysql.com/doc/refman/8.4/en/windows-installation.html
- Compatibilidad AGP 8.9 / Gradle / JDK: https://developer.android.com/build/releases/agp-8-9-0-release-notes
- Android Studio: [Descarga oficial](https://developer.android.com/studio)
- FastAPI: https://fastapi.tiangolo.com/

Revisa `docs/VERIFICACION.md` antes de presentar resultados de pruebas como evidencias del proyecto.

# Trabajo en campo y avisos · Electricis Manager 0.8.0

## Actualizar tu instalación de Windows

1. Actualiza el código del proyecto. Mantén el archivo `backend/.env` que ya usas y el servicio MySQL activo.
2. Si la API anterior está corriendo, detenla con **Ctrl + C**. Desde CMD en `backend`, ejecuta:

   ```cmd
   .venv\Scripts\python.exe -m pip install -r requirements.txt
   .venv\Scripts\python.exe manage.py init-db
   .venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
   ```

   `init-db` agrega `task_field_plans`, `task_field_entries` y `task_field_photos`.
   Conserva los usuarios, proyectos y actividades existentes. Usa la misma base que tenías funcionando.
3. Deja esa ventana abierta y abre `http://127.0.0.1:8000/health` en la PC.
   Comprueba que responda `{"status":"ok","database":"mysql"}`.
4. Abre la carpeta `android` en Android Studio, sincroniza Gradle y ejecuta **Run** en el emulador.
   El APK histórico de la carpeta `apk` es 0.7.0. Usa **Run** para instalar la versión 0.8.0.
5. Si Android Studio y la API están en la misma PC, configura `http://10.0.2.2:8000/` en la app.
   Desde un celular con tu túnel activo, usa la dirección HTTPS vigente del túnel.

## Probar una actividad

1. Con una cuenta de administrador o supervisor, crea una actividad y asígnala a un técnico.
2. En la actividad, abre **Trabajo en campo**. Escribe un paso por línea y pulsa **Enviar checklist**.
3. Ingresa con el técnico asignado. Abre esa actividad, pulsa **Registrar inicio** y concede el permiso de ubicación.
   En el emulador, puedes enviar un punto desde **Extended Controls (…) → Location** antes de marcar el inicio.
4. Marca los pasos del checklist. Cada cambio se guarda en la API. Toma una foto o selecciónala de la galería.
5. Pulsa **Finalizar trabajo** cuando la foto y todos los pasos asignados estén guardados.
   El estado cambia a **Completada** y actualiza el avance del proyecto.
6. Vuelve a ingresar como supervisor. Abre **Trabajo en campo** para consultar la hora,
   el punto en el mapa, el checklist y la fotografía.

Cada actividad admite un registro de inicio. La hora viene de la API y se muestra en horario de Perú.
La ubicación viene del dispositivo y se captura al pulsar el botón; no hay seguimiento continuo.
Solo el responsable asignado registra y finaliza su trabajo. Administración y supervisión pueden revisar
la evidencia y editar el checklist antes del inicio. Si no se asignó ningún paso, se requiere inicio y foto.
La API conserva una foto JPEG de hasta 2 MB por actividad y elimina sus metadatos al volver a codificarla.

## Probar los avisos

- En **Vista general → Avisos de actividades**, activa el permiso de notificaciones si Android lo solicita.
- Crea una actividad abierta que venza hoy o mañana y otra con fecha anterior, dentro de un proyecto activo.
- Con el técnico asignado, entra a la app o pulsa **Actualizar**. Debes ver **Por vencer** y **Retrasada**.
- Los avisos son locales y revisan las actividades de la última sincronización. Pueden aparecer con la app cerrada;
  Android decide cuándo ejecutar la comprobación en segundo plano. Los cambios de otro usuario se incorporan al
  volver a sincronizar con la API. No se repite un aviso por la misma fecha y condición.
- El técnico recibe avisos de sus actividades; administración y supervisión reciben los de proyectos activos.
  Una actividad completada deja de generar avisos cuando se sincroniza su nuevo estado. Cerrar sesión limpia
  los recordatorios guardados en ese dispositivo.

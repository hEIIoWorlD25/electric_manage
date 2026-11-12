# Electricis Manager 0.9.0

Administrador: crea y edita proyectos, define responsables y fechas, crea usuarios desde Usuarios. También puede asistir en la gestión de actividades.
Supervisor: organiza actividades, asigna responsables, prepara checklists y revisa evidencias. No crea usuarios ni modifica proyectos. Solicita nuevas cuentas desde Bandeja → Solicitar creación de usuario.
Técnico: ejecuta sus actividades y registra evidencia. Puede recibir mensajes de cualquier rol, pero solo enviar o responder a supervisores.

Bandeja muestra recibidos, enviados, estado de lectura y respuesta. Pulsar Actualizar para consultar mensajes nuevos; no es chat push.

Las fechas de proyectos y actividades incluyen hora de Perú (UTC−05). Los registros anteriores mantienen 23:59 hasta editarlos. No se permite que una actividad venza después del proyecto. El contador se actualiza cada 30 segundos en pantalla. Los avisos locales empiezan dentro de las últimas 3 horas y cambian al vencer. Android ejecuta la revisión aproximadamente cada 15 minutos, pudiendo retrasarla por ahorro de batería. Activar notificaciones y sincronizar después de cambiar fechas; los avisos usan la última información descargada y se eliminan al cerrar sesión.

Desde un proyecto, Imprimir / Guardar PDF abre un informe con información, actividades, historial, checklists, GPS, fotografías e informes de campo. El técnico solo ve evidencias de sus propias actividades. Elegir Guardar como PDF o una impresora en el diálogo de Android.

## Actualizar en Windows

El paquete de actualización contiene únicamente archivos de código y documentación. Con Android Studio cerrado y la API detenida (Ctrl+C), copia su contenido sobre la carpeta principal ElectricisManager, reemplazando los archivos incluidos. Conserva tu configuración backend/.env, tu entorno .venv y la base MySQL: no están incluidos en el paquete.

Desde la carpeta backend:

```cmd
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe manage.py init-db
.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

`init-db` agrega `deadlines` y `messages`; no elimina las tablas anteriores. Actualizar API y Android juntos. En Android Studio ejecutar Run o `gradlew.bat assembleDebug` con el JDK configurado. La URL del celular sigue siendo la IP local de la PC, mientras esa IP no cambie.

## Validación manual
1. Admin crea supervisor y técnico; probar correo duplicado.
2. Supervisor solicita cuenta; admin abre el mensaje y crea la cuenta en Usuarios.
3. Técnico escribe al supervisor; verificar que admin no aparezca entre destinatarios.
4. Admin crea proyecto con entrega dentro de 3 horas; supervisor crea actividad dentro del plazo.
5. Activar avisos; comprobar contador, aviso y transición a retraso.
6. Registrar trabajo en campo y exportar PDF; comprobar fotografías y todas las actividades.

## Verificación realizada

Compilación Android `assembleDebug` correcta; 2 pruebas unitarias Android correctas; 9 pruebas de API con SQLite correctas, incluyendo permisos, privacidad de mensajes, horas, impresión con foto y conservación de registros al crear tablas. Falta validar esta versión con MySQL y probar visualmente los nuevos flujos, impresión y entrega de notificaciones en un dispositivo.

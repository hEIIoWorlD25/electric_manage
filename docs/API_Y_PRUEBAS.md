# API REST y demostración

Base local: http://127.0.0.1:8000/ — contrato completo: openapi.json.
Swagger interactivo: /docs. Todas las rutas salvo /health y /auth/login requieren
`Authorization: Bearer TOKEN`. No poner el token en URLs ni compartirlo en capturas.

| Método | Ruta | Acceso / uso |
|---|---|---|
| GET | /health | Conectividad con base |
| POST | /auth/login | Correo y contraseña → token y usuario |
| POST | /auth/logout | Revocar token actual |
| GET | /users | Directorio de responsables autenticados |
| GET | /dashboard | Indicadores globales |
| GET | /projects | Listado con avance calculado |
| POST | /projects | Admin/supervisor: crear |
| PUT | /projects/{id} | Admin/supervisor: modificar |
| GET | /projects/{id}/tasks | Consultar actividades |
| POST | /projects/{id}/tasks | Admin/supervisor: crear actividad |
| PUT | /tasks/{id} | Admin/supervisor: editar datos |
| PATCH | /tasks/{id}/status | Admin/supervisor o técnico asignado |
| GET | /tasks/{id}/history | Consultar historial |
| GET | /projects/{id}/reports | Consultar informes |
| POST | /projects/{id}/reports | Registrar informe con autor de sesión |

La actualización de proyecto y actividad es PUT: enviar todos los campos del
formulario, no solo el campo cambiado. Las fechas usan AAAA-MM-DD.

## Ejemplo de proyecto
```json
{
  "name": "Renovación de tablero",
  "description": "Instalación y pruebas de tablero de distribución",
  "location": "Lima",
  "manager_id": 1,
  "start_date": "2026-10-01",
  "end_date": "2026-12-01",
  "status": "activo"
}
```
El id 1 es solo un ejemplo: usa un administrador/supervisor de GET /users.

## Pruebas automáticas de lógica
Desde backend:
```bat
.venv\Scripts\python.exe -m pip install -r requirements-test.txt
.venv\Scripts\python.exe -m pytest tests -q
```
Las pruebas crean una base temporal aislada con SQLite para validar rutas y permisos.
No tocan MySQL ni tus datos. Esto NO sustituye las pruebas de integración MySQL.
El servidor normal no permite TEST_DATABASE_URL sin APP_ENV=test.

## Pruebas de aceptación manuales
Registra fecha, resultado observado y evidencia real al ejecutar cada caso.
No se incluyen capturas simuladas ni se declaran aprobados sin ejecución.

| Caso | Acción | Resultado esperado |
|---|---|---|
| PA01 | Login con contraseña incorrecta | Rechazo sin acceso al panel |
| PA02 | Login válido | Panel y proyectos visibles |
| PA03 | Crear proyecto con fin anterior a inicio | Rechazo de validación |
| PA04 | Técnico intenta crear proyecto por API | HTTP 403 |
| PA05 | Asignar tarea a técnico | Visible en proyecto con responsable |
| PA06 | Técnico actualiza tarea propia | Estado y avance actualizados |
| PA07 | Técnico actualiza tarea ajena por API | HTTP 403 |
| PA08 | Consultar historial | Cambio, usuario y fecha |
| PA09 | Registrar informe | Autor de sesión y contenido persistidos |
| PA10 | Consultar tablas en Workbench | Mismos datos que en app |
| PA11 | Finalizar con tareas pendientes | HTTP 409 |
| PA12 | Cerrar sesión y reutilizar token | HTTP 401 |
| PA13 | Detener API e intentar actualizar | Mensaje de conexión, sin cierre inesperado |
| PA14 | Reiniciar API y actualizar | Datos MySQL conservados |
| PA15 | Cerrar proceso Android y abrir | Solicita login, conserva URL |

## Extensiones de la próxima entrega
No implementar conexión directa Android→MySQL. Para fotos, mapas o Flutter se
reutiliza el contrato de API. Añadir migraciones de base cuando se cambie el modelo;
create_all crea tablas faltantes, pero no transforma columnas ya existentes.

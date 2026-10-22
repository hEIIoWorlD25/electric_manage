# Electricis Manager · Entrega 0.7.0

Empresa objetivo: Mantenimiento Ingeniería y Proyectos GTR S.A.C.
Proyecto académico; no constituye una implantación aprobada por la empresa.

## Qué significa el 70 %
Es un hito de alcance propuesto, no una medición certificada ni una nota del curso.
La siguiente ponderación organiza 100 puntos de trabajo. Los 70 primeros tienen código
implementado; la validación integral de Android y MySQL se detalla por separado en
VERIFICACION.md y debe completarse antes de afirmar que el producto está listo para uso real.

| Bloque | Puntos del alcance | Entrega |
|---|---:|---|
| Login, sesión revocable y tres roles | 10 | Implementado |
| Registro, consulta y edición de proyectos | 15 | Implementado |
| Actividades, asignación, estados e historial | 15 | Implementado |
| Registro y consulta de informes | 10 | Implementado |
| Panel, avance y detección de retrasos | 10 | Implementado |
| API, modelo MySQL, estructura MVVM y guía | 10 | Implementado |
| Room, lectura offline y cola de sincronización | 6 | Pendiente |
| Google Maps, permisos y ubicaciones | 5 | Pendiente |
| Evidencias fotográficas, integración Firebase complementaria | 4 | Pendiente |
| Reportes exportables y gestión de materiales/ODS 12 | 4 | Pendiente |
| Ejercicios o módulo Flutter según docente | 4 | Pendiente |
| Pruebas en dispositivos, seguridad de producción y publicación | 7 | Pendiente |

## Arquitectura
Android (Compose) → MainViewModel (estado/corrutinas) → Repository (Retrofit)
→ API REST FastAPI → SQLAlchemy/PyMySQL → MySQL 8.4.
DataStore conserva únicamente la URL de la API. No almacena credenciales.
MySQL es la base central obligatoria. SQLite se usa SOLO para pruebas aisladas de
lógica de servidor cuando no está disponible un servidor MySQL; la aplicación normal
no tiene ruta alternativa hacia SQLite.

El token de sesión se mantiene en memoria Android. El servidor guarda solo su hash
SHA-256 y su vencimiento de 8 horas. La contraseña usa scrypt con salt aleatorio.
Cerrar sesión revoca el token en el servidor; si no hay conexión, la app informa el
error y permite reintentar, sin afirmar que la revocación ocurrió.
No hay autorregistro público. Los usuarios se crean desde la consola del servidor.

## Modelo relacional
- users: nombre, correo único, hash de contraseña, rol.
- sessions: hash de token, usuario, fecha de vencimiento.
- projects: nombre, descripción, ubicación textual, responsable, fechas, estado.
- tasks: proyecto, responsable, título, prioridad, vencimiento, estado.
- task_history: actividad, autor del cambio, acción, fecha UTC.
- reports: proyecto, autor, fecha de trabajo, avances, incidencias, observaciones.

users 1:N projects; users 1:N tasks; projects 1:N tasks;
projects 1:N reports; users 1:N reports; tasks 1:N task_history;
users 1:N sessions; users 1:N task_history.

## Reglas de negocio de esta versión
1. Todos los usuarios autenticados de esta única empresa pueden consultar todos los proyectos,
   las actividades, los informes y el directorio mínimo de responsables.
2. Admin y supervisor gestionan todos los proyectos y actividades. No hay aislamiento por cuadrilla.
3. Técnico cambia únicamente estados de tareas asignadas a su usuario. Puede informar en cualquier proyecto.
4. El responsable de un proyecto debe ser administrador o supervisor.
5. Fin de proyecto >= inicio. Vencimiento de tarea dentro de ese intervalo.
6. Avance = tareas completadas / total de tareas × 100, redondeado. Sin tareas = 0 %.
   Todas las tareas tienen el mismo peso. El avance global pondera por número de tareas.
7. Una tarea está retrasada si no está completada y su fecha límite es anterior a hoy.
   El servidor debe utilizar la zona horaria local de la empresa para el cálculo diario.
8. Finalizar exige al menos una tarea y todas completas; bloquea cambios de tareas hasta reabrir el proyecto.
9. Los informes son registros de solo creación y consulta. Su autor se toma de la sesión.
   No se permiten fechas futuras; un informe no modifica automáticamente el avance.
10. No se eliminan proyectos ni evidencias en esta entrega para mantener trazabilidad.
    Por tanto, el CRUD completo con eliminación segura sigue pendiente.
11. Los estados pausado y activo permiten registrar y actualizar actividades.
12. La ubicación es texto, todavía no son coordenadas ni un mapa.

## Historias de usuario entregadas
- HU01: como usuario, ingresar con credenciales y cerrar la sesión revocándola.
- HU02: como supervisor, crear y editar un proyecto con responsable y fechas válidas.
- HU03: como supervisor, asignar actividades y prioridades con plazos dentro del proyecto.
- HU04: como técnico, actualizar mis tareas y consultar el historial.
- HU05: como trabajador, registrar avances, incidencias y observaciones con autor automático.
- HU06: como supervisor, revisar avance, pendientes y retrasos en el panel.

## Alcances aún no cubiertos del sílabo
Persistencia local con Room, sincronización, geolocalización, integración Firebase,
Flutter, investigación de marketing ético, mediciones de recursos y publicación.
MySQL sustituye Firestore como almacenamiento central por decisión del proyecto;
si el docente exige Firebase, puede añadirse como servicio complementario (por ejemplo
notificaciones), previa definición de su uso, sin reemplazar MySQL.
La reducción de papel es una contribución potencial al ODS 12, no un impacto medido.
Se propone registrar uso y desperdicio de materiales en la fase final.

## Antes de uso real
HTTPS, límites de intentos de login, paginación, copias de seguridad restaurables,
migraciones de esquema, auditoría más detallada, control de concurrencia y revisión
de permisos con la empresa. La API no incorpora aún protección contra fuerza bruta;
no exponer este entorno académico directamente a Internet.

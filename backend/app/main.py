"""Electricis Manager: API REST. MySQL es obligatorio salvo pruebas aisladas."""
import os, hashlib, hmac, secrets
from datetime import datetime, timedelta, date, timezone
from typing import Annotated, Literal
from dotenv import load_dotenv
from fastapi import FastAPI, Depends, HTTPException, Request, Response
from starlette.concurrency import run_in_threadpool
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, Field, ConfigDict, model_validator
from sqlalchemy import create_engine, String, Text, ForeignKey, Date, DateTime, Double, JSON, LargeBinary, select, text
from sqlalchemy.dialects.mysql import MEDIUMBLOB
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker, Session
from sqlalchemy.engine import URL
from app.evidence import MAX_PHOTO_BYTES, normalize_jpeg
load_dotenv()

def now(): return datetime.now(timezone.utc).replace(tzinfo=None)
if os.getenv('TEST_DATABASE_URL'):
    if os.getenv('APP_ENV') != 'test': raise RuntimeError('TEST_DATABASE_URL solo se permite en pruebas')
    engine = create_engine(os.environ['TEST_DATABASE_URL'], connect_args={'check_same_thread':False})
else:
    engine = create_engine(URL.create('mysql+pymysql', username=os.getenv('DB_USER','electricis_app'), password=os.getenv('DB_PASSWORD',''), host=os.getenv('DB_HOST','127.0.0.1'), port=int(os.getenv('DB_PORT','3306')), database=os.getenv('DB_NAME','electricis_manager'), query={'charset':'utf8mb4'}), pool_pre_ping=True)
SessionLocal = sessionmaker(engine, expire_on_commit=False)
class Base(DeclarativeBase): pass
class User(Base):
    __tablename__='users'
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    email: Mapped[str] = mapped_column(String(150), unique=True)
    password_hash: Mapped[str] = mapped_column(String(256))
    role: Mapped[str] = mapped_column(String(20))
class LoginSession(Base):
    __tablename__='sessions'
    token_hash: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey('users.id'))
    expires_at: Mapped[datetime] = mapped_column(DateTime)
class Project(Base):
    __tablename__='projects'
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(150))
    description: Mapped[str] = mapped_column(Text)
    location: Mapped[str] = mapped_column(String(200))
    manager_id: Mapped[int] = mapped_column(ForeignKey('users.id'))
    start_date: Mapped[date] = mapped_column(Date)
    end_date: Mapped[date] = mapped_column(Date)
    status: Mapped[str] = mapped_column(String(20), default='activo')
class Task(Base):
    __tablename__='tasks'
    id: Mapped[int] = mapped_column(primary_key=True)
    project_id: Mapped[int] = mapped_column(ForeignKey('projects.id'), index=True)
    title: Mapped[str] = mapped_column(String(150))
    assignee_id: Mapped[int] = mapped_column(ForeignKey('users.id'))
    due_date: Mapped[date] = mapped_column(Date)
    priority: Mapped[str] = mapped_column(String(15))
    status: Mapped[str] = mapped_column(String(20), default='pendiente')
class TaskHistory(Base):
    __tablename__='task_history'
    id: Mapped[int] = mapped_column(primary_key=True)
    task_id: Mapped[int] = mapped_column(ForeignKey('tasks.id'), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey('users.id'))
    action: Mapped[str] = mapped_column(String(255))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now)
class Report(Base):
    __tablename__='reports'
    id: Mapped[int] = mapped_column(primary_key=True)
    project_id: Mapped[int] = mapped_column(ForeignKey('projects.id'), index=True)
    author_id: Mapped[int] = mapped_column(ForeignKey('users.id'))
    work_date: Mapped[date] = mapped_column(Date)
    progress: Mapped[str] = mapped_column(Text)
    incidents: Mapped[str] = mapped_column(Text)
    observations: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=now)

class FieldPlan(Base):
    __tablename__='task_field_plans'
    task_id: Mapped[int] = mapped_column(ForeignKey('tasks.id'), primary_key=True)
    checklist: Mapped[list[str]] = mapped_column(JSON)

class FieldEntry(Base):
    __tablename__='task_field_entries'
    task_id: Mapped[int] = mapped_column(ForeignKey('tasks.id'), primary_key=True)
    started_by: Mapped[int] = mapped_column(ForeignKey('users.id'))
    started_at: Mapped[datetime] = mapped_column(DateTime)
    latitude: Mapped[float] = mapped_column(Double)
    longitude: Mapped[float] = mapped_column(Double)
    accuracy_m: Mapped[float] = mapped_column(Double)
    checklist: Mapped[list[dict]] = mapped_column(JSON)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime)

class FieldPhoto(Base):
    __tablename__='task_field_photos'
    task_id: Mapped[int] = mapped_column(ForeignKey('task_field_entries.task_id'), primary_key=True)
    jpeg: Mapped[bytes] = mapped_column(LargeBinary().with_variant(MEDIUMBLOB(), 'mysql'))

def hash_password(value):
    salt=secrets.token_hex(16)
    digest=hashlib.scrypt(value.encode(),salt=salt.encode(),n=16384,r=8,p=1).hex()
    return salt+':'+digest

def verify(value, stored):
    salt,digest=stored.split(':')
    return hmac.compare_digest(hashlib.scrypt(value.encode(),salt=salt.encode(),n=16384,r=8,p=1).hex(),digest)

def db():
    with SessionLocal() as s: yield s
bearer=HTTPBearer()
def current_user(auth: HTTPAuthorizationCredentials=Depends(bearer), s:Session=Depends(db)):
    session=s.get(LoginSession, hashlib.sha256(auth.credentials.encode()).hexdigest())
    if not session or session.expires_at<=now(): raise HTTPException(401,'Sesión vencida. Ingresa nuevamente.')
    user=s.get(User,session.user_id)
    if not user: raise HTTPException(401,'Usuario no disponible')
    return user

def manager(user):
    if user.role not in ('admin','supervisor'): raise HTTPException(403,'Requiere administrador o supervisor')
def get(s,model,id):
    row=s.get(model,id)
    if not row: raise HTTPException(404,'Registro no encontrado')
    return row

def user_dict(u): return {'id':u.id,'name':u.name,'email':u.email,'role':u.role}
def record(row): return {c.name:getattr(row,c.name) for c in row.__table__.columns}
class Input(BaseModel):
    model_config=ConfigDict(str_strip_whitespace=True, extra='forbid')
class Login(Input):
    model_config=ConfigDict(str_strip_whitespace=False, extra="forbid")
    email:str=Field(min_length=3,max_length=150)
    password:str=Field(min_length=1,max_length=128)
class ProjectInput(Input):
    name:str=Field(min_length=3,max_length=150)
    description:str=Field(max_length=5000)
    location:str=Field(min_length=2,max_length=200)
    manager_id:int=Field(gt=0)
    start_date:date
    end_date:date
    status:Literal['activo','pausado','finalizado']='activo'
    @model_validator(mode='after')
    def dates(self):
        if self.end_date<self.start_date: raise ValueError('La fecha final debe ser posterior o igual a la inicial')
        return self
class TaskInput(Input):
    title:str=Field(min_length=3,max_length=150)
    assignee_id:int=Field(gt=0)
    due_date:date
    priority:Literal['baja','media','alta']='media'
class StatusInput(Input): status:Literal['pendiente','en_proceso','completada']
class ChecklistInput(Input):
    items: list[Annotated[str, Field(min_length=1, max_length=160)]] = Field(max_length=30)

    @model_validator(mode='after')
    def unique_items(self):
        if len({item.casefold() for item in self.items}) != len(self.items):
            raise ValueError('El checklist no puede tener pasos repetidos')
        return self

class CheckAnswers(Input):
    checked: list[Annotated[bool, Field(strict=True)]] = Field(max_length=30)

class WorkStart(Input):
    latitude: float = Field(ge=-90, le=90, allow_inf_nan=False)
    longitude: float = Field(ge=-180, le=180, allow_inf_nan=False)
    accuracy_m: float = Field(ge=0, le=100000, allow_inf_nan=False)

class ReportInput(Input):
    work_date:date
    progress:str=Field(min_length=5,max_length=5000)
    incidents:str=Field(default='',max_length=5000)
    observations:str=Field(default='',max_length=5000)
    @model_validator(mode='after')
    def valid_date(self):
        if self.work_date>date.today(): raise ValueError('No se permiten informes de fechas futuras')
        return self
app=FastAPI(title='Electricis Manager',version='0.8.0',description='API para GTR S.A.C. — entorno académico')
@app.get('/health')
def health(s:Session=Depends(db)):
    s.execute(text('SELECT 1'))
    return {'status':'ok','database':engine.dialect.name}
@app.post('/auth/login')
def login(data:Login,s:Session=Depends(db)):
    u=s.scalar(select(User).where(User.email==data.email.strip().lower()))
    if not u or not verify(data.password,u.password_hash): raise HTTPException(401,'Credenciales incorrectas')
    token=secrets.token_urlsafe(32)
    s.add(LoginSession(token_hash=hashlib.sha256(token.encode()).hexdigest(), user_id=u.id, expires_at=now()+timedelta(hours=8)))
    s.commit()
    return {'token':token,'user':user_dict(u)}
@app.post('/auth/logout')
def logout(auth:HTTPAuthorizationCredentials=Depends(bearer),u:User=Depends(current_user),s:Session=Depends(db)):
    row=s.get(LoginSession,hashlib.sha256(auth.credentials.encode()).hexdigest()); s.delete(row); s.commit()
    return {'message':'Sesión revocada'}
@app.get('/users')
def users(u:User=Depends(current_user),s:Session=Depends(db)):
    return [user_dict(x) for x in s.scalars(select(User).order_by(User.name))]

def project_dict(p,s):
    tasks=list(s.scalars(select(Task).where(Task.project_id==p.id)))
    done=sum(t.status=='completada' for t in tasks)
    return {**record(p),'progress':round(100*done/len(tasks)) if tasks else 0,'total_tasks':len(tasks),'completed_tasks':done}
@app.get('/projects')
def projects(u:User=Depends(current_user),s:Session=Depends(db)):
    return [project_dict(p,s) for p in s.scalars(select(Project).order_by(Project.id.desc()))]
@app.post('/projects',status_code=201)
def create_project(data:ProjectInput,u:User=Depends(current_user),s:Session=Depends(db)):
    manager(u); owner=get(s,User,data.manager_id)
    manager(owner)
    if data.status=='finalizado': raise HTTPException(409,'Crea el proyecto activo antes de finalizarlo')
    p=Project(**data.model_dump()); s.add(p); s.commit(); return project_dict(p,s)
@app.put('/projects/{id}')
def update_project(id:int,data:ProjectInput,u:User=Depends(current_user),s:Session=Depends(db)):
    manager(u); p=get(s,Project,id); manager(get(s,User,data.manager_id))
    existing=list(s.scalars(select(Task).where(Task.project_id==id)))
    if any(not data.start_date<=t.due_date<=data.end_date for t in existing):
        raise HTTPException(409,'El nuevo intervalo deja actividades fuera del proyecto')
    if data.status=='finalizado':
        tasks=list(s.scalars(select(Task).where(Task.project_id==id)))
        if not tasks or any(t.status!='completada' for t in tasks): raise HTTPException(409,'Completa todas las actividades antes de finalizar')
    for k,v in data.model_dump().items(): setattr(p,k,v)
    s.commit(); return project_dict(p,s)
@app.get('/projects/{id}/tasks')
def tasks(id:int,u:User=Depends(current_user),s:Session=Depends(db)):
    get(s,Project,id)
    rows=s.execute(select(Task, User.role, FieldPlan.task_id, FieldEntry.task_id)
        .join(User, User.id==Task.assignee_id)
        .outerjoin(FieldPlan, FieldPlan.task_id==Task.id)
        .outerjoin(FieldEntry, FieldEntry.task_id==Task.id)
        .where(Task.project_id==id).order_by(Task.due_date,Task.id))
    return [{**record(t), 'field_required': role=='tecnico' or plan is not None or entry is not None}
        for t,role,plan,entry in rows]

def locked_task(s, id):
    task=s.get(Task, id, with_for_update=True, populate_existing=True)
    if task is None:
        raise HTTPException(404,'Actividad no encontrada')
    return task
def editable_project(s,id):
    p=get(s,Project,id)
    if p.status=='finalizado': raise HTTPException(409,'Reabre el proyecto antes de modificar sus actividades')
    return p
@app.post('/projects/{id}/tasks',status_code=201)
def create_task(id:int,data:TaskInput,u:User=Depends(current_user),s:Session=Depends(db)):
    manager(u); p=editable_project(s,id); get(s,User,data.assignee_id)
    if not p.start_date<=data.due_date<=p.end_date: raise HTTPException(422,'Fecha fuera del intervalo del proyecto')
    t=Task(project_id=id,**data.model_dump()); s.add(t); s.flush()
    s.add(TaskHistory(task_id=t.id,user_id=u.id,action='Actividad registrada')); s.commit(); return record(t)
@app.put('/tasks/{id}')
def update_task(id:int,data:TaskInput,u:User=Depends(current_user),s:Session=Depends(db)):
    manager(u); t=locked_task(s,id); p=editable_project(s,t.project_id); get(s,User,data.assignee_id)
    if t.assignee_id!=data.assignee_id and s.get(FieldEntry,id,with_for_update=True) is not None:
        raise HTTPException(409,'El responsable no se puede cambiar después de registrar el inicio')
    if not p.start_date<=data.due_date<=p.end_date: raise HTTPException(422,'Fecha fuera del intervalo del proyecto')
    for k,v in data.model_dump().items(): setattr(t,k,v)
    s.add(TaskHistory(task_id=id,user_id=u.id,action='Datos de actividad actualizados')); s.commit(); return record(t)
@app.patch('/tasks/{id}/status')
def change_status(id:int,data:StatusInput,u:User=Depends(current_user),s:Session=Depends(db)):
    t=locked_task(s,id); editable_project(s,t.project_id)
    if u.role=='tecnico' and t.assignee_id!=u.id: raise HTTPException(403,'Solo puedes actualizar tus actividades')
    if t.status==data.status:
        return record(t)
    if s.get(FieldEntry,id,with_for_update=True) is not None or (
        data.status!='pendiente' and (get(s,User,t.assignee_id).role=='tecnico'
        or s.get(FieldPlan,id,with_for_update=True) is not None)
    ):
        raise HTTPException(409,'Usa Trabajo en campo para registrar el inicio y finalizar con evidencia')
    before=t.status; t.status=data.status
    s.add(TaskHistory(task_id=id,user_id=u.id,action=f'{before} → {t.status}')); s.commit(); return record(t)
@app.get('/tasks/{id}/history')
def history(id:int,u:User=Depends(current_user),s:Session=Depends(db)):
    get(s,Task,id)
    return [record(h) for h in s.scalars(select(TaskHistory).where(TaskHistory.task_id==id).order_by(TaskHistory.id.desc()))]
@app.get('/projects/{id}/reports')
def reports(id:int,u:User=Depends(current_user),s:Session=Depends(db)):
    get(s,Project,id)
    return [record(r) for r in s.scalars(select(Report).where(Report.project_id==id).order_by(Report.id.desc()))]
@app.post('/projects/{id}/reports',status_code=201)
def create_report(id:int,data:ReportInput,u:User=Depends(current_user),s:Session=Depends(db)):
    get(s,Project,id)
    r=Report(project_id=id,author_id=u.id,**data.model_dump()); s.add(r); s.commit(); return record(r)
@app.get('/dashboard')
def dashboard(u:User=Depends(current_user),s:Session=Depends(db)):
    ps=list(s.scalars(select(Project))); ts=list(s.scalars(select(Task)))
    done=sum(t.status=='completada' for t in ts)
    return {'active_projects':sum(p.status=='activo' for p in ps),'finished_projects':sum(p.status=='finalizado' for p in ps),'pending_tasks':len(ts)-done,'completed_tasks':done,'overdue_tasks':sum(t.status!='completada' and t.due_date<date.today() for t in ts),'progress':round(100*done/len(ts)) if ts else 0}

def field_access(task, user, *, write=False):
    if task.assignee_id != user.id and (write or user.role not in ('admin','supervisor')):
        raise HTTPException(403,'Solo el responsable asignado y los supervisores pueden consultar esta evidencia'
            if not write else 'Solo el responsable asignado puede registrar su trabajo')

def utc_iso(value):
    return value.replace(tzinfo=timezone.utc).isoformat() if value else None

def field_details(s, task, entry=None):
    entry=entry or s.get(FieldEntry,task.id)
    plan=s.get(FieldPlan,task.id)
    return {
        'task_id':task.id,
        'status':task.status,
        'checklist':entry.checklist if entry else [
            {'label':label, 'checked':False} for label in (plan.checklist if plan else [])
        ],
        'started_by':entry.started_by if entry else None,
        'started_at':utc_iso(entry.started_at) if entry else None,
        'finished_at':utc_iso(entry.finished_at) if entry else None,
        'latitude':entry.latitude if entry else None,
        'longitude':entry.longitude if entry else None,
        'accuracy_m':entry.accuracy_m if entry else None,
        'has_photo':s.scalar(select(FieldPhoto.task_id).where(FieldPhoto.task_id==task.id)) is not None,
    }

def editable_entry(s, id, u):
    task=locked_task(s,id)
    field_access(task,u,write=True)
    editable_project(s,task.project_id)
    entry=s.get(FieldEntry,id,with_for_update=True)
    if entry is None or entry.finished_at is not None or task.status!='en_proceso':
        raise HTTPException(409,'Registra el inicio de una actividad abierta antes de guardar la evidencia')
    return task,entry

@app.get('/tasks/{id}/field')
def read_field(id:int,response:Response,u:User=Depends(current_user),s:Session=Depends(db)):
    task=get(s,Task,id)
    field_access(task,u)
    response.headers['Cache-Control']='private, no-store'
    return field_details(s,task)

@app.put('/tasks/{id}/field/checklist')
def set_checklist(id:int,data:ChecklistInput,u:User=Depends(current_user),s:Session=Depends(db)):
    manager(u)
    task=locked_task(s,id)
    editable_project(s,task.project_id)
    if task.status=='completada' or s.get(FieldEntry,id,with_for_update=True):
        raise HTTPException(409,'El checklist se puede cambiar antes de registrar el inicio')
    plan=s.get(FieldPlan,id,with_for_update=True)
    if plan is None and data.items:
        s.add(FieldPlan(task_id=id,checklist=data.items))
    elif plan is not None:
        if data.items:
            plan.checklist=data.items
        else:
            s.delete(plan)
    s.add(TaskHistory(task_id=id,user_id=u.id,action='Checklist de campo actualizado'))
    s.commit()
    return field_details(s,task)

@app.post('/tasks/{id}/field/start')
def start_field(id:int,data:WorkStart,u:User=Depends(current_user),s:Session=Depends(db)):
    task=locked_task(s,id)
    field_access(task,u,write=True)
    entry=s.get(FieldEntry,id,with_for_update=True)
    if entry is not None:
        return field_details(s,task,entry)
    editable_project(s,task.project_id)
    if task.status=='completada':
        raise HTTPException(409,'Esta actividad ya está completada')
    plan=s.get(FieldPlan,id,with_for_update=True)
    entry=FieldEntry(task_id=id,started_by=u.id,started_at=now().replace(microsecond=0),**data.model_dump(),
        checklist=[{'label':label, 'checked':False} for label in (plan.checklist if plan else [])])
    s.add(entry)
    task.status='en_proceso'
    s.add(TaskHistory(task_id=id,user_id=u.id,action='Inicio de trabajo en campo registrado'))
    s.commit()
    return field_details(s,task,entry)

@app.put('/tasks/{id}/field/answers')
def answer_checklist(id:int,data:CheckAnswers,u:User=Depends(current_user),s:Session=Depends(db)):
    task,entry=editable_entry(s,id,u)
    if len(data.checked)!=len(entry.checklist):
        raise HTTPException(422,'El número de respuestas debe coincidir con el checklist asignado')
    entry.checklist=[{'label':item['label'],'checked':checked}
        for item,checked in zip(entry.checklist,data.checked)]
    s.commit()
    return field_details(s,task,entry)

@app.put('/tasks/{id}/field/photo',openapi_extra={'requestBody': {
    'required':True,'content':{'image/jpeg':{'schema':{'type':'string','format':'binary'}}}
}})
async def upload_field_photo(id:int,request:Request,u:User=Depends(current_user),s:Session=Depends(db)):
    field_access(get(s,Task,id),u,write=True)
    if request.headers.get('content-type','').split(';',1)[0].lower()!='image/jpeg':
        raise HTTPException(415,'Envía una fotografía JPEG')
    payload=bytearray()
    async for chunk in request.stream():
        if len(payload)+len(chunk)>MAX_PHOTO_BYTES:
            raise HTTPException(413,'La foto no debe superar los 2 MB')
        payload.extend(chunk)
    try:
        jpeg=await run_in_threadpool(normalize_jpeg,bytes(payload))
    except ValueError as exc:
        raise HTTPException(422,str(exc)) from exc
    task,entry=editable_entry(s,id,u)
    photo=s.get(FieldPhoto,id,with_for_update=True)
    if photo is None:
        s.add(FieldPhoto(task_id=id,jpeg=jpeg))
    else:
        photo.jpeg=jpeg
    s.commit()
    return field_details(s,task,entry)

@app.get('/tasks/{id}/field/photo',responses={200:{'content':{'image/jpeg':{}}}},response_class=Response)
def read_field_photo(id:int,u:User=Depends(current_user),s:Session=Depends(db)):
    field_access(get(s,Task,id),u)
    photo=get(s,FieldPhoto,id)
    return Response(photo.jpeg,media_type='image/jpeg',headers={
        'Cache-Control':'private, no-store','X-Content-Type-Options':'nosniff'
    })

@app.post('/tasks/{id}/field/finish')
def finish_field(id:int,u:User=Depends(current_user),s:Session=Depends(db)):
    task=locked_task(s,id)
    field_access(task,u,write=True)
    entry=s.get(FieldEntry,id,with_for_update=True)
    if entry is not None and entry.finished_at is not None:
        return field_details(s,task,entry)
    editable_project(s,task.project_id)
    if entry is None or task.status!='en_proceso':
        raise HTTPException(409,'Primero registra el inicio del trabajo')
    if any(not item['checked'] for item in entry.checklist):
        raise HTTPException(409,'Completa el checklist antes de finalizar')
    if s.scalar(select(FieldPhoto.task_id).where(FieldPhoto.task_id==id).with_for_update()) is None:
        raise HTTPException(409,'Adjunta una foto del trabajo antes de finalizar')
    entry.finished_at=now().replace(microsecond=0)
    task.status='completada'
    s.add(TaskHistory(task_id=id,user_id=u.id,action='Trabajo finalizado con evidencia'))
    s.commit()
    return field_details(s,task,entry)

@app.get('/reminders')
def reminders(u:User=Depends(current_user),s:Session=Depends(db)):
    query=select(Task.id,Task.title,Task.due_date).join(Project).where(
        Task.status!='completada',Project.status=='activo')
    if u.role not in ('admin','supervisor'):
        query=query.where(Task.assignee_id==u.id)
    return [{'id':id,'title':title,'due_date':due} for id,title,due in s.execute(query.order_by(Task.due_date,Task.id))]

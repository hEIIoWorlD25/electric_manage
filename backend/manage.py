import argparse,getpass
from datetime import date,timedelta
from sqlalchemy import select
from app.main import Base,engine,SessionLocal,User,Project,Task,hash_password
p=argparse.ArgumentParser()
p.add_argument('command',choices=['init-db','create-user','demo'])
a=p.parse_args()
if a.command=='init-db':
    Base.metadata.create_all(engine)
    print('Tablas creadas sin borrar datos existentes.')
elif a.command=='create-user':
    name=input('Nombre: ').strip(); email=input('Correo: ').strip().lower()
    role=input('Rol (admin/supervisor/tecnico): ').strip()
    password=getpass.getpass('Contraseña (mínimo 12 caracteres): ')
    if role not in ('admin','supervisor','tecnico') or len(password)<12 or '@' not in email or not name:
        raise SystemExit('Datos inválidos. No se creó usuario.')
    with SessionLocal() as s:
        if s.scalar(select(User).where(User.email==email)): raise SystemExit('El correo ya existe')
        s.add(User(name=name,email=email,role=role,password_hash=hash_password(password))); s.commit()
    print('Usuario creado.')
else:
    with SessionLocal() as s:
        admin=s.scalar(select(User).where(User.role=='admin'))
        if not admin: raise SystemExit('Crea primero un administrador')
        if s.scalar(select(Project)): raise SystemExit('Demo omitida: ya existen proyectos')
        today=date.today()
        project=Project(name='DEMO · Tablero eléctrico',description='Datos ficticios para práctica universitaria',location='Lima · ubicación referencial',manager_id=admin.id,start_date=today-timedelta(days=7),end_date=today+timedelta(days=30),status='activo')
        s.add(project); s.flush()
        for title,status,days in [('Inspección inicial','completada',-3),('Instalación de canaletas','pendiente',-1),('Pruebas del tablero','pendiente',10)]:
            s.add(Task(project_id=project.id,title=title,assignee_id=admin.id,due_date=today+timedelta(days=days),priority='alta',status=status))
        s.commit(); print('Proyecto ficticio creado.')

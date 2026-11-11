import os, tempfile
from io import BytesIO
from pathlib import Path
os.environ['APP_ENV']='test'
os.environ['TEST_DATABASE_URL']='sqlite:///'+(Path(tempfile.mkdtemp(prefix='electricis-tests-'))/'test.db').as_posix()
import pytest
from fastapi.testclient import TestClient
from PIL import Image
from sqlalchemy import inspect
from app.main import app,Base,engine,SessionLocal,User,hash_password
@pytest.fixture()
def setup():
    Base.metadata.drop_all(engine); Base.metadata.create_all(engine)
    with SessionLocal() as s:
        for name,role in [('Admin','admin'),('Tecnico','tecnico'),('Otro','tecnico')]:
            s.add(User(name=name,email=name.lower()+'@test.pe',role=role,password_hash=hash_password('ClaveSegura123!')))
        s.commit()
    c=TestClient(app)
    def auth(email):
        r=c.post('/auth/login',json={'email':email,'password':'ClaveSegura123!'})
        assert r.status_code==200
        return {'Authorization':'Bearer '+r.json()['token']}
    return c,auth('admin@test.pe'),auth('tecnico@test.pe')

def project(c,h):
    data=dict(name='Proyecto prueba',description='Prueba',location='Lima',manager_id=1,start_date='2020-01-01',end_date='2030-12-31',status='activo')
    r=c.post('/projects',headers=h,json=data); assert r.status_code==201
    return r.json()['id'],data

def photo():
    output=BytesIO()
    exif=Image.Exif()
    exif[270]='metadato de prueba'
    Image.new('RGB',(20,30),'teal').save(output,format='JPEG',exif=exif)
    return output.getvalue()

def upload(c,auth,id,data=None,mime='image/jpeg'):
    return c.put(f'/tasks/{id}/field/photo',headers={**auth,'Content-Type':mime},
        content=photo() if data is None else data)

def other_login(c):
    r=c.post('/auth/login',json={'email':'otro@test.pe','password':'ClaveSegura123!'})
    return {'Authorization':'Bearer '+r.json()['token']}

def test_auth_and_logout(setup):
    c,a,t=setup
    assert c.get('/projects').status_code==403
    assert c.post('/auth/login',json={'email':'admin@test.pe','password':'incorrecta'}).status_code==401
    assert c.post('/auth/logout',headers=a).status_code==200
    assert c.get('/projects',headers=a).status_code==401

def test_permissions_progress_history_report(setup):
    c,a,t=setup; pid,p=project(c,a)
    assert c.post('/projects',headers=t,json=p).status_code==403
    body=dict(title='Instalar tablero',assignee_id=2,due_date='2025-01-01',priority='alta')
    r=c.post(f'/projects/{pid}/tasks',headers=a,json=body); assert r.status_code==201
    tid=r.json()['id']
    assert c.patch(f'/tasks/{tid}/status',headers=t,json={'status':'completada'}).status_code==409
    assert c.post(f'/tasks/{tid}/field/start',headers=t,
        json={'latitude':-12.03,'longitude':-77.04,'accuracy_m':12.5}).status_code==200
    assert upload(c,t,tid).status_code==200
    assert c.post(f'/tasks/{tid}/field/finish',headers=t).status_code==200
    assert c.get('/projects',headers=a).json()[0]['progress']==100
    assert len(c.get(f'/tasks/{tid}/history',headers=a).json())==3
    report=dict(work_date='2025-01-01',progress='Tablero instalado',incidents='',observations='')
    assert c.post(f'/projects/{pid}/reports',headers=t,json=report).status_code==201
    assert c.get(f'/projects/{pid}/reports',headers=a).json()[0]['author_id']==2
    assert c.get('/dashboard',headers=a).json()['completed_tasks']==1
    p['status']='finalizado'; assert c.put(f'/projects/{pid}',headers=a,json=p).status_code==200
    assert c.patch(f'/tasks/{tid}/status',headers=t,json={'status':'pendiente'}).status_code==409

def test_validation_and_ownership(setup):
    c,a,t=setup; pid,p=project(c,a)
    p['end_date']='2010-01-01'; assert c.put(f'/projects/{pid}',headers=a,json=p).status_code==422
    body=dict(title='Inspección',assignee_id=3,due_date='2025-01-01',priority='alta')
    tid=c.post(f'/projects/{pid}/tasks',headers=a,json=body).json()['id']
    assert c.patch(f'/tasks/{tid}/status',headers=t,json={'status':'completada'}).status_code==403
    assert c.patch(f'/tasks/{tid}/status',headers=a,json={'status':'inventado'}).status_code==422
    body['assignee_id']=999; assert c.post(f'/projects/{pid}/tasks',headers=a,json=body).status_code==404
    body['assignee_id']=1; body['due_date']='2040-01-01'; assert c.post(f'/projects/{pid}/tasks',headers=a,json=body).status_code==422
    assert c.get('/projects/999/tasks',headers=a).status_code==404

def test_finalization_and_report_validation(setup):
    c,a,t=setup; pid,p=project(c,a)
    p['status']='finalizado'; assert c.put(f'/projects/{pid}',headers=a,json=p).status_code==409
    data=dict(work_date='2099-01-01',progress='Trabajo futuro',incidents='',observations='')
    assert c.post(f'/projects/{pid}/reports',headers=t,json=data).status_code==422
    data['work_date']='2025-01-01'; data['author_id']=1
    assert c.post(f'/projects/{pid}/reports',headers=t,json=data).status_code==422

def test_project_date_change_preserves_task_interval(setup):
    c,a,t=setup; pid,p=project(c,a)
    body=dict(title='Trabajo programado',assignee_id=2,due_date='2025-01-01',priority='media')
    assert c.post(f'/projects/{pid}/tasks',headers=a,json=body).status_code==201
    p['end_date']='2024-12-31'
    assert c.put(f'/projects/{pid}',headers=a,json=p).status_code==409
    assert c.get('/projects',headers=a).json()[0]['end_date']=='2030-12-31'

def test_field_checklist_gps_photo_and_roles(setup):
    c,a,t=setup; pid,_=project(c,a); other=other_login(c)
    body=dict(title='Verificación',assignee_id=2,due_date='2026-11-11',priority='alta')
    tid=c.post(f'/projects/{pid}/tasks',headers=a,json=body).json()['id']
    path=f'/tasks/{tid}/field'
    coordinates={'latitude':-12.045,'longitude':-77.035,'accuracy_m':7.5}
    assert c.get(f'/projects/{pid}/tasks',headers=a).json()[0]['field_required']
    assert c.get(path,headers=other).status_code==403
    assert c.put(path+'/checklist',headers=t,json={'items':['Verificar']}).status_code==403
    assert c.put(path+'/checklist',headers=a,json={'items':['Revisar',' revisar ']}).status_code==422
    assert c.put(path+'/checklist',headers=a,json={'items':['Revisar tablero','Medir tensión']}).status_code==200
    assert c.post(path+'/start',headers=other,json=coordinates).status_code==403
    assert c.post(path+'/start',headers=a,json=coordinates).status_code==403
    assert c.post(path+'/start',headers=t,json={**coordinates,'latitude':91}).status_code==422
    assert c.post(path+'/finish',headers=t).status_code==409
    started=c.post(path+'/start',headers=t,json=coordinates)
    assert started.status_code==200
    assert started.json()['started_at'].endswith('+00:00') and started.json()['started_by']==2
    assert started.json()['status']=='en_proceso'
    assert c.post(path+'/start',headers=t,json={**coordinates,'latitude':0}).json()==started.json()
    assert c.put(path+'/checklist',headers=a,json={'items':['Editar']}).status_code==409
    assert c.put(f'/tasks/{tid}',headers=a,json={**body,'assignee_id':3}).status_code==409
    assert c.put(path+'/answers',headers=t,json={'checked':[True]}).status_code==422
    assert c.post(path+'/finish',headers=t).status_code==409
    assert c.put(path+'/answers',headers=t,json={'checked':[True,True]}).status_code==200
    assert c.post(path+'/finish',headers=t).status_code==409
    assert upload(c,other,tid).status_code==403
    assert upload(c,t,tid,b'not-a-photo').status_code==422
    assert upload(c,t,tid,photo(),'text/plain').status_code==415
    assert upload(c,t,tid,b'x'*(2*1024*1024+1)).status_code==413
    assert upload(c,t,tid).status_code==200
    result=c.get(path+'/photo',headers=a)
    assert result.headers['content-type']=='image/jpeg' and 'no-store' in result.headers['cache-control']
    assert not Image.open(BytesIO(result.content)).getexif()
    assert c.get(path+'/photo',headers=other).status_code==403
    assert c.get(path,headers=a).json()['latitude']==coordinates['latitude']
    finished=c.post(path+'/finish',headers=t)
    assert finished.status_code==200 and finished.json()['status']=='completada'
    assert c.post(path+'/finish',headers=t).json()==finished.json()
    assert upload(c,t,tid).status_code==409
    assert c.patch(f'/tasks/{tid}/status',headers=a,json={'status':'pendiente'}).status_code==409
    assert c.get('/reminders',headers=t).json()==[]

def test_reminders_only_active_and_visible_assignments(setup):
    c,a,t=setup; pid,p=project(c,a)
    paused=c.post('/projects',headers=a,json={**p,'name':'Proyecto pausado','status':'pausado'}).json()['id']
    body=dict(title='Actividad',assignee_id=2,due_date='2026-11-11',priority='media')
    def add(project_id,**changes):
        return c.post(f'/projects/{project_id}/tasks',headers=a,json={**body,**changes}).json()['id']
    assigned=add(pid)
    others=add(pid,assignee_id=3)
    done=add(pid,assignee_id=1)
    add(paused)
    assert c.patch(f'/tasks/{done}/status',headers=a,json={'status':'completada'}).status_code==200
    assert [x['id'] for x in c.get('/reminders',headers=t).json()]==[assigned]
    assert [x['id'] for x in c.get('/reminders',headers=a).json()]==[assigned,others]

def test_create_all_adds_field_tables_preserving_existing_data(setup):
    c,a,_=setup; pid,_=project(c,a)
    for name in ('task_field_photos','task_field_entries','task_field_plans'):
        Base.metadata.tables[name].drop(engine)
    Base.metadata.create_all(engine)
    Base.metadata.create_all(engine)
    assert {'task_field_plans','task_field_entries','task_field_photos'} <= set(inspect(engine).get_table_names())
    assert c.get('/projects',headers=a).json()[0]['id']==pid

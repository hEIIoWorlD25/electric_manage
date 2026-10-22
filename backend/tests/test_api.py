import os, tempfile
from pathlib import Path
os.environ['APP_ENV']='test'
os.environ['TEST_DATABASE_URL']='sqlite:///'+(Path(tempfile.mkdtemp(prefix='electricis-tests-'))/'test.db').as_posix()
import pytest
from fastapi.testclient import TestClient
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
    assert c.patch(f'/tasks/{tid}/status',headers=t,json={'status':'completada'}).status_code==200
    assert c.get('/projects',headers=a).json()[0]['progress']==100
    assert len(c.get(f'/tasks/{tid}/history',headers=a).json())==2
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

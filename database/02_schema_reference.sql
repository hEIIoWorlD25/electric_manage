-- Referencia generada desde los modelos de la API. Alternativa a manage.py init-db.
USE electricis_manager;


CREATE TABLE IF NOT EXISTS users (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	name VARCHAR(100) NOT NULL, 
	email VARCHAR(150) NOT NULL, 
	password_hash VARCHAR(256) NOT NULL, 
	`role` VARCHAR(20) NOT NULL, 
	PRIMARY KEY (id), 
	UNIQUE (email)
)

;

CREATE TABLE IF NOT EXISTS projects (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	name VARCHAR(150) NOT NULL, 
	description TEXT NOT NULL, 
	location VARCHAR(200) NOT NULL, 
	manager_id INTEGER NOT NULL, 
	start_date DATE NOT NULL, 
	end_date DATE NOT NULL, 
	status VARCHAR(20) NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(manager_id) REFERENCES users (id)
)

;

CREATE TABLE IF NOT EXISTS sessions (
	token_hash VARCHAR(64) NOT NULL, 
	user_id INTEGER NOT NULL, 
	expires_at DATETIME NOT NULL, 
	PRIMARY KEY (token_hash), 
	FOREIGN KEY(user_id) REFERENCES users (id)
)

;

CREATE TABLE IF NOT EXISTS reports (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	project_id INTEGER NOT NULL, 
	author_id INTEGER NOT NULL, 
	work_date DATE NOT NULL, 
	progress TEXT NOT NULL, 
	incidents TEXT NOT NULL, 
	observations TEXT NOT NULL, 
	created_at DATETIME NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(project_id) REFERENCES projects (id), 
	FOREIGN KEY(author_id) REFERENCES users (id)
)

;
CREATE INDEX ix_reports_project_id ON reports (project_id);

CREATE TABLE IF NOT EXISTS tasks (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	project_id INTEGER NOT NULL, 
	title VARCHAR(150) NOT NULL, 
	assignee_id INTEGER NOT NULL, 
	due_date DATE NOT NULL, 
	priority VARCHAR(15) NOT NULL, 
	status VARCHAR(20) NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(project_id) REFERENCES projects (id), 
	FOREIGN KEY(assignee_id) REFERENCES users (id)
)

;
CREATE INDEX ix_tasks_project_id ON tasks (project_id);

CREATE TABLE IF NOT EXISTS task_history (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	task_id INTEGER NOT NULL, 
	user_id INTEGER NOT NULL, 
	action VARCHAR(255) NOT NULL, 
	created_at DATETIME NOT NULL, 
	PRIMARY KEY (id), 
	FOREIGN KEY(task_id) REFERENCES tasks (id), 
	FOREIGN KEY(user_id) REFERENCES users (id)
)

;
CREATE INDEX ix_task_history_task_id ON task_history (task_id);

@echo off
cd /d "%~dp0"
py -3.12 -m venv .venv
if errorlevel 1 goto fail
.venv\Scripts\python.exe -m pip install -r requirements.txt
if errorlevel 1 goto fail
if not exist .env copy .env.example .env >nul
echo.
echo Dependencias listas. Edita .env antes de inicializar MySQL.
pause
exit /b 0
:fail
echo Error. Revisa la conexion o instala Python 3.12 con Python Launcher.
pause
exit /b 1

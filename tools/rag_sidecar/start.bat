@echo off
cd /d "%~dp0"
set PYTHON=C:\Users\DELL\AppData\Local\Programs\Python\Python310\python.exe
echo Starting RAG Sidecar on port 5001...
"%PYTHON%" -m uvicorn main:app --host 0.0.0.0 --port 5001 --reload
pause

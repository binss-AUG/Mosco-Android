@echo off
:: Su dung trang ma 65001 cho UTF-8
chcp 65001 > nul
title Mosco Launcher
setlocal

:menu
cls
echo ==========================================
echo    MOSCO PROJECT - MASTER LAUNCHER
echo ==========================================
echo.
echo [1] Run Android Client
echo [2] Run Backend Server
echo [3] Run RAG Sidecar (AI)
echo [4] Run All (Server + Sidecar)
echo [5] Exit
echo.
echo ==========================================
:: Su dung ky tu thuong de tranh loi font
set /p choice="Chon (1-5): "

if "%choice%"=="1" goto run_client
if "%choice%"=="2" goto run_server
if "%choice%"=="3" goto run_rag
if "%choice%"=="4" goto run_all
if "%choice%"=="5" exit
goto menu

:run_client
echo Dang mo Android Client...
start "Mosco_Client" cmd /c "cd /d %~dp0\..\client && build_and_run_app.bat"
timeout /t 2 > nul
goto menu

:run_server
echo Dang mo Backend Server...
start "Mosco_Server" cmd /c "cd /d %~dp0\..\server && gradlew bootRun"
timeout /t 2 > nul
goto menu

:run_rag
echo Dang mo RAG Sidecar (port 5001)...
start "Mosco_RAG" cmd /c "cd /d %~dp0\..\tools\rag_sidecar && start.bat"
timeout /t 2 > nul
goto menu

:run_all
echo Dang khoi dong tat ca...
start "Mosco_Server" cmd /c "cd /d %~dp0\..\server && gradlew bootRun"
start "Mosco_RAG" cmd /c "cd /d %~dp0\..\tools\rag_sidecar && start.bat"
timeout /t 2 > nul
goto menu

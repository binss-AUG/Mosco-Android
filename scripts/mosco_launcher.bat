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
echo [3] Exit
echo.
echo ==========================================
:: Su dung ky tu thuong de tranh loi font
set /p choice="Chon (1-3): "

if "%choice%"=="1" goto run_client
if "%choice%"=="2" goto run_server
if "%choice%"=="3" exit
goto menu

:run_client
echo Dang mo Android Client...
start "Mosco_Client" cmd /c "cd /d %~dp0\..\client && build_and_run_app.bat"
timeout /t 2 > nul
goto menu

:run_server
echo Dang mo Backend Server...
start "Mosco_Server" cmd /c "cd /d %~dp0\..\server && start_server.bat"
timeout /t 2 > nul
goto menu

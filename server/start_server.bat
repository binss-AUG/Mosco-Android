@echo off
chcp 65001 > nul
title Mosco Server
setlocal
cd /d "%~dp0"
echo Dang khoi dong Server...
call .\gradlew bootRun
if %ERRORLEVEL% neq 0 pause
endlocal

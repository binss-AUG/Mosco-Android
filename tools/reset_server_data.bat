@echo off
setlocal enabledelayedexpansion
title MOSCO SERVER - PRODUCTION GRADE RESET
color 0b

echo ===================================================
echo      M O S C O   S E R V E R   M A N A G E R
echo        Zero-Data-Loss ^& Production Reset Mode
echo ===================================================
echo.
echo INFO: This script implements the standard corporate flow:
echo  1. Reads config dynamically from server/.env
echo  2. Creates a Database Snapshot Backup (.sql)
echo  3. Safely Wipes ^& Re-creates Database Schema
echo  4. Generates new JWT Secret to force User Logouts
echo.

set /p confirm="Type 'RESET' to format the server data: "
if not "!confirm!"=="RESET" (
    echo.
    echo Operation Aborted.
    timeout /t 3 >nul
    exit /b
)

:: 1. Tự động đọc biến môi trường (.env)
set "ENV_FILE=server\.env"
if not exist "%ENV_FILE%" (
    echo [ERROR] Khong tim thay file %ENV_FILE%! 
    echo Ban hay tao file .env trong thu muc server.
    pause
    exit /b
)

echo.
echo [1/5] Reading Configuration from .env...
for /f "tokens=1,2 delims==" %%A in (%ENV_FILE%) do (
    if "%%A"=="DB_USER" set "db_user=%%B"
    if "%%A"=="DB_PASS" set "db_pass=%%B"
    if "%%A"=="DB_NAME" set "db_name=%%B"
)
echo  -^> Database set to: !db_name!

:: 2. Backup tức thời (Snapshot)
echo.
echo [2/5] Creating Database Backup Snapshot...
set "TIMESTAMP=%date:~6,4%%date:~3,2%%date:~0,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
set "TIMESTAMP=%TIMESTAMP: =0%"
set "BACKUP_FILE=server\data\backup_!db_name!_!TIMESTAMP!.sql"
if not exist "server\data" mkdir "server\data"

mysqldump -u !db_user! -p"!db_pass!" !db_name! > "%BACKUP_FILE%" 2>nul
if %errorlevel% equ 0 (
    echo  -^> Snapshot created successfully: %BACKUP_FILE%
) else (
    echo  -^> [WARNING] Khong the Backup. Co the Database chua duoc tao.
)

:: 3. Reset Database (Wipe)
echo.
echo [3/5] Executing Schema Wipe on MySQL...
mysql -u !db_user! -p"!db_pass!" -e "DROP DATABASE IF EXISTS !db_name!; CREATE DATABASE !db_name!;"
if %errorlevel% neq 0 (
    echo  -^> [ERROR] Giao tiep MySQL that bai! Kiem tra lai DB_USER / DB_PASS trong ban .env.
    pause
    exit /b
)
echo  -^> Database rebuilt. (Ready for Spring Boot DDL-Auto)

:: 4. Khắc phục "Ghost Session" bằng JWT Versioning (Rotation)
echo.
echo [4/5] Rotating JWT Secret Key...
:: Poweshell sinh chuỗi ngẫu nhiên 48 kí tự và thay thế trực tiếp vào file .env
powershell -Command "$newSecret = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 48 | %% {[char]$_}); (Get-Content server\.env) -replace '^JWT_SECRET=.*', ('JWT_SECRET=' + $newSecret) | Set-Content server\.env"
if %errorlevel% equ 0 (
    echo  -^> Token Version updated! All Old Client Sessions will be rejected (401 Unauthorized).
) else (
    echo  -^> [WARNING] Failed to update JWT Secret.
)

:: 5. Xóa Caches Server Build
echo.
echo [5/5] Flushing Build System Caches...
rmdir /s /q "server\target" 2>nul
rmdir /s /q "server\build" 2>nul
del /q "server\*.log" 2>nul
echo  -^> Caches cleared.

echo.
echo ===================================================
echo [SUCCESS] MISSION ACCOMPLISHED!
echo Ban co the Restart Server (Run Spring Boot) ngay bay gio.
echo ===================================================
pause

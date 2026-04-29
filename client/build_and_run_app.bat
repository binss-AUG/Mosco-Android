@echo off
chcp 65001 > nul
title Mosco Client
setlocal
cd /d "%~dp0"
echo Dang build va cai dat App...
call .\gradlew installDebug
if %ERRORLEVEL% eq 0 (
    echo Thanh cong! Dang mo App...
    adb shell am start -n com.vn.jet.mosco/.SplashActivity
) else (
    echo Loi build!
    pause
)
endlocal

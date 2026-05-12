@echo off
:: File hỗ trợ chạy nhanh kịch bản PowerShell với quyền Administrator
:: Người dùng chỉ cần click đúp vào file này.

echo Dang khoi chay kich ban tu dong cai dat Mosco...
powershell -NoExit -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup_mosco.ps1"

:: Giữ cửa sổ dòng lệnh ở lại để đọc thông báo lỗi nếu có
pause

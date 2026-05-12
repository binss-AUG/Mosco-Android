@echo off
:: File hỗ trợ chạy nhanh kịch bản PowerShell với quyền Administrator
:: Người dùng chỉ cần click đúp vào file này.

echo Đang khởi chạy kịch bản tự động cài đặt Mosco...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup_mosco.ps1"

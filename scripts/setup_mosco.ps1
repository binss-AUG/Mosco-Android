# Script tự động cài đặt OpenJDK 21, MySQL Server, cập nhật IP Client và khởi chạy Backend
# Yêu cầu: Đặt script này trong thư mục 'scripts' của dự án

param([switch]$Elevated)

# Tự động xin quyền Administrator nếu chưa có
if (-not $Elevated -and -not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList ("-noprofile -file `"{0}`" -Elevated" -f $PSCommandPath)
    exit
}

# Lấy thư mục hiện tại của script làm chuẩn (thư mục 'scripts')
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location -Path $ScriptDir

Write-Host "=======================================================" -ForegroundColor Yellow
Write-Host "      HỆ THỐNG TỰ ĐỘNG CÀI ĐẶT & KHỞI CHẠY MOSCO       " -ForegroundColor Yellow
Write-Host "=======================================================" -ForegroundColor Yellow

# 1. Kiểm tra và cài đặt Java (OpenJDK 21)
Write-Host "`n[1/5] Đang kiểm tra môi trường Java (OpenJDK 21)..." -ForegroundColor Cyan
$java = Get-Command "java" -ErrorAction SilentlyContinue
if (-not $java) {
    Write-Host "-> Chưa có Java. Bắt đầu tải và cài đặt tự động..." -ForegroundColor White
    winget install Microsoft.OpenJDK.21 --silent --accept-package-agreements --accept-source-agreements
    $env:Path += ";C:\Program Files\Microsoft\jdk-21.0.x\bin"
} else {
    Write-Host "-> Java đã được cài đặt sẵn." -ForegroundColor Green
}

# 2. Kiểm tra và cài đặt MySQL Server
Write-Host "`n[2/5] Đang kiểm tra MySQL Server..." -ForegroundColor Cyan
$mysqlService = Get-Service "MySQL*" -ErrorAction SilentlyContinue
if (-not $mysqlService) {
    Write-Host "-> Chưa có MySQL. Bắt đầu tải và cài đặt tự động (chế độ ngầm)..." -ForegroundColor White
    winget install Oracle.MySQL --silent --accept-package-agreements --accept-source-agreements
    Write-Host "-> Đang chờ dịch vụ MySQL khởi tạo..." -ForegroundColor Yellow
    Start-Sleep -Seconds 15
} else {
    Write-Host "-> MySQL Server đã tồn tại." -ForegroundColor Green
}

# 3. Khởi động MySQL và Nạp dữ liệu
Write-Host "`n[3/5] Khởi động MySQL và chuẩn bị cơ sở dữ liệu..." -ForegroundColor Cyan
Start-Service "MySQL*" -ErrorAction SilentlyContinue

$mysqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if (-not (Test-Path $mysqlBin)) {
    $mysqlBin = Get-ChildItem -Path "C:\Program Files\MySQL" -Recurse -Filter "mysql.exe" | Select-Object -First 1 -ExpandProperty FullName
}

if ($mysqlBin -and (Test-Path $mysqlBin)) {
    Write-Host "-> Đang tạo Database 'mosco_db' (nếu chưa có)..." -ForegroundColor White
    & $mysqlBin -u root -e "CREATE DATABASE IF NOT EXISTS mosco_db;"
    
    $dumpFile = Join-Path $ScriptDir "dump.sql"
    if (Test-Path $dumpFile) {
        Write-Host "-> Tìm thấy file dump.sql. Đang nạp dữ liệu thẻ bài vào hệ thống..." -ForegroundColor Yellow
        cmd.exe /c "`"$mysqlBin`" -u root mosco_db < `"$dumpFile`""
        Write-Host "-> Nạp dữ liệu thành công!" -ForegroundColor Green
    } else {
        Write-Host "-> Chú ý: Không tìm thấy file dump.sql tại thư mục hiện tại." -ForegroundColor Magenta
    }
} else {
    Write-Host "-> Không tìm thấy file chạy mysql.exe. Vui lòng kiểm tra lại quá trình cài đặt MySQL." -ForegroundColor Red
}

# 4. Tự động phát hiện IPv4 LAN và Cập nhật file AppConfig.java của Client
Write-Host "`n[4/5] Tự động cấu hình địa chỉ IP kết nối cho Android Client..." -ForegroundColor Cyan

# Ưu tiên lấy IP thực tế cấp qua DHCP (tránh các card mạng ảo của VMware/VirtualBox)
$ipObj = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { 
    $_.InterfaceAlias -notlike "*Loopback*" -and 
    $_.InterfaceAlias -notlike "*vEthernet*" -and 
    $_.IPAddress -notlike "169.254.*" -and 
    $_.PrefixOrigin -eq "Dhcp" 
} | Select-Object -First 1

# Nếu không có IP DHCP, lấy tạm IP nội bộ dải 192.168.* hoặc 10.*
if (-not $ipObj) {
    $ipObj = Get-NetIPAddress -AddressFamily IPv4 | Where-Object { 
        $_.IPAddress -like "192.168.*" -or $_.IPAddress -like "10.*" 
    } | Select-Object -First 1
}

if ($ipObj) {
    $currentIP = $ipObj.IPAddress
    Write-Host "-> Phát hiện địa chỉ IPv4 LAN của máy: $currentIP" -ForegroundColor Green
    
    # Đường dẫn file AppConfig.java (từ thư mục scripts đi lùi lại 1 cấp)
    $appConfigPath = Join-Path $ScriptDir "..\client\app\src\main\java\com\vn\jet\mosco\utils\AppConfig.java"
    $appConfigPath = [System.IO.Path]::GetFullPath($appConfigPath)
    
    if (Test-Path $appConfigPath) {
        $content = Get-Content -Path $appConfigPath -Raw
        # Thay thế IP trong chuỗi BASE_URL bằng Regex
        $newContent = $content -replace '(public static final String BASE_URL\s*=\s*"http://)[^:]+(:8080/";)', ("`$1" + $currentIP + "`$2")
        
        Set-Content -Path $appConfigPath -Value $newContent -Encoding UTF8
        Write-Host "-> Đã cập nhật tự động BASE_URL trong AppConfig.java thành công!" -ForegroundColor Green
    } else {
        Write-Host "-> Không tìm thấy file AppConfig.java tại: $appConfigPath" -ForegroundColor Red
    }
} else {
    Write-Host "-> Không thể tự động phát hiện địa chỉ IPv4 LAN. Vui lòng tự cấu hình file AppConfig.java." -ForegroundColor Yellow
}

# 5. Khởi chạy Backend Spring Boot
Write-Host "`n[5/5] Khởi chạy Server Spring Boot..." -ForegroundColor Cyan
$jarFile = Join-Path $ScriptDir "mosco-backend.jar"

if (Test-Path $jarFile) {
    Write-Host "-> Đang chạy ứng dụng Mosco Backend..." -ForegroundColor Green
    Write-Host "=======================================================" -ForegroundColor Yellow
    java -jar "$jarFile"
} else {
    Write-Host "-> Lỗi: Không tìm thấy file thực thi '$jarFile'." -ForegroundColor Red
    Write-Host "-> Vui lòng copy file .jar đã build vào cùng thư mục với script này!" -ForegroundColor Red
    Pause
}

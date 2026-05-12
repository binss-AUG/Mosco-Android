# Script tu dong cap nhat IP Client va khoi chay Backend thong qua Docker Compose
# Yeu cau: Dat script nay trong thu muc 'scripts' cua du an

param([switch]$Elevated)

# Tu dong xin quyen Administrator voi co -NoExit de giu cua so doc nhat ky
if (-not $Elevated -and -not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList ("-NoExit -NoProfile -ExecutionPolicy Bypass -File `"{0}`" -Elevated" -f $PSCommandPath)
    exit
}

$ScriptDir = $PSScriptRoot
Set-Location -Path $ScriptDir

Write-Host "=======================================================" -ForegroundColor Yellow
Write-Host "      HE THONG KHOI CHAY MOSCO DOCKERIZATION           " -ForegroundColor Yellow
Write-Host "=======================================================" -ForegroundColor Yellow

# 1. Tu dong phat hien IPv4 LAN va Cap nhat file AppConfig.java cua Client
Write-Host "`n[1/2] Tu dong cau hinh dia chi IP ket noi cho Android Client..." -ForegroundColor Cyan

$ipObj = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { 
    $_.InterfaceAlias -notlike "*Loopback*" -and 
    $_.InterfaceAlias -notlike "*vEthernet*" -and 
    $_.IPAddress -notlike "169.254.*" -and 
    $_.PrefixOrigin -eq "Dhcp" 
} | Select-Object -First 1

if (-not $ipObj) {
    $ipObj = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { 
        $_.IPAddress -like "192.168.*" -or $_.IPAddress -like "10.*" 
    } | Select-Object -First 1
}

if ($ipObj) {
    $currentIP = $ipObj.IPAddress
    Write-Host "-> Phat hien dia chi IPv4 LAN cua may: $currentIP" -ForegroundColor Green
    
    $appConfigPath = Join-Path $ScriptDir "..\client\app\src\main\java\com\vn\jet\mosco\utils\AppConfig.java"
    $appConfigPath = [System.IO.Path]::GetFullPath($appConfigPath)
    
    if (Test-Path $appConfigPath) {
        $content = Get-Content -Path $appConfigPath -Raw
        $newContent = $content -replace 'public static final String BASE_URL\s*=\s*"http://[^:]+:8080/";', ("public static final String BASE_URL = `"http://" + $currentIP + ":8080/`";")
        Set-Content -Path $appConfigPath -Value $newContent -Encoding UTF8
        Write-Host "-> Da cap nhat tu dong BASE_URL trong AppConfig.java thanh cong!" -ForegroundColor Green
    } else {
        Write-Host "-> Khong tim thay file AppConfig.java tai: $appConfigPath" -ForegroundColor Red
    }
} else {
    Write-Host "-> Khong the tu dong phat hien dia chi IPv4 LAN." -ForegroundColor Yellow
}

# 2. Khoi chay he sinh thai Docker Compose
Write-Host "`n[2/2] Khoi chay he sinh thai Backend (Docker Compose)..." -ForegroundColor Cyan

# Kiem tra cong cu Docker co san sang chua
$dockerCmd = Get-Command "docker" -ErrorAction SilentlyContinue
if (-not $dockerCmd) {
    Write-Host "-> [!] He thong phat hien may nay chua cai dat Docker Desktop." -ForegroundColor Yellow
    Write-Host "-> Dang tu dong mo trinh duyet de tai bo cai Docker Desktop Installer..." -ForegroundColor Green
    Start-Process "https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe"
    Write-Host "-> Vui long hoan tat cai dat Docker Desktop roi chay lai script nay!" -ForegroundColor White
    Pause
    exit
}

# Chuyen ve thu muc goc chua tệp docker-compose.yml
$RootDir = [System.IO.Path]::GetFullPath((Join-Path $ScriptDir "..\"))
Set-Location -Path $RootDir

Write-Host "-> Bat dau tai hinh anh co so va dung hinh container (Co the mat vai phut lan dau)..." -ForegroundColor White
docker compose up --build -d

Write-Host "`n=======================================================" -ForegroundColor Green
Write-Host "-> DOCKERIZATION HOAN TAT THANH CONG!" -ForegroundColor Green
Write-Host "-> Backend Spring Boot dang hoat dong tai: http://localhost:8080/" -ForegroundColor Green
Write-Host "-> Co so du lieu MySQL dang chay o cong: 3307 (Noi bo: 3306)" -ForegroundColor Green
Write-Host "=======================================================" -ForegroundColor Green
Pause

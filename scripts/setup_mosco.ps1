# Script tu dong cai dat OpenJDK 21, MySQL Server, cap nhat IP Client va khoi chay Backend
# Yeu cau: Dat script nay trong thu muc 'scripts' cua du an

param([switch]$Elevated)

# Tu dong xin quyen Administrator
if (-not $Elevated -and -not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList ("-NoProfile -ExecutionPolicy Bypass -File `"{0}`" -Elevated" -f $PSCommandPath)
    exit
}

$ScriptDir = $PSScriptRoot
Set-Location -Path $ScriptDir

Write-Host "=======================================================" -ForegroundColor Yellow
Write-Host "      HE THONG TU DONG CAI DAT & KHOI CHAY MOSCO       " -ForegroundColor Yellow
Write-Host "=======================================================" -ForegroundColor Yellow

# 1. Kiem tra phien ban Java va cai dat OpenJDK 21 neu can
Write-Host "`n[1/5] Dang kiem tra moi truong Java..." -ForegroundColor Cyan
$javaCmd = Get-Command "java" -ErrorAction SilentlyContinue
$javaVersionOk = $false
$targetJavaBin = "java" # Mac dinh dung lenh java cua he thong neu dat chuan

if ($javaCmd) {
    # Kiem tra xem Java hien tai la version bao nhieu (tranh dung nham Java 8)
    $verOutput = & java -version 2>&1 | Out-String
    if ($verOutput -match 'version "(\d+)\.') {
        $major = [int]$Matches[1]
        if ($major -eq 1) { $major = 8 } # Java 1.8 = 8
        
        Write-Host "-> Phat hien Java hien tai cua may la thuong vu: Java $major" -ForegroundColor White
        if ($major -ge 17) {
            $javaVersionOk = $true
            Write-Host "-> Phien ban Java dat chuan (>= 17)." -ForegroundColor Green
        } else {
            Write-Host "-> Canh bao: Java $major qua cu (chi ho tro class file 52.0). He thong yeu cau Java 17/21." -ForegroundColor Yellow
        }
    }
}

# Neu Java chua co hoac phien ban qua cu (< 17), tien hanh cai dat JDK 21
if (-not $javaVersionOk) {
    Write-Host "-> Bat dau tai va cai dat Microsoft OpenJDK 21..." -ForegroundColor White
    
    # Kiem tra winget
    $winget = Get-Command "winget" -ErrorAction SilentlyContinue
    if ($winget) {
        winget install Microsoft.OpenJDK.21 --silent --accept-package-agreements --accept-source-agreements
    } else {
        Write-Host "-> Khong tim thay 'winget'. Chuyen sang tai truc tiep file MSI tu Microsoft..." -ForegroundColor Yellow
        $msiPath = Join-Path $ScriptDir "jdk21.msi"
        try {
            Invoke-WebRequest -Uri "https://aka.ms/download-jdk/microsoft-jdk-21-windows-x64.msi" -OutFile $msiPath -UseBasicParsing
            Write-Host "-> Tai file thanh cong. Dang cai dat ngam..." -ForegroundColor White
            Start-Process msiexec.exe -ArgumentList "/i `"$msiPath`" /qn" -Wait
            # Xoa file msi sau khi cai xong
            Remove-Item $msiPath -Force -ErrorAction SilentlyContinue
        } catch {
            Write-Host "-> Loi tai JDK truc tiep: $_" -ForegroundColor Red
        }
    }
    
    # Tim duong dan tuyet doi cua OpenJDK 21 vua cai dat de dam bao dung dung ban moi
    Start-Sleep -Seconds 5
    $jdk21Bin = Get-ChildItem -Path "C:\Program Files\Microsoft" -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk21Bin) {
        $targetJavaBin = $jdk21Bin.FullName
        Write-Host "-> Cai dat thanh cong! Su dung Java 21 tai: $targetJavaBin" -ForegroundColor Green
    } else {
        Write-Host "-> Khong tim thay file java.exe cua JDK 21 sau khi cai. Thu dung lenh mac dinh." -ForegroundColor Yellow
    }
}

# 2. Kiem tra va cai dat MySQL Server
Write-Host "`n[2/5] Dang kiem tra MySQL Server..." -ForegroundColor Cyan
$mysqlService = Get-Service "MySQL*" -ErrorAction SilentlyContinue
if (-not $mysqlService) {
    Write-Host "-> Chua co MySQL. Bat dau tai va cai dat tu dong (che do ngam)..." -ForegroundColor White
    $winget = Get-Command "winget" -ErrorAction SilentlyContinue
    if ($winget) {
        winget install Oracle.MySQL --silent --accept-package-agreements --accept-source-agreements
        Write-Host "-> Dang cho dich vu MySQL khoi tao..." -ForegroundColor Yellow
        Start-Sleep -Seconds 15
    } else {
        Write-Host "-> May khong ho tro 'winget'. Vui long tu cai dat MySQL Server 8.0 bang tay." -ForegroundColor Red
    }
} else {
    Write-Host "-> MySQL Server da ton tai." -ForegroundColor Green
}

# 3. Khoi dong MySQL va Nap du lieu
Write-Host "`n[3/5] Khoi dong MySQL va chuan bi co so du lieu..." -ForegroundColor Cyan
Start-Service "MySQL*" -ErrorAction SilentlyContinue

$mysqlBin = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if (-not (Test-Path $mysqlBin)) {
    $found = Get-ChildItem -Path "C:\Program Files\MySQL" -Recurse -Filter "mysql.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { $mysqlBin = $found.FullName }
}

if (Test-Path $mysqlBin) {
    Write-Host "-> Dang ket noi MySQL de tao Database 'mosco_db'..." -ForegroundColor White
    $argsCreate = @("-u", "root", "-e", "CREATE DATABASE IF NOT EXISTS mosco_db;")
    $err = ""
    & $mysqlBin $argsCreate 2>&1 | Out-String -OutVariable err
    
    if ($err -like "*Access denied*") {
        Write-Host "-> Loi: Tai khoan root cua MySQL tren may nay co dat mat khau!" -ForegroundColor Red
        Write-Host "-> Vui long tu import file dump.sql vao database 'mosco_db' bang cong cu cua ban." -ForegroundColor Yellow
    } else {
        if (Test-Path ".\dump.sql") {
            Write-Host "-> Tim thay file dump.sql. Dang nap du lieu the bai vao he thong..." -ForegroundColor Yellow
            $cmdString = "`"" + $mysqlBin + "`" -u root mosco_db < dump.sql"
            cmd.exe /c $cmdString
            Write-Host "-> Nap du lieu hoan tat!" -ForegroundColor Green
        } else {
            Write-Host "-> Chu y: Khong tim thay file dump.sql tai thu muc hien tai." -ForegroundColor Magenta
        }
    }
} else {
    Write-Host "-> Khong tim thay file chay mysql.exe. Bo qua buoc nap tu dong." -ForegroundColor Yellow
}

# 4. Tu dong phat hien IPv4 LAN va Cap nhat file AppConfig.java cua Client
Write-Host "`n[4/5] Tu dong cau hinh dia chi IP ket noi cho Android Client..." -ForegroundColor Cyan

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
        $newContent = $content -replace '(public static final String BASE_URL\s*=\s*"http://)[^:]+(:8080/";)', ("`$1" + $currentIP + "`$2")
        Set-Content -Path $appConfigPath -Value $newContent -Encoding UTF8
        Write-Host "-> Da cap nhat tu dong BASE_URL trong AppConfig.java thanh cong!" -ForegroundColor Green
    } else {
        Write-Host "-> Khong tim thay file AppConfig.java tai: $appConfigPath" -ForegroundColor Red
    }
} else {
    Write-Host "-> Khong the tu dong phat hien dia chi IPv4 LAN." -ForegroundColor Yellow
}

# 5. Khoi chay Backend Spring Boot
Write-Host "`n[5/5] Khoi chay Server Spring Boot..." -ForegroundColor Cyan
$jarFile = Join-Path $ScriptDir "mosco-backend.jar"

if (Test-Path $jarFile) {
    Write-Host "-> Dang chay ung dung Mosco Backend bang Java: $targetJavaBin" -ForegroundColor Green
    Write-Host "=======================================================" -ForegroundColor Yellow
    # Su dung dung duong dan file java.exe tuyet doi de tranh goi nham Java 8
    & $targetJavaBin "-jar" "$jarFile"
    Pause
} else {
    Write-Host "-> Loi: Khong tim thay file thuc thi '$jarFile'." -ForegroundColor Red
    Write-Host "-> Vui long copy file .jar da build vao cung thu muc voi script nay!" -ForegroundColor Red
    Pause
}

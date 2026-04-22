# PowerShell script to remove BOM (Byte Order Mark) from Java files
Write-Host "Removing BOM from Java files..." -ForegroundColor Cyan

# Find all Java files recursively
$javaFiles = Get-ChildItem -Path "src\main\java" -Recurse -Filter "*.java"

$count = 0
foreach ($file in $javaFiles) {
    # Read file content with UTF8 (which removes BOM)
    $content = [System.IO.File]::ReadAllText($file.FullName)
    
    # Save file without BOM
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
    
    Write-Host "  Processed: $($file.FullName)" -ForegroundColor Gray
    $count++
}

Write-Host "`nDone! Processed $count Java files." -ForegroundColor Green
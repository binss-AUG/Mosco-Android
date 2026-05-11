$basePath = "d:\MEox\UITer\DOAN\Mosco_Megre\Mosco\client\app\src\main\java\com\vn\jet\mosco"
$files = @("RankActivity.java","MissionActivity.java","FriendActivity.java","FormationActivity.java","DailyCheckinActivity.java")

foreach ($file in $files) {
    $fullPath = Join-Path $basePath $file
    if (Test-Path $fullPath) {
        $content = Get-Content $fullPath -Raw
        $content = $content -replace 'extends AppCompatActivity','extends MoscoBaseActivity'
        Set-Content -Path $fullPath -Value $content -NoNewline
        Write-Output "Updated: $file"
    }
}

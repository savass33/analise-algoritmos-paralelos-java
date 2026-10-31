param(
    [ValidateSet('verify','benchmark','analyze','charts','dashboard','all')]
    [string]$Action = 'all'
)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildId = [Guid]::NewGuid().ToString('N')
$classes = Join-Path ([System.IO.Path]::GetTempPath()) "comp_paralela_$buildId"
$sources = Get-ChildItem -Path (Join-Path $project 'src') -Filter '*.java' | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force -Path $classes | Out-Null
try {
    javac --release 17 -encoding UTF-8 -d $classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar os arquivos Java.' }

    $main = 'BenchmarkApp'
    $csv = Join-Path $project 'resultados\resultados.csv'
    $summary = Join-Path $project 'resultados\resumo.csv'
    $charts = Join-Path $project 'resultados\graficos'

    if ($Action -in @('verify','all')) {
        java -cp $classes $main verify
        if ($LASTEXITCODE -ne 0) { throw 'Falha na verificacao dos algoritmos.' }
    }
    if ($Action -in @('benchmark','all')) {
        java -cp $classes $main benchmark $csv
        if ($LASTEXITCODE -ne 0) { throw 'Falha durante o benchmark.' }
    }
    if ($Action -in @('analyze','all')) {
        java -cp $classes $main analyze $csv $summary
        if ($LASTEXITCODE -ne 0) { throw 'Falha durante a analise dos CSVs.' }
    }
    if ($Action -in @('charts','all')) {
        java -cp $classes $main charts $csv $charts
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar os graficos.' }
    }
    if ($Action -eq 'dashboard') {
        java -cp $classes $main dashboard $csv
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao abrir o painel Swing.' }
    }
}
finally {
    if (Test-Path -LiteralPath $classes) {
        Remove-Item -LiteralPath $classes -Recurse -Force
    }
}

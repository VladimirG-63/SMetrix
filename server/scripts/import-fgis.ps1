param(
    [Parameter(Mandatory = $true)]
    [string]$File,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_-]{1,20}$')]
    [string]$Region,

    [ValidatePattern('^$|^20\d{2}-Q[1-4]$')]
    [string]$Period = '',

    [string]$ImportKey = $env:FGIS_IMPORT_KEY,

    [string]$ServerUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $File -PathType Leaf)) {
    throw "FGIS XLSX file not found: $File"
}
if ([IO.Path]::GetExtension($File) -ne '.xlsx') {
    throw 'The official FGIS split form must be an .xlsx file.'
}
if ([string]::IsNullOrWhiteSpace($ImportKey)) {
    throw 'Set FGIS_IMPORT_KEY or pass -ImportKey.'
}

$resolvedFile = (Resolve-Path -LiteralPath $File).Path
$endpoint = $ServerUrl.TrimEnd('/') + '/api/v1/admin/fgis/import'
$arguments = @(
    '--fail-with-body', '--silent', '--show-error',
    '-X', 'POST',
    '-H', "X-FGIS-Import-Key: $ImportKey",
    '-F', "region=$Region",
    '-F', "file=@$resolvedFile;type=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
)
if (-not [string]::IsNullOrWhiteSpace($Period)) {
    $arguments += @('-F', "period=$Period")
}
$arguments += $endpoint

Write-Host "Importing official FGIS split form for region $Region ..."
& curl.exe @arguments
if ($LASTEXITCODE -ne 0) {
    throw "FGIS import failed (curl exit code $LASTEXITCODE)."
}
Write-Host "`nFGIS import completed. Materials are available through /api/v1/materials."

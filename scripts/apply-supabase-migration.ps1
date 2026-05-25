param(
  [Parameter(Mandatory = $true)]
  [string]$MigrationPath,

  [string]$EnvPath = (Join-Path (Split-Path $PSScriptRoot -Parent) ".env")
)

$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "invoke-psql.ps1") -EnvPath $EnvPath -FilePath $MigrationPath
$exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }

if ($exitCode -ne 0) {
  exit $exitCode
}

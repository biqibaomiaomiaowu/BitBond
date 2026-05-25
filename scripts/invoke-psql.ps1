param(
  [string]$EnvPath = (Join-Path (Split-Path $PSScriptRoot -Parent) ".env"),

  [Parameter(Mandatory = $true, ParameterSetName = "Command")]
  [string]$CommandText,

  [Parameter(Mandatory = $true, ParameterSetName = "File")]
  [string]$FilePath
)

$ErrorActionPreference = "Stop"
$previousPgPassword = $env:PGPASSWORD

function Get-DatabaseConnectionParts {
  param(
    [Parameter(Mandatory = $true)]
    [string]$DatabaseUrl
  )

  $uri = $null
  if (-not [System.Uri]::TryCreate($DatabaseUrl, [System.UriKind]::Absolute, [ref]$uri)) {
    throw "DATABASE_URL is invalid"
  }

  if ($uri.Scheme -notin @("postgres", "postgresql")) {
    throw "DATABASE_URL must use postgres or postgresql scheme"
  }

  $userInfoParts = $uri.UserInfo -split ":", 2
  if ($userInfoParts.Count -lt 1 -or [string]::IsNullOrWhiteSpace($userInfoParts[0])) {
    throw "DATABASE_URL must include user"
  }

  $password = $null
  if ($userInfoParts.Count -ge 2) {
    $password = [System.Uri]::UnescapeDataString($userInfoParts[1])
  }

  $databaseName = [System.Uri]::UnescapeDataString($uri.AbsolutePath.TrimStart("/"))
  if ([string]::IsNullOrWhiteSpace($uri.Host) -or [string]::IsNullOrWhiteSpace($databaseName)) {
    throw "DATABASE_URL must include host and database name"
  }

  $port = $uri.Port
  if ($port -lt 0) {
    $port = 5432
  }

  return [pscustomobject]@{
    Host = $uri.Host
    Port = [string]$port
    User = [System.Uri]::UnescapeDataString($userInfoParts[0])
    Password = $password
    Database = $databaseName
  }
}

$exitCode = 0
try {
  & (Join-Path $PSScriptRoot "load-env.ps1") -Path $EnvPath

  if ([string]::IsNullOrWhiteSpace($env:DATABASE_URL)) {
    throw "DATABASE_URL is required"
  }

  $connection = Get-DatabaseConnectionParts -DatabaseUrl $env:DATABASE_URL
  $psqlArgs = @(
    "-h", $connection.Host,
    "-p", $connection.Port,
    "-U", $connection.User,
    "-d", $connection.Database
  )

  if ($PSCmdlet.ParameterSetName -eq "Command") {
    $psqlArgs += @("-c", $CommandText)
  }
  else {
    $psqlArgs += @("-f", $FilePath)
  }

  if ($null -eq $connection.Password) {
    $env:PGPASSWORD = $null
  }
  else {
    $env:PGPASSWORD = $connection.Password
  }
  & psql @psqlArgs
  $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
}
finally {
  $env:PGPASSWORD = $previousPgPassword
}

if ($exitCode -ne 0) {
  exit $exitCode
}

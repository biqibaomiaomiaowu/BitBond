param(
  [string]$EnvPath = (Join-Path (Split-Path $PSScriptRoot -Parent) ".env")
)

$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "load-env.ps1") -Path $EnvPath

if ([string]::IsNullOrWhiteSpace($env:SUPABASE_URL)) {
  throw "SUPABASE_URL is required"
}

if ([string]::IsNullOrWhiteSpace($env:SUPABASE_ANON_KEY)) {
  throw "SUPABASE_ANON_KEY is required"
}

$supabaseUrl = $env:SUPABASE_URL.TrimEnd("/")
$headers = @{
  "apikey" = $env:SUPABASE_ANON_KEY
  "Authorization" = "Bearer $($env:SUPABASE_ANON_KEY)"
}

Invoke-RestMethod `
  -Method Get `
  -Uri "$supabaseUrl/rest/v1/avatars?select=id&limit=1" `
  -Headers $headers `
  | Out-Null

Write-Output "Supabase smoke: avatars endpoint reachable"

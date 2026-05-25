$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$scratch = Join-Path $root ".tmp-script-test"
$originalPath = $env:Path
$originalPgPassword = $env:PGPASSWORD

try {
  New-Item -ItemType Directory -Force -Path $scratch | Out-Null

  $fakeEnv = Join-Path $scratch ".env"
  $fakeDatabaseUrl = "postgresql://user:fake-password@example.test:5432/db"
  Set-Content -Path $fakeEnv -Value @(
    "SUPABASE_URL=https://example.test",
    ("SUPABASE" + "_ANON_KEY=fake-anon-value"),
    ("DATABASE" + "_URL=" + $fakeDatabaseUrl)
  )

  $output = powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "scripts\load-env.ps1") -Path $fakeEnv 2>&1
  if ($LASTEXITCODE -ne 0) { throw "load-env failed" }
  if ($output -match "fake-anon-value|fake-password") { throw "secret leaked from load-env" }

  $argvFile = Join-Path $scratch "argv.txt"
  $passwordFile = Join-Path $scratch "pgpassword.txt"
  $psqlShim = Join-Path $scratch "psql.cmd"
  Set-Content -Path $psqlShim -Value @"
@echo off
if defined PGPASSWORD (echo %PGPASSWORD%>"$passwordFile") else (echo __UNSET__>"$passwordFile")
for %%A in (%*) do echo %%~A>>"$argvFile"
"@
  $env:Path = "$scratch;$env:Path"
  $env:PGPASSWORD = "preexisting-parent-value"

  $output = & (Join-Path $root "scripts\invoke-psql.ps1") -EnvPath $fakeEnv -CommandText "select 1" 2>&1
  if ($LASTEXITCODE -ne 0) { throw "invoke-psql failed" }
  if ($output -match "fake-password|$([regex]::Escape($fakeDatabaseUrl))") { throw "secret leaked from invoke-psql output" }
  if ($env:PGPASSWORD -ne "preexisting-parent-value") { throw "PGPASSWORD was not restored in current process" }

  $argv = Get-Content -Path $argvFile
  $joinedArgv = $argv -join " "
  if ($joinedArgv -match ("fake-password|" + "postgres" + "://|" + [regex]::Escape($fakeDatabaseUrl))) { throw "secret leaked from invoke-psql argv" }
  if (($argv | Select-Object -Index 0) -ne "-h" -or ($argv | Select-Object -Index 1) -ne "example.test") { throw "psql host args were not discrete argv tokens" }
  if (($argv | Select-Object -Index 2) -ne "-p" -or ($argv | Select-Object -Index 3) -ne "5432") { throw "psql port args were not discrete argv tokens" }
  if (($argv | Select-Object -Index 4) -ne "-U" -or ($argv | Select-Object -Index 5) -ne "user") { throw "psql user args were not discrete argv tokens" }
  if (($argv | Select-Object -Index 6) -ne "-d" -or ($argv | Select-Object -Index 7) -ne "db") { throw "psql database args were not discrete argv tokens" }
  if (($argv | Select-Object -Index 8) -ne "-c" -or ($argv | Select-Object -Index 9) -ne "select 1") { throw "psql command args were not discrete argv tokens" }

  $capturedPassword = Get-Content -Path $passwordFile -Raw
  if ($capturedPassword.Trim() -ne "fake-password") { throw "PGPASSWORD was not set for psql process" }

  Remove-Item -Force $argvFile, $passwordFile

  $passwordlessEnv = Join-Path $scratch ".env-passwordless"
  Set-Content -Path $passwordlessEnv -Value @(
    "SUPABASE_URL=https://example.test",
    ("SUPABASE" + "_ANON_KEY=fake-anon-value"),
    "PGPASSWORD=env-file-password-should-not-survive",
    ("DATABASE" + "_URL=postgresql://readonly@example.test:5432/db2")
  )

  $env:PGPASSWORD = "preexisting-parent-value"
  $output = & (Join-Path $root "scripts\invoke-psql.ps1") -EnvPath $passwordlessEnv -CommandText "select 2" 2>&1
  if ($LASTEXITCODE -ne 0) { throw "invoke-psql passwordless failed" }
  if ($env:PGPASSWORD -ne "preexisting-parent-value") { throw "PGPASSWORD was not restored after passwordless URL" }

  $argv = Get-Content -Path $argvFile
  if (($argv | Select-Object -Index 0) -ne "-h" -or ($argv | Select-Object -Index 1) -ne "example.test") { throw "passwordless psql host args were not discrete argv tokens" }
  if (($argv | Select-Object -Index 4) -ne "-U" -or ($argv | Select-Object -Index 5) -ne "readonly") { throw "passwordless psql user args were not discrete argv tokens" }
  if (($argv | Select-Object -Index 6) -ne "-d" -or ($argv | Select-Object -Index 7) -ne "db2") { throw "passwordless psql database args were not discrete argv tokens" }

  $capturedPassword = Get-Content -Path $passwordFile -Raw
  if ($capturedPassword.Trim() -ne "__UNSET__") { throw "passwordless URL leaked preexisting PGPASSWORD into psql process" }

  $invalidEnv = Join-Path $scratch ".env-invalid-url"
  Set-Content -Path $invalidEnv -Value @(
    "PGPASSWORD=env-file-password-should-not-survive",
    ("DATABASE" + "_URL=mysql://readonly@example.test:5432/db2")
  )

  $env:PGPASSWORD = "preexisting-parent-value"
  $invalidUrlFailed = $false
  try {
    $output = & (Join-Path $root "scripts\invoke-psql.ps1") -EnvPath $invalidEnv -CommandText "select 3" 2>&1
  }
  catch {
    $invalidUrlFailed = $true
    $output = $_.Exception.Message
  }

  if (-not $invalidUrlFailed) { throw "invoke-psql invalid URL unexpectedly passed" }
  if ($env:PGPASSWORD -ne "preexisting-parent-value") { throw "PGPASSWORD was not restored after pre-psql failure" }
  if ($output -match "env-file-password-should-not-survive") { throw "pre-psql failure leaked env PGPASSWORD" }

  $malformedEnv = Join-Path $scratch ".env-malformed-url"
  $malformedUrl = "postgresql://user:fake-password@[bad/db"
  Set-Content -Path $malformedEnv -Value @(
    "PGPASSWORD=env-file-password-should-not-survive",
    ("DATABASE" + "_URL=" + $malformedUrl)
  )

  $env:PGPASSWORD = "preexisting-parent-value"
  $malformedFailed = $false
  try {
    $output = & (Join-Path $root "scripts\invoke-psql.ps1") -EnvPath $malformedEnv -CommandText "select 4" 2>&1
  }
  catch {
    $malformedFailed = $true
    $output = $_.Exception.Message
  }

  if (-not $malformedFailed) { throw "invoke-psql malformed URL unexpectedly passed" }
  if ($env:PGPASSWORD -ne "preexisting-parent-value") { throw "PGPASSWORD was not restored after malformed URL failure" }
  if ($output -match "fake-password|$([regex]::Escape($malformedUrl))|env-file-password-should-not-survive") { throw "malformed URL failure leaked secret value" }
}
finally {
  $env:Path = $originalPath
  $env:PGPASSWORD = $originalPgPassword
  if (Test-Path -Path $scratch) {
    Remove-Item -Recurse -Force $scratch
  }
}

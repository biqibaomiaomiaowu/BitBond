param(
  [Parameter(Mandatory = $true)]
  [string]$Path
)

$ErrorActionPreference = "Stop"

function ConvertFrom-DotEnvValue {
  param(
    [Parameter(Mandatory = $true)]
    [AllowEmptyString()]
    [string]$Text
  )

  $value = $Text.Trim()
  if ($value.Length -eq 0) {
    return ""
  }

  if ($value[0] -eq '"') {
    $builder = [System.Text.StringBuilder]::new()
    $escaped = $false

    for ($i = 1; $i -lt $value.Length; $i++) {
      $char = $value[$i]

      if ($escaped) {
        $append = switch ([string]$char) {
          "n" { "`n" }
          "r" { "`r" }
          "t" { "`t" }
          '"' { '"' }
          "\" { "\" }
          default { [string]$char }
        }

        [void]$builder.Append($append)
        $escaped = $false
      }
      elseif ($char -eq "\") {
        $escaped = $true
      }
      elseif ($char -eq '"') {
        return $builder.ToString()
      }
      else {
        [void]$builder.Append($char)
      }
    }

    return $builder.ToString()
  }

  if ($value[0] -eq "'") {
    $endQuote = $value.IndexOf("'", 1)
    if ($endQuote -ge 1) {
      return $value.Substring(1, $endQuote - 1)
    }

    return $value.Substring(1)
  }

  $comment = [regex]::Match($value, "\s+#")
  if ($comment.Success) {
    $value = $value.Substring(0, $comment.Index).TrimEnd()
  }

  return $value
}

$envFile = Resolve-Path -LiteralPath $Path

foreach ($line in Get-Content -LiteralPath $envFile) {
  $trimmed = $line.Trim()
  if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
    continue
  }

  if ($line -notmatch '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
    continue
  }

  $name = $Matches[1]
  $value = ConvertFrom-DotEnvValue -Text $Matches[2]
  [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
}

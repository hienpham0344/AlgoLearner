# Loads literal KEY=VALUE entries without evaluating shell expressions.
# Existing process environment variables take precedence over .env.
$ErrorActionPreference = 'Stop'
$savedEnvironment = @{}
Push-Location $PSScriptRoot
try {
    if (Test-Path -LiteralPath '.env') {
        foreach ($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
            if ($line -match '^\s*(#|$)') { continue }
            if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
                throw 'Invalid .env entry; expected KEY=VALUE (contents hidden).'
            }
            $key = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            if ($null -eq [Environment]::GetEnvironmentVariable($key, 'Process')) {
                $savedEnvironment[$key] = $null
                [Environment]::SetEnvironmentVariable($key, $value, 'Process')
            }
        }
    }
    $maven = Get-Command mvn -ErrorAction SilentlyContinue
    if ($maven) { $executable = $maven.Source }
    elseif (Test-Path '.tools/apache-maven-3.9.9/bin/mvn.cmd') {
        $executable = Join-Path $PSScriptRoot '.tools/apache-maven-3.9.9/bin/mvn.cmd'
    }
    else { throw 'Maven not found. Install Maven and add it to PATH.' }
    & $executable @args
    $mavenExitCode = $LASTEXITCODE
}
finally {
    foreach ($key in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $savedEnvironment[$key], 'Process')
    }
    Pop-Location
}
exit $mavenExitCode

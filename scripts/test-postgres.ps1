param(
    [string]$PostgresBin = "$env:ProgramFiles\PostgreSQL\18\bin"
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$mavenCommand = (Get-Command mvn -ErrorAction Stop).Source
$initdbCommand = Join-Path $PostgresBin 'initdb.exe'
$pgctlCommand = Join-Path $PostgresBin 'pg_ctl.exe'
if (!(Test-Path -LiteralPath $initdbCommand) -or !(Test-Path -LiteralPath $pgctlCommand)) {
    throw 'PostgreSQL binaries not found. Supply -PostgresBin with your installation directory.'
}

$testRoot = Join-Path $projectRoot ('target\postgres-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
$clusterPath = Join-Path $testRoot 'data'
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
try {
    $listener.Start()
    $testPort = $listener.LocalEndpoint.Port
} finally {
    $listener.Stop()
}

$previousEnvironment = @{}
foreach ($name in @('TEST_DATABASE_URL', 'TEST_DATABASE_USER', 'TEST_DATABASE_PASSWORD')) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name)
}
$started = $false
Push-Location $projectRoot
try {
    & $initdbCommand -D $clusterPath -U geoquest_test -A trust --encoding=UTF8 --no-locale *> (Join-Path $testRoot 'initdb.log')
    if ($LASTEXITCODE -ne 0) {
        throw "Could not initialize temporary PostgreSQL. See $testRoot\initdb.log"
    }
    & $pgctlCommand -D $clusterPath -l (Join-Path $testRoot 'server.log') -o "-h 127.0.0.1 -p $testPort" -w start
    if ($LASTEXITCODE -ne 0) {
        throw "Could not start temporary PostgreSQL. See $testRoot\server.log"
    }
    $started = $true
    $env:TEST_DATABASE_URL = "jdbc:postgresql://127.0.0.1:$testPort/postgres"
    $env:TEST_DATABASE_USER = 'geoquest_test'
    $env:TEST_DATABASE_PASSWORD = 'test-password'

    & $mavenCommand -B -Ppostgres-integration test
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL integration tests failed. Inspect the Maven output above.'
    }
} finally {
    if ($started) {
        & $pgctlCommand -D $clusterPath -m fast -w stop
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Could not stop temporary PostgreSQL at $clusterPath"
        }
    }
    foreach ($name in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name])
    }
    Pop-Location
}

$ErrorActionPreference = "Stop"

$containers = @(
    "shadowbase-kafka",
    "shadowbase-debezium",
    "shadowbase-mysql-cdc"
)

$failed = $false

Write-Host "`nChecking ShadowBase CDC containers..." -ForegroundColor Cyan

foreach ($container in $containers) {
    try {
        $status = docker inspect --format '{{.State.Status}}' $container 2>$null
        $health = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}no healthcheck{{end}}' $container 2>$null

        if ($status -eq "running" -and $health -eq "healthy") {
            Write-Host "[PASS] $container is running and healthy" -ForegroundColor Green
        }
        else {
            Write-Host "[FAIL] $container status: $status, health: $health" -ForegroundColor Red
            $failed = $true
        }
    }
    catch {
        Write-Host "[FAIL] Could not inspect $container" -ForegroundColor Red
        $failed = $true
    }
}

Write-Host "`nChecking Debezium Connect REST API..." -ForegroundColor Cyan

try {
    $connectInfo = Invoke-RestMethod "http://localhost:8083/"
    Write-Host "[PASS] Debezium Connect API is responding (version $($connectInfo.version))" -ForegroundColor Green
}
catch {
    Write-Host "[FAIL] Debezium Connect API is not responding" -ForegroundColor Red
    $failed = $true
}

Write-Host "`nChecking MySQL Debezium connector..." -ForegroundColor Cyan

try {
    $connector = Invoke-RestMethod "http://localhost:8083/connectors/shadowbase-mysql-connector/status"

    if ($connector.connector.state -eq "RUNNING") {
        Write-Host "[PASS] Connector is RUNNING" -ForegroundColor Green
    }
    else {
        Write-Host "[FAIL] Connector state: $($connector.connector.state)" -ForegroundColor Red
        $failed = $true
    }

    if ($connector.tasks.Count -gt 0 -and
        @($connector.tasks | Where-Object { $_.state -ne "RUNNING" }).Count -eq 0) {
        Write-Host "[PASS] All connector tasks are RUNNING" -ForegroundColor Green
    }
    else {
        Write-Host "[FAIL] One or more connector tasks are not RUNNING" -ForegroundColor Red
        $failed = $true
    }
}
catch {
    Write-Host "[FAIL] Could not retrieve connector status: $($_.Exception.Message)" -ForegroundColor Red
    $failed = $true
}

if ($failed) {
    Write-Host "`nCDC health check FAILED." -ForegroundColor Red
    exit 1
}

Write-Host "`nAll ShadowBase CDC health checks passed." -ForegroundColor Green
exit 0

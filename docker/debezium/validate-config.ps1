$ErrorActionPreference = "Stop"

$composeFile = Join-Path $PSScriptRoot "docker-compose.yml"
$configFile = Join-Path $PSScriptRoot "connector-config.json"

if (-not (Test-Path $composeFile)) {
    Write-Error "Docker Compose file not found: $composeFile"
    exit 1
}

if (-not (Test-Path $configFile)) {
    Write-Error "Connector configuration not found: $configFile"
    exit 1
}

try {
    $connector = Get-Content $configFile -Raw | ConvertFrom-Json -ErrorAction Stop
}
catch {
    Write-Error "Invalid connector JSON: $($_.Exception.Message)"
    exit 1
}

if ($connector.name -ne "shadowbase-mysql-connector") {
    Write-Error "Unexpected connector name."
    exit 1
}

$config = $connector.config

$requiredProperties = @(
    "connector.class",
    "topic.prefix",
    "database.user",
    "database.password",
    "database.hostname",
    "database.port",
    "database.server.id",
    "database.include.list",
    "table.include.list",
    "schema.history.internal.kafka.bootstrap.servers",
    "schema.history.internal.kafka.topic"
)

$missingProperties = @(
    $requiredProperties | Where-Object {
        $property = $_
        $value = $config.PSObject.Properties[$property]
        $null -eq $value -or [string]::IsNullOrWhiteSpace([string]$value.Value)
    }
)

if ($missingProperties.Count -gt 0) {
    Write-Error "Missing required properties: $($missingProperties -join ', ')"
    exit 1
}

if ($config.'connector.class' -ne "io.debezium.connector.mysql.MySqlConnector") {
    Write-Error "Incorrect Debezium MySQL connector class."
    exit 1
}

if ($config.'database.password' -ne '${DEBEZIUM_DB_PASSWORD}') {
    Write-Error "Database password must use the DEBEZIUM_DB_PASSWORD environment variable."
    exit 1
}

Write-Host "Debezium configuration validation passed." -ForegroundColor Green
Write-Host "Connector: $($connector.name)"
Write-Host "Required properties: $($requiredProperties.Count) checked"

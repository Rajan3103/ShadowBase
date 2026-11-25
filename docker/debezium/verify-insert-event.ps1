param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Email,

    [int]$TimeoutMs = 15000
)

$ErrorActionPreference = "Stop"

$container = "shadowbase-kafka"
$topic = "shadowbase_mysql.shadowbase_cdc.customers"

Write-Host "Checking Kafka container..." -ForegroundColor Cyan

$running = docker inspect --format '{{.State.Running}}' $container 2>$null

if ($LASTEXITCODE -ne 0 -or $running -ne "true") {
    Write-Error "Kafka container is not running."
    exit 1
}

Write-Host "Reading Kafka topic: $topic" -ForegroundColor Cyan

$previousPreference = $ErrorActionPreference

try {
    $ErrorActionPreference = "Continue"

    $messages = @(
        & docker exec $container `
            /kafka/bin/kafka-console-consumer.sh `
            --bootstrap-server kafka:9092 `
            --topic $topic `
            --from-beginning `
            --timeout-ms $TimeoutMs 2>$null
    )

    $consumerExitCode = $LASTEXITCODE
}
finally {
    $ErrorActionPreference = $previousPreference
}

foreach ($line in $messages) {
    $jsonLine = [string]$line

    if (-not $jsonLine.TrimStart().StartsWith("{")) {
        continue
    }

    try {
        $event = $jsonLine | ConvertFrom-Json -ErrorAction Stop
    }
    catch {
        continue
    }

    if ($event.payload.after.email -eq $Email -and
        $event.payload.op -eq "c") {

        Write-Host "`n[PASS] Matching INSERT event found in Kafka." -ForegroundColor Green
        Write-Host "Email: $Email"
        Write-Host "Topic: $topic"
        Write-Host "Operation: INSERT (c)"
        exit 0
    }
}

if ($consumerExitCode -ne 0) {
    Write-Error "Kafka consumer failed with exit code $consumerExitCode."
    exit 1
}

Write-Error "No matching INSERT event found for email: $Email"
exit 1

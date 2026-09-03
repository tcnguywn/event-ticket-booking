# Script khoi dong toan bo cac Microservices trong cac cua so rieng biet
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  STARTING ALL TICKET BOOKING SERVICES   " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$rootPath = $PSScriptRoot
if (-not $rootPath) {
    $rootPath = Get-Location
}

$services = @(
    @{ Name = "API-Gateway"; Task = ":gateway:bootRun"; Port = 8888 },
    @{ Name = "Event-Ticket-Service"; Task = ":event-ticket-service:bootRun"; Port = 8082 },
    @{ Name = "Order-Service"; Task = ":order-service:bootRun"; Port = 8083 },
    @{ Name = "Payment-Service"; Task = ":payment-service:bootRun"; Port = 8084 },
    @{ Name = "Notification-Service"; Task = ":notification-service:bootRun"; Port = 8085 }
)

foreach ($svc in $services) {
    Write-Host "Launching $($svc.Name) on port $($svc.Port)..." -ForegroundColor Yellow
    $cmd = "`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; cd '$rootPath'; ./gradlew $($svc.Task)"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd
    Start-Sleep -Seconds 3
}

Write-Host "`nAll services have been launched in separate terminal windows." -ForegroundColor Green

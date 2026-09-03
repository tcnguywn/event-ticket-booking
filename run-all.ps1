param(
    [switch]$SkipDocker = $false
)

# Script khoi dong toan bo ha tang va cac Microservices trong cac cua so rieng biet
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  STARTING ALL TICKET BOOKING SERVICES   " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$rootPath = $PSScriptRoot
if (-not $rootPath) {
    $rootPath = Get-Location
}

# 1. Khoi dong ha tang Docker
if (-not $SkipDocker) {
    Write-Host "`n[1/2] Checking & Starting Infrastructure Containers in Docker..." -ForegroundColor Magenta
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $existingContainers = @(
            "redis-server",
            "qi-kafka-1",
            "qi-kafka-ui-1",
            "ticketing_keycloak"
        )

        foreach ($c in $existingContainers) {
            $check = docker ps -a -q -f "name=^/${c}$"
            if ($check) {
                Write-Host "  -> Starting container: $c..." -ForegroundColor Yellow
                docker start $c | Out-Null
            }
        }

        # Kiem tra va khoi dong MailHog (SMTP container cho notification-service)
        $mailhogExists = docker ps -a -q -f "name=^/ticketing_mailhog$"
        if (-not $mailhogExists) {
            Write-Host "  -> Container 'ticketing_mailhog' chua co, dang tao moi..." -ForegroundColor Yellow
            docker run -d --name ticketing_mailhog -p 1025:1025 -p 8025:8025 mailhog/mailhog:latest | Out-Null
        } else {
            Write-Host "  -> Starting container: ticketing_mailhog..." -ForegroundColor Yellow
            docker start ticketing_mailhog | Out-Null
        }

        Write-Host "  -> Docker infrastructure is UP!" -ForegroundColor Green
        Write-Host "     * MailHog Web Inbox: http://localhost:8025 (SMTP: 1025)" -ForegroundColor Cyan
        Write-Host "     * Kafka UI:          http://localhost:8081" -ForegroundColor Cyan
        Write-Host "     * Keycloak Auth:     http://localhost:8080" -ForegroundColor Cyan
        Write-Host "     * Redis Cache:       localhost:6379" -ForegroundColor Cyan
        Write-Host "     * Kafka Broker:      localhost:9092" -ForegroundColor Cyan
    } else {
        Write-Host "  [WARN] Docker command not found. Skipping Docker containers launch." -ForegroundColor Yellow
    }
} else {
    Write-Host "`n[1/2] Skipping Docker containers launch (-SkipDocker specified)." -ForegroundColor DarkGray
}

# 2. Khoi dong cac Microservices
Write-Host "`n[2/2] Launching Java Microservices in Separate Terminals..." -ForegroundColor Magenta

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

Write-Host "`n=========================================" -ForegroundColor Green
Write-Host "  ALL SERVICES LAUNCHED SUCCESSFULLY!    " -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host "Endpoints overview:" -ForegroundColor White
Write-Host " - API Gateway:            http://localhost:8888" -ForegroundColor Gray
Write-Host " - Event Ticket Service:   http://localhost:8082" -ForegroundColor Gray
Write-Host " - Order Service:          http://localhost:8083" -ForegroundColor Gray
Write-Host " - Payment Service:        http://localhost:8084" -ForegroundColor Gray
Write-Host " - Notification Service:   http://localhost:8085" -ForegroundColor Gray
Write-Host " - MailHog Web Inbox:      http://localhost:8025" -ForegroundColor Gray
Write-Host " - Keycloak Admin:         http://localhost:8080" -ForegroundColor Gray


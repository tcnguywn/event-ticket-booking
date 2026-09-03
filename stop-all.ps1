param(
    [switch]$StopDocker = $false
)

# Script dung toan bo cac microservices dang chay
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  STOPPING ALL TICKET BOOKING SERVICES   " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$rootPath = $PSScriptRoot
if (-not $rootPath) {
    $rootPath = Get-Location
}

# 1. Dung cac Java Microservices theo port
$ports = @(8888, 8082, 8083, 8084, 8085)

foreach ($port in $ports) {
    # 1. Thu dung Get-NetTCPConnection
    $pids = @()
    $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($connections) {
        $pids += $connections.OwningProcess
    }
    
    # 2. Fallback sang netstat
    $netstatOut = netstat -ano | findstr ":$port "
    if ($netstatOut) {
        foreach ($line in $netstatOut) {
            $parts = $line.Trim() -split '\s+'
            if ($parts.Length -ge 5) {
                $pidVal = [int]$parts[-1]
                if ($pidVal -gt 0 -and ($pids -notcontains $pidVal)) {
                    $pids += $pidVal
                }
            }
        }
    }

    if ($pids.Count -gt 0) {
        foreach ($procId in ($pids | Select-Object -Unique)) {
            try {
                $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
                Write-Host "Stopping process: $($proc.ProcessName) (PID: $procId) listening on port $port..." -ForegroundColor Yellow
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            } catch {
                # Process already stopped
            }
        }
    } else {
        Write-Host "No active process on port $port." -ForegroundColor DarkGray
    }
}

# 2. Cleanup gradle daemons
Write-Host "`nStopping Gradle Daemons..." -ForegroundColor DarkGray
./gradlew --stop 2>$null

# 3. Dung Docker Containers neu co flag -StopDocker
if ($StopDocker) {
    Write-Host "`nStopping Docker Infrastructure Containers (MailHog, Kafka, Redis, Keycloak)..." -ForegroundColor Yellow
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        $containers = @("ticketing_keycloak", "redis-server", "qi-kafka-1", "qi-kafka-ui-1", "ticketing_mailhog")
        foreach ($c in $containers) {
            $id = docker ps -q -f "name=^/${c}$"
            if ($id) {
                Write-Host "  -> Stopping container: $c..." -ForegroundColor DarkYellow
                docker stop $c | Out-Null
            }
        }
        Write-Host "Docker containers stopped." -ForegroundColor Green
    }
}

Write-Host "`n=========================================" -ForegroundColor Green
Write-Host "  ALL SERVICES STOPPED SUCCESSFULLY!     " -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green

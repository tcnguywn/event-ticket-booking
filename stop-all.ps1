# Script dung toan bo cac microservices dang chay
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  STOPPING ALL TICKET BOOKING SERVICES   " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

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
                Write-Host "Stopping PID: $procId listening on port $port..." -ForegroundColor Yellow
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            } catch {
                # Process already stopped
            }
        }
    } else {
        Write-Host "No active process on port $port." -ForegroundColor DarkGray
    }
}

# Cleanup gradle daemons
./gradlew --stop 2>$null

Write-Host "`n=========================================" -ForegroundColor Green
Write-Host "  ALL SERVICES STOPPED SUCCESSFULLY!     " -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green

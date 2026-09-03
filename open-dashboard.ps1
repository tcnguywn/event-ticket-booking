# Script mo giao dien Console Dashboard test trong browser
$dashboardPath = Join-Path $PSScriptRoot "dashboard\index.html"
Write-Host "Opening Distributed Ticket Booking Dashboard: $dashboardPath" -ForegroundColor Cyan
Start-Process $dashboardPath

# Traffic Accident Risk Platform - One-Click Start Script
$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ROOT

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Traffic Accident Risk Platform - Starting All Services" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# ============================================================
# 1. Environment Check
# ============================================================
Write-Host "[1/7] Checking environment..." -ForegroundColor Yellow

$dockerOk = Get-Command docker -ErrorAction SilentlyContinue
if ($dockerOk) { Write-Host "  [OK] Docker" -ForegroundColor Green }
else { Write-Host "  [WARN] Docker not found - MySQL/Redis unavailable" -ForegroundColor Yellow }

$javaOk = Get-Command java -ErrorAction SilentlyContinue
if ($javaOk) { Write-Host "  [OK] Java" -ForegroundColor Green }
else { Write-Host "  [ERROR] Java not found! Install JDK 17+" -ForegroundColor Red }

$mvnOk = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvnOk) { Write-Host "  [OK] Maven" -ForegroundColor Green }
else { Write-Host "  [ERROR] Maven not found!" -ForegroundColor Red }

$nodeOk = Get-Command node -ErrorAction SilentlyContinue
if ($nodeOk) { Write-Host "  [OK] Node.js $(node --version)" -ForegroundColor Green }
else { Write-Host "  [ERROR] Node.js not found! Install Node.js 18+" -ForegroundColor Red }

$pyOk = Get-Command python -ErrorAction SilentlyContinue
if ($pyOk) { Write-Host "  [OK] Python $(python --version 2>&1)" -ForegroundColor Green }
else { Write-Host "  [ERROR] Python not found! Install Python 3.9+" -ForegroundColor Red }

if ((-not $javaOk) -or (-not $mvnOk) -or (-not $nodeOk) -or (-not $pyOk)) {
    Write-Host ""
    Write-Host "Environment check FAILED. Please install missing dependencies." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""

# ============================================================
# 2. Docker Services (MySQL + Redis)
# ============================================================
Write-Host "[2/7] Starting Docker containers (MySQL + Redis)..." -ForegroundColor Yellow

$mysqlCompose = Join-Path $ROOT "backend\docker-compose.mysql.yml"
$redisCompose = Join-Path $ROOT "backend\docker-compose.redis.yml"

if ($dockerOk) {
    docker compose -f "$mysqlCompose" up -d 2>$null
    Write-Host "  [OK] MySQL started (port 3306)" -ForegroundColor Green
    docker compose -f "$redisCompose" up -d 2>$null
    Write-Host "  [OK] Redis started (port 6379)" -ForegroundColor Green
} else {
    Write-Host "  [SKIP] Docker unavailable" -ForegroundColor Yellow
}

Write-Host ""

# ============================================================
# 3. Install Frontend Dependencies
# ============================================================
Write-Host "[3/7] Installing frontend dependencies..." -ForegroundColor Yellow
Push-Location (Join-Path $ROOT "frontend")
npm install 2>$null
Pop-Location
Write-Host "  [OK] Dependencies installed" -ForegroundColor Green
Write-Host ""

# ============================================================
# 4. Start Algorithm Services
# ============================================================
Write-Host "[4/7] Starting algorithm services..." -ForegroundColor Yellow

$alg1Dir = Join-Path $ROOT "algorithm1"
Start-Job -Name "alg1-yolov5" -ArgumentList $alg1Dir -ScriptBlock {
    param($d); Set-Location $d; python run.py 2>&1 | Out-File "$d\alg1.log"
} | Out-Null
Write-Host "  [OK] Algorithm1 (YOLOv5) - port 8000" -ForegroundColor Green

$alg2Dir = Join-Path $ROOT "algorithm2"
Start-Job -Name "alg2-expert" -ArgumentList $alg2Dir -ScriptBlock {
    param($d); Set-Location $d; python run.py 2>&1 | Out-File "$d\alg2.log"
} | Out-Null
Write-Host "  [OK] Algorithm2 (Expert System) - port 8001" -ForegroundColor Green

$alg3Dir = Join-Path $ROOT "algorithm3"
Start-Job -Name "alg3-recovery" -ArgumentList $alg3Dir -ScriptBlock {
    param($d); Set-Location $d; python run.py 2>&1 | Out-File "$d\alg3.log"
} | Out-Null
Write-Host "  [OK] Algorithm3 (Recovery) - port 8003" -ForegroundColor Green

$alg4Dir = Join-Path $ROOT "algorithm4"
Start-Job -Name "alg4-dispatch" -ArgumentList $alg4Dir -ScriptBlock {
    param($d); Set-Location $d; python run.py 2>&1 | Out-File "$d\alg4.log"
} | Out-Null
Write-Host "  [OK] Algorithm4 (Dispatch RL) - port 8004" -ForegroundColor Green

Write-Host ""

# ============================================================
# 5. Start Spring Boot Backend
# ============================================================
Write-Host "[5/7] Starting Spring Boot backend (port 8080)..." -ForegroundColor Yellow
$backendDir = Join-Path $ROOT "backend"
Start-Job -Name "backend-spring" -ArgumentList $backendDir -ScriptBlock {
    param($d); Set-Location $d; mvn spring-boot:run 2>&1 | Out-File "$d\backend.log"
} | Out-Null
Write-Host "  [OK] Backend compiling & starting (may take 1-2 min)..." -ForegroundColor Green
Write-Host ""

# ============================================================
# 6. Start Vue Frontend
# ============================================================
Write-Host "[6/7] Starting Vue frontend (port 3000)..." -ForegroundColor Yellow
$frontendDir = Join-Path $ROOT "frontend"
Start-Job -Name "frontend-vue" -ArgumentList $frontendDir -ScriptBlock {
    param($d); Set-Location $d; npm run dev 2>&1 | Out-File "$d\frontend.log"
} | Out-Null
Write-Host "  [OK] Frontend starting..." -ForegroundColor Green
Write-Host ""

# ============================================================
# 7. Wait for Backend to be Ready
# ============================================================
Write-Host "[7/7] Waiting for services to be ready..." -ForegroundColor Yellow
Write-Host ""
Write-Host "  Waiting for backend (max 120s)..." -ForegroundColor Gray

$backendReady = $false
for ($i = 1; $i -le 60; $i++) {
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/health" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        if ($resp.StatusCode -eq 200) {
            $backendReady = $true
            Write-Host "  [OK] Backend is ready!" -ForegroundColor Green
            break
        }
    } catch {
        Start-Sleep -Seconds 2
    }
}
if (-not $backendReady) {
    Write-Host "  [WARN] Backend may still be starting, check manually later" -ForegroundColor Yellow
}

# Summary
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  All Services Started!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Frontend:    https://localhost:3000" -ForegroundColor White
Write-Host "  Backend:     http://localhost:8080" -ForegroundColor White
Write-Host "  Swagger UI:  http://localhost:8080/swagger-ui/index.html" -ForegroundColor White
Write-Host ""
Write-Host "  Algorithm API Docs:" -ForegroundColor White
Write-Host "    Algo1 (YOLOv5):    http://localhost:8000/docs" -ForegroundColor Gray
Write-Host "    Algo2 (Expert):    http://localhost:8001/docs" -ForegroundColor Gray
Write-Host "    Algo3 (Recovery):  http://localhost:8003/docs" -ForegroundColor Gray
Write-Host "    Algo4 (Dispatch):  http://localhost:8004/docs" -ForegroundColor Gray
Write-Host ""
Write-Host "  Stop all services: .\stop-all.ps1" -ForegroundColor Yellow
Write-Host ""
Read-Host "Press Enter to exit (services continue in background)"

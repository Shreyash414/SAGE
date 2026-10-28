# ==============================================================================
# SAGE -- Institutional Approval and Compliance Platform
# Windows Service Runner: Database -> MinIO -> Backend API -> User Portal -> Admin Panel
# ==============================================================================

[CmdletBinding()]
param (
    [switch]$SkipDocker,     # Skip Docker container verification/startup
    [switch]$Force,          # Force terminate any existing processes on target ports and restart
    [switch]$Stop,           # Stop all running SAGE development services and exit
    [switch]$Status,         # Check and report status of all SAGE services without starting
    [switch]$Help
)

$ErrorActionPreference = "Continue"

if ($Help) {
    Write-Host "SAGE Windows Service Runner (run.ps1)"
    Write-Host "Usage:"
    Write-Host "  .\run.ps1             Start all SAGE services in proper dependency order"
    Write-Host "  .\run.ps1 -Force      Kill any processes on conflicting ports and restart all services"
    Write-Host "  .\run.ps1 -Stop       Terminate all running SAGE background windows and services"
    Write-Host "  .\run.ps1 -Status     Check health status of all SAGE services"
    Write-Host "  .\run.ps1 -SkipDocker Skip Docker Compose startup (use when external DB/MinIO are running)"
    Write-Host "  .\run.ps1 -Help       Display this help message"
    exit 0
}

# ------------------------------------------------------------------------------
# 0. Environment Setup and Configuration
# ------------------------------------------------------------------------------
$ProjectRoot = $PSScriptRoot
Set-Location $ProjectRoot

# Ensure standard Node.js and Git installations are in PATH
$nodePath = "C:\Program Files\nodejs"
if (Test-Path $nodePath) {
    if ($env:PATH -notlike "*$nodePath*") {
        $env:PATH = "$nodePath;$env:PATH"
    }
}
$gitPath = "C:\Program Files\Git\cmd"
if (Test-Path $gitPath) {
    if ($env:PATH -notlike "*$gitPath*") {
        $env:PATH = "$gitPath;$env:PATH"
    }
}

function Import-DotEnv([string]$Path) {
    if (Test-Path $Path) {
        Write-Host "Loading environment configuration from $Path" -ForegroundColor DarkGray
        Get-Content $Path | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
                $idx = $line.IndexOf("=")
                $key = $line.Substring(0, $idx).Trim()
                $val = $line.Substring($idx + 1).Trim()
                if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
                    $val = $val.Substring(1, $val.Length - 2)
                }
                if (-not [string]::IsNullOrEmpty($key)) {
                    [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
                }
            }
        }
    }
}

Import-DotEnv (Join-Path $ProjectRoot ".env")

# STRICT PORTS (Per Architecture & Specification - DO NOT CHANGE)
$DB_HOST            = if ($env:DB_HOST) { $env:DB_HOST } else { "127.0.0.1" }
$DB_PORT            = 5432
$SERVER_PORT        = 8080
$FRONTEND_PORT      = 5173
$ADMIN_PORT         = 5174
$MINIO_PORT         = 9000
$MINIO_CONSOLE_PORT = 9001

$DB_USER            = if ($env:DB_USER) { $env:DB_USER } else { "aicte_app_user" }
$DB_NAME            = if ($env:DB_NAME) { $env:DB_NAME } else { "aicte_platform_db" }
$DB_ENDPOINT        = "{0}:{1}" -f $DB_HOST, $DB_PORT

# ------------------------------------------------------------------------------
# Helper Functions: Network Testing and Process Management
# ------------------------------------------------------------------------------
function Test-PortOpen([string]$HostName, [int]$Port, [int]$TimeoutMs = 1000) {
    try {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        $iar = $tcpClient.BeginConnect($HostName, $Port, $null, $null)
        $wh = $iar.AsyncWaitHandle
        if (-not $wh.WaitOne($TimeoutMs, $false)) {
            $tcpClient.Close()
            return $false
        }
        $tcpClient.EndConnect($iar)
        $tcpClient.Close()
        return $true
    } catch {
        return $false
    }
}

function Test-HttpEndpoint([string]$Url, [int]$TimeoutSec = 3, [string]$ExpectedSubstring = $null) {
    try {
        $probeUrl = $Url.Replace("://localhost:", "://127.0.0.1:")
        
        # Fast exit: check TCP port first to avoid long OS SYN timeouts
        $uri = [System.Uri]$probeUrl
        if (-not (Test-PortOpen $uri.Host $uri.Port 800)) {
            return $false
        }

        $req = [System.Net.HttpWebRequest]::Create($probeUrl)
        $req.Timeout = $TimeoutSec * 1000
        $req.Method = "GET"
        $resp = $null
        try {
            $resp = $req.GetResponse()
            if ($ExpectedSubstring) {
                $stream = $resp.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $body = $reader.ReadToEnd()
                $reader.Close()
                $stream.Close()
                return $body.Contains($ExpectedSubstring)
            }
            return $true
        } finally {
            if ($resp) { $resp.Close() }
        }
    } catch [System.Net.WebException] {
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            if ($ExpectedSubstring) {
                try {
                    $stream = $_.Exception.Response.GetResponseStream()
                    $reader = New-Object System.IO.StreamReader($stream)
                    $body = $reader.ReadToEnd()
                    $reader.Close()
                    $stream.Close()
                    return $body.Contains($ExpectedSubstring)
                } catch {
                    return $false
                }
            }
            return ($status -ge 200 -and $status -lt 500)
        }
        return $false
    } catch {
        return $false
    }
}

function Get-PortListeners([int]$Port) {
    $results = @()
    try {
        $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($conns) {
            foreach ($conn in $conns) {
                $pidVal = [int]$conn.OwningProcess
                if ($pidVal -gt 0) {
                    $proc = Get-Process -Id $pidVal -ErrorAction SilentlyContinue
                    $procName = if ($proc) { $proc.ProcessName } else { "Unknown" }
                    $procPath = if ($proc) { $proc.Path } else { "" }
                    $svc = Get-CimInstance -ClassName Win32_Service -Filter "ProcessId = $pidVal" -ErrorAction SilentlyContinue
                    $svcName = if ($svc) { $svc.Name } else { "" }
                    $results += [PSCustomObject]@{
                        Port        = $Port
                        Pid         = $pidVal
                        ProcessName = $procName
                        Path        = $procPath
                        ServiceName = $svcName
                    }
                }
            }
        }
    } catch {
        try {
            $lines = netstat -ano -p tcp | Select-String ":$Port\s+.*LISTENING\s+(\d+)"
            foreach ($match in $lines) {
                $pidVal = [int]$match.Matches.Groups[1].Value
                if ($pidVal -gt 0) {
                    $proc = Get-Process -Id $pidVal -ErrorAction SilentlyContinue
                    $procName = if ($proc) { $proc.ProcessName } else { "Unknown" }
                    $results += [PSCustomObject]@{
                        Port        = $Port
                        Pid         = $pidVal
                        ProcessName = $procName
                        Path        = if ($proc) { $proc.Path } else { "" }
                        ServiceName = ""
                    }
                }
            }
        } catch {}
    }
    return $results
}

function Resolve-PortConflict([int]$Port, [string]$ServiceName, [scriptblock]$HealthCheckScript) {
    # 1. Fast TCP connection check
    $isOpen = Test-PortOpen "127.0.0.1" $Port 800
    if (-not $isOpen) {
        return $true
    }

    # 2. Check if already healthy
    if ($HealthCheckScript) {
        $isHealthy = & $HealthCheckScript
        if ($isHealthy) {
            Write-Host ("  [OK] $ServiceName is already running and healthy on port $Port.") -ForegroundColor Green
            return $true
        }
    }

    # 3. Port is occupied by an unhealthy or conflicting process
    $listeners = Get-PortListeners $Port
    if (-not $listeners -or $listeners.Count -eq 0) {
        Write-Host ("  [WARN] Port $Port is responding to TCP but listener process could not be identified.") -ForegroundColor Yellow
        return $false
    }

    foreach ($listener in $listeners) {
        $pId = $listener.Pid
        $pName = $listener.ProcessName
        $pPath = $listener.Path
        $svcName = $listener.ServiceName

        # Safety Check: Never terminate Windows system processes
        if ($pId -le 4 -or $pName -in @("System", "Idle", "Registry", "smss", "csrss", "wininit", "services", "lsass", "svchost")) {
            Write-Host ("  [FAIL] Port $Port is occupied by critical Windows system process: $pName (PID $pId). Cannot terminate.") -ForegroundColor Red
            return $false
        }

        # Safety Check: Never terminate Docker Desktop itself
        if ($pName -like "*com.docker.backend*" -or $pName -like "*docker-proxy*" -or $pName -like "*Docker Desktop*") {
            if ($Port -in @(5432, 9000, 9001)) {
                Write-Host ("  [INFO] Port $Port is bound by Docker container ($pName, PID $pId). Reusing container.") -ForegroundColor Cyan
                return $true
            } else {
                Write-Host ("  [FAIL] Port $Port is unexpectedly bound by Docker ($pName, PID $pId).") -ForegroundColor Red
                return $false
            }
        }

        # Handle Windows Services (e.g., OracleXETNSListener on Port 8080)
        if ($svcName -or ($pName -like "*TNSLSNR*" -or $pName -like "*oracle*")) {
            $targetService = if ($svcName) { $svcName } else { "OracleXETNSListener" }
            Write-Host ("  [CONFLICT] Port $Port is occupied by Windows Service '$targetService' ($pName, PID $pId).") -ForegroundColor Yellow
            
            $stopped = $false
            $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
            
            if ($isAdmin) {
                Write-Host ("  Stopping Windows service '$targetService' as Administrator...") -ForegroundColor Cyan
                try {
                    Stop-Service -Name $targetService -Force -ErrorAction Stop
                    $stopped = $true
                } catch {
                    Write-Host ("  [WARN] Stop-Service failed: " + $_.Exception.Message) -ForegroundColor Yellow
                }
            } else {
                Write-Host ("  Administrator privileges required to stop Windows service '$targetService'.") -ForegroundColor Yellow
                Write-Host ("  Requesting elevated permission to stop '$targetService'...") -ForegroundColor Cyan
                try {
                    $elevatedProc = Start-Process powershell.exe -ArgumentList "-NoProfile", "-Command", "Stop-Service -Name '$targetService' -Force" -Verb RunAs -Wait -PassThru -ErrorAction Stop
                    if ($elevatedProc.ExitCode -eq 0) {
                        $stopped = $true
                    }
                } catch {
                    Write-Host ("  [WARN] Elevation request was declined: " + $_.Exception.Message) -ForegroundColor Yellow
                }
            }

            # Wait and recheck
            for ($w = 0; $w -lt 5; $w++) {
                Start-Sleep -Milliseconds 800
                if (-not (Test-PortOpen "127.0.0.1" $Port 500)) {
                    Write-Host ("  [RESOLVED] Port $Port freed after stopping '$targetService'.") -ForegroundColor Green
                    return $true
                }
            }

            if (-not $stopped) {
                Write-Host ("  [FAIL] Could not stop Windows service '$targetService' on port $Port.") -ForegroundColor Red
                Write-Host ("  Please run PowerShell as Administrator and execute: Stop-Service $targetService") -ForegroundColor Yellow
                return $false
            }
        }

        # Ordinary conflicting process (e.g. stale java, rogue node instance)
        Write-Host ("  [CONFLICT] Port $Port is occupied by $pName (PID: $pId). Attempting graceful shutdown...") -ForegroundColor Yellow
        try {
            Stop-Process -Id $pId -ErrorAction SilentlyContinue
        } catch {}

        # Wait up to 2.5 seconds for graceful termination
        $stillAlive = $true
        for ($w = 0; $w -lt 5; $w++) {
            Start-Sleep -Milliseconds 500
            $procCheck = Get-Process -Id $pId -ErrorAction SilentlyContinue
            if (-not $procCheck) {
                $stillAlive = $false
                break
            }
        }

        # Forceful termination if graceful didn't finish
        if ($stillAlive) {
            Write-Host ("  Process PID $pId did not exit gracefully; attempting forceful termination...") -ForegroundColor Yellow
            Start-Process "taskkill.exe" -ArgumentList "/F", "/T", "/PID", $pId -NoNewWindow -Wait -PassThru -ErrorAction SilentlyContinue | Out-Null
            Start-Sleep -Milliseconds 800
        }

        # Recheck port availability
        $isStillOpen = Test-PortOpen "127.0.0.1" $Port 800
        if (-not $isStillOpen) {
            Write-Host ("  [RESOLVED] Port $Port is now free.") -ForegroundColor Green
        } else {
            Write-Host ("  [FAIL] Port $Port remains occupied by PID $pId ($pName).") -ForegroundColor Red
            return $false
        }
    }

    return $true
}

function Terminate-ServiceOnPort([int]$Port, [string]$ServiceName = "") {
    $listeners = Get-PortListeners $Port
    foreach ($listener in $listeners) {
        $pId = $listener.Pid
        $pName = $listener.ProcessName
        if ($pId -gt 4 -and $pName -notin @("System", "Idle", "Registry", "com.docker.backend", "docker-proxy")) {
            Write-Host "  Stopping $ServiceName on port $Port -> $pName (PID: $pId)..." -ForegroundColor Yellow
            try {
                Stop-Process -Id $pId -ErrorAction SilentlyContinue
                Start-Sleep -Milliseconds 500
                $chk = Get-Process -Id $pId -ErrorAction SilentlyContinue
                if ($chk) {
                    Start-Process "taskkill.exe" -ArgumentList "/F", "/T", "/PID", $pId -NoNewWindow -Wait -PassThru -ErrorAction SilentlyContinue | Out-Null
                }
            } catch {}
        }
    }
}

# ------------------------------------------------------------------------------
# Action: -Stop
# ------------------------------------------------------------------------------
if ($Stop) {
    Write-Host ""
    Write-Host "==============================================================================" -ForegroundColor Yellow
    Write-Host "  Initiating Graceful Shutdown of SAGE Development Services...                " -ForegroundColor Yellow
    Write-Host "==============================================================================" -ForegroundColor Yellow

    Write-Host "Stopping SAGE Admin Panel (Port $ADMIN_PORT)..."
    Terminate-ServiceOnPort $ADMIN_PORT "Admin Panel"

    Write-Host "Stopping SAGE User Frontend (Port $FRONTEND_PORT)..."
    Terminate-ServiceOnPort $FRONTEND_PORT "User Frontend"

    Write-Host "Stopping SAGE Backend API (Port $SERVER_PORT)..."
    Terminate-ServiceOnPort $SERVER_PORT "Spring Boot Backend"

    if (-not $SkipDocker -and (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Host "Stopping Docker Compose containers (PostgreSQL and MinIO)..."
        $null = cmd.exe /c "docker compose stop postgres minio 2>&1"
    }

    Write-Host ""
    Write-Host ">>> All SAGE development services stopped cleanly." -ForegroundColor Green
    exit 0
}

# ------------------------------------------------------------------------------
# Action: -Status
# ------------------------------------------------------------------------------
if ($Status) {
    Write-Host ""
    Write-Host "==============================================================================" -ForegroundColor Cyan
    Write-Host "  SAGE -- Service Health Status                                               " -ForegroundColor Cyan
    Write-Host "==============================================================================" -ForegroundColor Cyan

    $dbUp       = Test-PortOpen "127.0.0.1" $DB_PORT
    $minioUp    = Test-HttpEndpoint "http://127.0.0.1:$MINIO_PORT/minio/health/live" 2
    $minioConUp = Test-HttpEndpoint "http://127.0.0.1:$MINIO_CONSOLE_PORT" 2
    $backendUp  = Test-HttpEndpoint "http://127.0.0.1:$SERVER_PORT/api/v1/health" 2 "UP"
    $frontendUp = Test-HttpEndpoint "http://127.0.0.1:$FRONTEND_PORT" 2
    $adminUp    = Test-HttpEndpoint "http://127.0.0.1:$ADMIN_PORT" 2

    $dbLabel = "  PostgreSQL ({0}):" -f $DB_ENDPOINT
    Write-Host ($dbLabel.PadRight(35)) -NoNewline
    if ($dbUp) { Write-Host "[ONLINE]" -ForegroundColor Green } else { Write-Host "[OFFLINE]" -ForegroundColor Red }

    $minioLabel = "  MinIO API (Port {0}):" -f $MINIO_PORT
    Write-Host ($minioLabel.PadRight(35)) -NoNewline
    if ($minioUp) { Write-Host "[ONLINE]" -ForegroundColor Green } else { Write-Host "[OFFLINE]" -ForegroundColor Red }

    $minioConLabel = "  MinIO Console (Port {0}):" -f $MINIO_CONSOLE_PORT
    Write-Host ($minioConLabel.PadRight(35)) -NoNewline
    if ($minioConUp) { Write-Host "[ONLINE]" -ForegroundColor Green } else { Write-Host "[OFFLINE]" -ForegroundColor Red }

    $backendLabel = "  Backend REST API (Port {0}):" -f $SERVER_PORT
    Write-Host ($backendLabel.PadRight(35)) -NoNewline
    if ($backendUp) { Write-Host "[ONLINE]" -ForegroundColor Green } else { Write-Host "[OFFLINE]" -ForegroundColor Red }

    $frontendLabel = "  User Portal (Port {0}):" -f $FRONTEND_PORT
    Write-Host ($frontendLabel.PadRight(35)) -NoNewline
    if ($frontendUp) { Write-Host "[ONLINE]" -ForegroundColor Green } else { Write-Host "[OFFLINE]" -ForegroundColor Red }

    $adminLabel = "  Admin Panel (Port {0}):" -f $ADMIN_PORT
    Write-Host ($adminLabel.PadRight(35)) -NoNewline
    if ($adminUp) { Write-Host "[ONLINE]" -ForegroundColor Green } else { Write-Host "[OFFLINE]" -ForegroundColor Red }

    Write-Host "------------------------------------------------------------------------------" -ForegroundColor DarkGray
    exit 0
}

# ------------------------------------------------------------------------------
# Main Startup Banner
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "==============================================================================" -ForegroundColor Blue
Write-Host "    SAGE -- Institutional Approval and Compliance Platform (Windows Runner)   " -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Blue

# If -Force flag was explicitly passed, terminate existing dev processes first
if ($Force) {
    Write-Host "[-Force flag detected: Clearing processes on development ports...]" -ForegroundColor Yellow
    Terminate-ServiceOnPort $ADMIN_PORT "Admin Panel"
    Terminate-ServiceOnPort $FRONTEND_PORT "User Frontend"
    Terminate-ServiceOnPort $SERVER_PORT "Spring Boot Backend"
}

# ------------------------------------------------------------------------------
# Pre-Flight: Automatic Port Conflict Detection and Resolution
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "[Pre-Flight] Checking required ports (5173, 5174, 8080, 5432, 9000, 9001)..." -ForegroundColor White

$portsToValidate = @(
    @{ Port = $DB_PORT;            Name = "PostgreSQL";       Check = { Test-PortOpen "127.0.0.1" $DB_PORT 800 } },
    @{ Port = $MINIO_PORT;        Name = "MinIO API";        Check = { Test-HttpEndpoint "http://127.0.0.1:$MINIO_PORT/minio/health/live" 2 } },
    @{ Port = $MINIO_CONSOLE_PORT;Name = "MinIO Console";    Check = { Test-HttpEndpoint "http://127.0.0.1:$MINIO_CONSOLE_PORT" 2 } },
    @{ Port = $SERVER_PORT;       Name = "Backend REST API"; Check = { Test-HttpEndpoint "http://127.0.0.1:$SERVER_PORT/api/v1/health" 2 "UP" } },
    @{ Port = $FRONTEND_PORT;     Name = "User Portal UI";   Check = { Test-HttpEndpoint "http://127.0.0.1:$FRONTEND_PORT" 2 } },
    @{ Port = $ADMIN_PORT;        Name = "SAGE Admin Panel"; Check = { Test-HttpEndpoint "http://127.0.0.1:$ADMIN_PORT" 2 } }
)

$allConflictsResolved = $true
foreach ($entry in $portsToValidate) {
    $ok = Resolve-PortConflict $entry.Port $entry.Name $entry.Check
    if (-not $ok) {
        $allConflictsResolved = $false
    }
}

if (-not $allConflictsResolved) {
    Write-Host ""
    Write-Host "  [FAIL] Startup halted due to unresolved port conflicts." -ForegroundColor Red
    Write-Host "  Please review the warnings above and resolve the conflicting processes." -ForegroundColor Yellow
    exit 1
}

# ------------------------------------------------------------------------------
# 1. Start / Verify PostgreSQL Database (Port 5432)
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host ("[1/5] Checking PostgreSQL Database ({0})..." -f $DB_ENDPOINT) -ForegroundColor White

$isDbActive = Test-PortOpen "127.0.0.1" $DB_PORT 1000
if ($isDbActive) {
    Write-Host ("  [OK] PostgreSQL is active and accepting connections on port {0}." -f $DB_PORT) -ForegroundColor Green
} else {
    if ($SkipDocker) {
        Write-Host ("  [FAIL] PostgreSQL is not responding on {0} and -SkipDocker was specified." -f $DB_ENDPOINT) -ForegroundColor Red
        exit 1
    }

    Write-Host "  [WARN] PostgreSQL is not currently running. Starting via Docker Compose..." -ForegroundColor Yellow

    $dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $dockerCmd) {
        Write-Host "  [FAIL] Error: 'docker' command was not found in PATH." -ForegroundColor Red
        Write-Host ("  Please install Docker Desktop or start PostgreSQL manually on port {0}." -f $DB_PORT) -ForegroundColor Yellow
        exit 1
    }

    $null = cmd.exe /c "docker info 2>&1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [WARN] Docker Desktop daemon is not responding." -ForegroundColor Yellow
        $dockerDesktopPath = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
        if (Test-Path $dockerDesktopPath) {
            Write-Host "  Launching Docker Desktop in background. Please wait..." -ForegroundColor Cyan
            Start-Process $dockerDesktopPath
            $daemonReady = $false
            for ($i = 1; $i -le 30; $i++) {
                Start-Sleep -Seconds 1
                $null = cmd.exe /c "docker info 2>&1"
                if ($LASTEXITCODE -eq 0) {
                    $daemonReady = $true
                    break
                }
                Write-Host -NoNewline "."
            }
            Write-Host ""
            if (-not $daemonReady) {
                Write-Host "  [FAIL] Docker Desktop daemon is still initializing. Please start Docker Desktop and re-run." -ForegroundColor Red
                exit 1
            }
        } else {
            Write-Host "  [FAIL] Docker daemon is offline. Please launch Docker Desktop and retry." -ForegroundColor Red
            exit 1
        }
    }

    Write-Host "  Starting PostgreSQL container via 'docker compose up -d postgres'..." -ForegroundColor Cyan
    cmd.exe /c "docker compose up -d postgres"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Failed to launch PostgreSQL container via docker compose." -ForegroundColor Red
        exit 1
    }

    Write-Host -NoNewline "  Waiting for PostgreSQL to be ready..."
    $dbReady = $false
    for ($i = 1; $i -le 30; $i++) {
        if (Test-PortOpen "127.0.0.1" $DB_PORT 1000) {
            $dbReady = $true
            break
        }
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 1
    }
    Write-Host ""

    if ($dbReady) {
        Write-Host ("  [OK] PostgreSQL database is ready on port {0}." -f $DB_PORT) -ForegroundColor Green
    } else {
        Write-Host ("  [FAIL] Unable to connect to PostgreSQL at {0} after 30 seconds." -f $DB_ENDPOINT) -ForegroundColor Red
        exit 1
    }
}

# ------------------------------------------------------------------------------
# 2. Start / Verify MinIO Object Storage (Port 9000 & 9001)
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host ("[2/5] Checking MinIO Object Storage (Port: {0})..." -f $MINIO_PORT) -ForegroundColor White

$isMinioActive = Test-HttpEndpoint "http://127.0.0.1:$MINIO_PORT/minio/health/live" 2
if ($isMinioActive) {
    Write-Host ("  [OK] MinIO Object Storage is active on port {0}." -f $MINIO_PORT) -ForegroundColor Green
} else {
    if ($SkipDocker) {
        Write-Host ("  [WARN] MinIO is not running on port {0} (-SkipDocker active); continuing." -f $MINIO_PORT) -ForegroundColor Yellow
    } else {
        Write-Host "  [WARN] MinIO is not running. Starting via Docker Compose..." -ForegroundColor Yellow
        $null = cmd.exe /c "docker compose up -d minio 2>&1"

        Write-Host -NoNewline "  Waiting for MinIO to become ready..."
        $minioReady = $false
        for ($i = 1; $i -le 25; $i++) {
            if (Test-HttpEndpoint "http://127.0.0.1:$MINIO_PORT/minio/health/live" 2) {
                $minioReady = $true
                break
            }
            Write-Host -NoNewline "."
            Start-Sleep -Seconds 1
        }
        Write-Host ""

        if ($minioReady) {
            Write-Host ("  [OK] MinIO Object Storage is now ready on port {0}." -f $MINIO_PORT) -ForegroundColor Green
        } else {
            Write-Host "  [WARN] MinIO startup timed out; proceeding." -ForegroundColor Yellow
        }
    }
}

# ------------------------------------------------------------------------------
# 3. Start Spring Boot Backend API (Port 8080)
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host ("[3/5] Checking Spring Boot Backend API (Port: {0})..." -f $SERVER_PORT) -ForegroundColor White

$isBackendRunning = Test-HttpEndpoint "http://127.0.0.1:$SERVER_PORT/api/v1/health" 3 "UP"

if ($isBackendRunning) {
    Write-Host ("  [OK] Spring Boot Backend API is already running and healthy on port {0}. (Reusing)" -f $SERVER_PORT) -ForegroundColor Green
} else {
    $mvnwCmd = Join-Path $ProjectRoot "mvnw.cmd"
    if (-not (Test-Path $mvnwCmd)) {
        Write-Host "  [FAIL] Maven wrapper '$mvnwCmd' was not found." -ForegroundColor Red
        exit 1
    }

    Write-Host "  Launching Spring Boot Backend in a dedicated PowerShell window..." -ForegroundColor Cyan

    $backendTitle = "SAGE Backend API (Port {0})" -f $SERVER_PORT
    $backendCmd = "`$Host.UI.RawUI.WindowTitle = '$backendTitle'; Write-Host 'Starting SAGE Spring Boot Backend API (Port $SERVER_PORT)...' -ForegroundColor Yellow; Set-Location '$ProjectRoot'; .\mvnw.cmd spring-boot:run"

    Start-Process powershell.exe -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $backendCmd -WorkingDirectory $ProjectRoot

    Write-Host -NoNewline ("  Waiting for Backend API to become healthy on http://localhost:{0}/api/v1/health..." -f $SERVER_PORT)
    $backendReady = $false
    for ($i = 1; $i -le 60; $i++) {
        if (Test-HttpEndpoint "http://127.0.0.1:$SERVER_PORT/api/v1/health" 2 "UP") {
            $backendReady = $true
            break
        }
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 1
    }
    Write-Host ""

    if ($backendReady) {
        Write-Host ("  [OK] Backend is healthy and running on http://localhost:{0}" -f $SERVER_PORT) -ForegroundColor Green
    } else {
        Write-Host ("  [FAIL] Backend health check timed out on http://localhost:{0}/api/v1/health" -f $SERVER_PORT) -ForegroundColor Red
        Write-Host "  Please inspect the 'SAGE Backend API' window for errors." -ForegroundColor Yellow
        exit 1
    }
}

# ------------------------------------------------------------------------------
# 4. Start Vite React User Frontend (Port 5173)
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host ("[4/5] Checking Vite React User Frontend (Port: {0})..." -f $FRONTEND_PORT) -ForegroundColor White

$isFrontendRunning = Test-HttpEndpoint "http://127.0.0.1:$FRONTEND_PORT" 2

if ($isFrontendRunning) {
    Write-Host ("  [OK] User Frontend is already running on http://localhost:{0}. (Reusing)" -f $FRONTEND_PORT) -ForegroundColor Green
} else {
    $frontendDir = Join-Path $ProjectRoot "frontend"
    if (-not (Test-Path (Join-Path $frontendDir "node_modules"))) {
        Write-Host "  Installing user frontend dependencies (npm install)..." -ForegroundColor Cyan
        Start-Process cmd.exe -ArgumentList "/c", "npm install" -WorkingDirectory $frontendDir -Wait -NoNewWindow
    }

    Write-Host "  Launching User Frontend in a dedicated PowerShell window..." -ForegroundColor Cyan

    $frontendTitle = "SAGE User Frontend (Port {0})" -f $FRONTEND_PORT
    $frontendCmd = "`$Host.UI.RawUI.WindowTitle = '$frontendTitle'; Write-Host 'Starting SAGE User Frontend (Port $FRONTEND_PORT)...' -ForegroundColor Green; Set-Location '$frontendDir'; npm.cmd run dev -- --host 0.0.0.0 --port $FRONTEND_PORT"

    Start-Process powershell.exe -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $frontendCmd -WorkingDirectory $frontendDir

    Write-Host -NoNewline ("  Waiting for User Frontend to respond on http://localhost:{0}..." -f $FRONTEND_PORT)
    $frontendReady = $false
    for ($i = 1; $i -le 25; $i++) {
        if (Test-HttpEndpoint "http://127.0.0.1:$FRONTEND_PORT" 2) {
            $frontendReady = $true
            break
        }
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 1
    }
    Write-Host ""

    if ($frontendReady) {
        Write-Host ("  [OK] User Frontend is accessible on http://localhost:{0}" -f $FRONTEND_PORT) -ForegroundColor Green
    } else {
        Write-Host "  [WARN] User Frontend startup is taking longer than expected. Check the 'SAGE User Frontend' window." -ForegroundColor Yellow
    }
}

# ------------------------------------------------------------------------------
# 5. Start SAGE Admin Panel (Port 5174)
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host ("[5/5] Checking SAGE Admin Panel (Port: {0})..." -f $ADMIN_PORT) -ForegroundColor White

$isAdminRunning = Test-HttpEndpoint "http://127.0.0.1:$ADMIN_PORT" 2

if ($isAdminRunning) {
    Write-Host ("  [OK] Admin Panel is already running on http://localhost:{0}. (Reusing)" -f $ADMIN_PORT) -ForegroundColor Green
} else {
    $adminDir = Join-Path $ProjectRoot "admin"
    if (-not (Test-Path (Join-Path $adminDir "node_modules"))) {
        Write-Host "  Installing admin panel dependencies (npm install)..." -ForegroundColor Cyan
        Start-Process cmd.exe -ArgumentList "/c", "npm install" -WorkingDirectory $adminDir -Wait -NoNewWindow
    }

    Write-Host "  Launching Admin Panel in a dedicated PowerShell window..." -ForegroundColor Cyan

    $adminTitle = "SAGE Admin Panel (Port {0})" -f $ADMIN_PORT
    $adminCmd = "`$Host.UI.RawUI.WindowTitle = '$adminTitle'; Write-Host 'Starting SAGE Admin Panel (Port $ADMIN_PORT)...' -ForegroundColor Magenta; Set-Location '$adminDir'; npm.cmd run dev -- --host 0.0.0.0 --port $ADMIN_PORT --strictPort"

    Start-Process powershell.exe -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $adminCmd -WorkingDirectory $adminDir

    Write-Host -NoNewline ("  Waiting for Admin Panel to respond on http://localhost:{0}..." -f $ADMIN_PORT)
    $adminReady = $false
    for ($i = 1; $i -le 25; $i++) {
        if (Test-HttpEndpoint "http://127.0.0.1:$ADMIN_PORT" 2) {
            $adminReady = $true
            break
        }
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 1
    }
    Write-Host ""

    if ($adminReady) {
        Write-Host ("  [OK] Admin Panel is accessible on http://localhost:{0}" -f $ADMIN_PORT) -ForegroundColor Green
    } else {
        Write-Host "  [WARN] Admin Panel startup is taking longer than expected. Check the 'SAGE Admin Panel' window." -ForegroundColor Yellow
    }
}

# ------------------------------------------------------------------------------
# 6. Comprehensive Final Health Audit (All 6 Required Ports)
# ------------------------------------------------------------------------------
Write-Host ""
Write-Host "[Final Audit] Verifying all required SAGE services..." -ForegroundColor White

$hPostgres     = Test-PortOpen "127.0.0.1" $DB_PORT 1000
$hMinioLive    = Test-HttpEndpoint "http://127.0.0.1:$MINIO_PORT/minio/health/live" 3
$hMinioConsole = Test-HttpEndpoint "http://127.0.0.1:$MINIO_CONSOLE_PORT" 3
$hBackend      = Test-HttpEndpoint "http://127.0.0.1:$SERVER_PORT/api/v1/health" 3 "UP"
$hFrontend     = Test-HttpEndpoint "http://127.0.0.1:$FRONTEND_PORT" 3
$hAdmin        = Test-HttpEndpoint "http://127.0.0.1:$ADMIN_PORT" 3

$allHealthy = $hPostgres -and $hMinioLive -and $hMinioConsole -and $hBackend -and $hFrontend -and $hAdmin

if ($allHealthy) {
    Write-Host ""
    Write-Host "==============================================================================" -ForegroundColor Green
    Write-Host "    SAGE -- Institutional Approval and Compliance Platform is FULLY OPERATIONAL " -ForegroundColor Green
    Write-Host "==============================================================================" -ForegroundColor Green
    Write-Host " - User Portal UI:       " -NoNewline; Write-Host ("http://localhost:{0}" -f $FRONTEND_PORT) -ForegroundColor Cyan
    Write-Host " - SAGE Admin Panel:     " -NoNewline; Write-Host ("http://localhost:{0}" -f $ADMIN_PORT) -ForegroundColor Cyan
    Write-Host " - Backend REST API:     " -NoNewline; Write-Host ("http://localhost:{0}" -f $SERVER_PORT) -ForegroundColor Cyan
    Write-Host " - MinIO Object Storage: " -NoNewline; Write-Host ("http://localhost:{0} (Console: {1})" -f $MINIO_PORT, $MINIO_CONSOLE_PORT) -ForegroundColor Cyan
    Write-Host " - Swagger OpenAPI UI:   " -NoNewline; Write-Host ("http://localhost:{0}/swagger-ui.html" -f $SERVER_PORT) -ForegroundColor Cyan
    Write-Host " - Health Endpoint:      " -NoNewline; Write-Host ("http://localhost:{0}/api/v1/health" -f $SERVER_PORT) -ForegroundColor Cyan
    Write-Host " - PostgreSQL Database:  " -NoNewline; Write-Host ("{0}/{1} (User: {2})" -f $DB_ENDPOINT, $DB_NAME, $DB_USER) -ForegroundColor Cyan
    Write-Host "------------------------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host " All services passed health checks and are accepting traffic." -ForegroundColor Green
    Write-Host " To stop all services cleanly at any time, execute:" -ForegroundColor DarkGray
    Write-Host "   .\run.ps1 -Stop" -ForegroundColor Cyan
    Write-Host "------------------------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "==============================================================================" -ForegroundColor Red
    Write-Host "    SAGE Startup Verification Failed: One or more services are not healthy    " -ForegroundColor Red
    Write-Host "==============================================================================" -ForegroundColor Red
    Write-Host ("  - PostgreSQL (5432):     " + (if ($hPostgres) { "[ONLINE]" } else { "[OFFLINE]" })) -ForegroundColor (if ($hPostgres) { "Green" } else { "Red" })
    Write-Host ("  - MinIO API (9000):      " + (if ($hMinioLive) { "[ONLINE]" } else { "[OFFLINE]" })) -ForegroundColor (if ($hMinioLive) { "Green" } else { "Red" })
    Write-Host ("  - MinIO Console (9001):  " + (if ($hMinioConsole) { "[ONLINE]" } else { "[OFFLINE]" })) -ForegroundColor (if ($hMinioConsole) { "Green" } else { "Red" })
    Write-Host ("  - Backend API (8080):    " + (if ($hBackend) { "[ONLINE]" } else { "[OFFLINE]" })) -ForegroundColor (if ($hBackend) { "Green" } else { "Red" })
    Write-Host ("  - User Frontend (5173):  " + (if ($hFrontend) { "[ONLINE]" } else { "[OFFLINE]" })) -ForegroundColor (if ($hFrontend) { "Green" } else { "Red" })
    Write-Host ("  - Admin Panel (5174):    " + (if ($hAdmin) { "[ONLINE]" } else { "[OFFLINE]" })) -ForegroundColor (if ($hAdmin) { "Green" } else { "Red" })
    Write-Host "------------------------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host " Platform is NOT fully operational. Please review the failed services above." -ForegroundColor Red
    exit 1
}

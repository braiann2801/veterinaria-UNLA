# Barrera de calidad del proyecto (AGENTS.md 4.4 / 5).
#
# Cualquier falla detiene el flujo por completo: no se aplican cambios, no se
# commitea y se reporta el error. Los pasos son acumulativos, en el orden que
# mas rapido detecta el problema: tipos antes que bundling, y backend antes que
# cerrar la tarea.

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root

function Stop-Harness([string]$motivo) {
    Write-Host "[ERROR FATAL] $motivo" -ForegroundColor Red
    Pop-Location
    exit 1
}

Write-Host "=== [HARNESS] 1/3 Chequeo de tipos del frontend (tsc --noEmit) ===" -ForegroundColor Cyan

# `next build` dice "Skipping validation of types" con este proyecto, asi que
# el build por si solo NO alcanza para detectar un error de TypeScript. Sin
# este paso la barrera dejaria pasar exactamente el fallo que dice cubrir.
npx tsc --noEmit
if ($LASTEXITCODE -ne 0) {
    Stop-Harness "Fallaron los tipos de TypeScript. Flujo detenido."
}

Write-Host "=== [HARNESS] 2/3 Verificando integridad de Frontend (Webpack Build) ===" -ForegroundColor Cyan

npx next build --webpack
if ($LASTEXITCODE -ne 0) {
    Stop-Harness "Frontend no compila o fallan tipos. Flujo detenido."
}

Write-Host "=== [HARNESS] 3/3 Verificando Backend Spring Boot ===" -ForegroundColor Cyan

if (-not (Test-Path "backend/pom.xml")) {
    Write-Host "[AVISO] Modulo backend aun no inicializado." -ForegroundColor Yellow
    Pop-Location
    exit 0
}

Push-Location backend

# Prioriza mvnw.cmd (wrapper local) y si no existe usa mvn global
if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd clean test
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    mvn clean test
} else {
    Pop-Location
    Stop-Harness "No se encontro mvnw.cmd ni mvn en el sistema."
}

if ($LASTEXITCODE -ne 0) {
    Pop-Location
    Stop-Harness "Tests unitarios de Java fallaron. Flujo detenido."
}

Pop-Location
Write-Host "=== [HARNESS] Todos los controles pasaron exitosamente. ===" -ForegroundColor Green
Pop-Location
exit 0
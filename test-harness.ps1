Write-Host "=== [HARNESS] 1/2 Verificando integridad de Frontend (TypeScript / Build) ===" -ForegroundColor Cyan

# Verificación de compilación / tipos sin emitir basura
npm run build --webpack
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR FATAL] Frontend no compila o fallan tipos. Flujo detenido." -ForegroundColor Red
    exit 1
}

Write-Host "=== [HARNESS] 2/2 Verificando Backend Spring Boot ===" -ForegroundColor Cyan
if (Test-Path "backend/pom.xml") {
    Push-Location backend
    mvn clean test
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR FATAL] Tests unitarios de Java fallaron. Flujo detenido." -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Pop-Location
} else {
    Write-Host "[AVISO] Módulo backend aún no inicializado, saltando pruebas de Java..." -ForegroundColor Yellow
}

Write-Host "=== [HARNESS] Todos los controles pasaron exitosamente. ===" -ForegroundColor Green
exit 0
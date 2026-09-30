Write-Host "=== [HARNESS] 1/2 Verificando integridad de Frontend (Webpack Build) ===" -ForegroundColor Cyan

# Compilación limpia del frontend
npx next build --webpack
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR FATAL] Frontend no compila o fallan tipos. Flujo detenido." -ForegroundColor Red
    exit 1
}

Write-Host "=== [HARNESS] 2/2 Verificando Backend Spring Boot ===" -ForegroundColor Cyan
if (Test-Path "backend/pom.xml") {
    Push-Location backend

    # Prioriza mvnw.cmd (wrapper local) y si no existe usa mvn global
    if (Test-Path ".\mvnw.cmd") {
        .\mvnw.cmd clean test
    } elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
        mvn clean test
    } else {
        Write-Host "[ERROR FATAL] No se encontró mvnw.cmd ni mvn en el sistema." -ForegroundColor Red
        Pop-Location
        exit 1
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR FATAL] Tests unitarios de Java fallaron. Flujo detenido." -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Pop-Location
} else {
    Write-Host "[AVISO] Modulo backend aun no inicializado." -ForegroundColor Yellow
}

Write-Host "=== [HARNESS] Todos los controles pasaron exitosamente. ===" -ForegroundColor Green
exit 0
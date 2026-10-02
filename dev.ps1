# dev.ps1 - Inicia Backend y Frontend en paralelo
Write-Host "Iniciando Sistema Veterinario MVP 1..." -ForegroundColor Cyan

# 1. Levantar Spring Boot en una ventana nueva
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd backend; Write-Host 'Levantando Backend Spring Boot...' -ForegroundColor Green; .\mvnw.cmd spring-boot:run"

# 2. Levantar Next.js en la ventana actual
Write-Host "Levantando Frontend Next.js..." -ForegroundColor Yellow
npm run dev
# dev.ps1 - Inicia Backend y Frontend en paralelo
Write-Host "Iniciando Sistema Veterinario MVP 1..." -ForegroundColor Cyan

# 1. Cargar variables desde .env (raiz del proyecto o backend) al entorno actual
$rutaEnv = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path -LiteralPath $rutaEnv)) {
    $rutaEnv = Join-Path $PSScriptRoot "backend\.env"
}
if (Test-Path -LiteralPath $rutaEnv) {
    Write-Host "Cargando variables de entorno desde $rutaEnv..." -ForegroundColor DarkGray
    foreach ($linea in Get-Content -LiteralPath $rutaEnv) {
        $texto = $linea.Trim()
        # Ignorar lineas vacias y comentarios
        if ($texto -eq "" -or $texto.StartsWith("#")) { continue }
        $indice = $texto.IndexOf("=")
        if ($indice -lt 1) { continue }
        $clave = $texto.Substring(0, $indice).Trim().TrimStart([char]0xFEFF)
        $valor = $texto.Substring($indice + 1).Trim()
        if ($clave -eq "") { continue }
        # Quitar comillas envolventes ("..." o '...')
        if ($valor.Length -ge 2) {
            $primero = $valor[0]
            $ultimo = $valor[$valor.Length - 1]
            if (($primero -eq '"' -and $ultimo -eq '"') -or ($primero -eq "'" -and $ultimo -eq "'")) {
                $valor = $valor.Substring(1, $valor.Length - 2)
            }
        }
        Set-Item -Path "Env:$clave" -Value $valor
    }
} else {
    Write-Host "No se encontro archivo .env en la raiz ni en backend." -ForegroundColor Yellow
}

# 2. Levantar Spring Boot en una ventana nueva (hereda las variables de entorno)
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd backend; Write-Host 'Levantando Backend Spring Boot...' -ForegroundColor Green; .\mvnw.cmd spring-boot:run"

# 3. Levantar Next.js en la ventana actual
Write-Host "Levantando Frontend Next.js..." -ForegroundColor Yellow
npm run dev
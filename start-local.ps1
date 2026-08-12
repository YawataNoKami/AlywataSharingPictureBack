# Démarre MongoDB (portable, sans installeur) puis le backend Spring Boot,
# avec des identifiants et secrets de développement local uniquement.
# Usage : depuis PowerShell, à la racine du projet backend :
#   .\start-local.ps1
#
# Ce script est un raccourci de confort pour le développement local sans
# Docker. Il ne doit jamais être utilisé pour un déploiement réel (les
# secrets ci-dessous sont des valeurs de test, pas des secrets de prod).

$ErrorActionPreference = "Stop"

$mongoDir = "E:\DEV APP MTG\mongodb-local"
$mongodExe = Get-ChildItem -Path $mongoDir -Recurse -Filter "mongod.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
$dataPath = Join-Path $mongoDir "data"
$logPath = Join-Path $mongoDir "logs\mongod.log"

if (-not $mongodExe) {
    Write-Error "mongod.exe introuvable dans $mongoDir. Voir le README (section 'Installer MongoDB en local sans Docker') pour l'installer."
    exit 1
}

# Démarre MongoDB uniquement s'il n'écoute pas déjà sur le port 27017.
$mongoAlreadyRunning = Get-NetTCPConnection -LocalPort 27017 -State Listen -ErrorAction SilentlyContinue
if ($mongoAlreadyRunning) {
    Write-Output "MongoDB écoute déjà sur le port 27017, pas de redémarrage."
} else {
    Write-Output "Démarrage de MongoDB (dbpath: $dataPath)..."
    New-Item -ItemType Directory -Force -Path $dataPath | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path $logPath) | Out-Null
    Start-Process -FilePath $mongodExe.FullName -ArgumentList "--dbpath", "`"$dataPath`"", "--logpath", "`"$logPath`"", "--port", "27017" -WindowStyle Hidden
    Start-Sleep -Seconds 3
}

$jar = Get-ChildItem -Path "$PSScriptRoot\target" -Filter "photo-app-backend-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) {
    Write-Output "Aucun jar trouvé dans target/, build en cours (mvn clean package -DskipTests)..."
    & mvn clean package -DskipTests
    $jar = Get-ChildItem -Path "$PSScriptRoot\target" -Filter "photo-app-backend-*.jar" | Select-Object -First 1
}

$env:ADMIN_USERNAME = "admin"
$env:ADMIN_PASSWORD = "TestPassword123!"
$env:PARTNER_USERNAME = "partner"
$env:PARTNER_PASSWORD = "TestPassword456!"
$env:JWT_SECRET = "local-dev-jwt-secret-key-min-256-bits-xxxxxxxxxxxxxxxxxxxx"
$env:ENCRYPTION_KEY = "0123456789abcdef0123456789abcdef"
$env:MONGO_URI = "mongodb://localhost:27017/photoapp"
$env:FRONTEND_URL = "http://localhost:4200"

Write-Output "Démarrage du backend sur http://localhost:8080 (identifiants : admin/TestPassword123! et partner/TestPassword456!)..."
Write-Output "Ctrl+C pour arrêter."
java -jar $jar.FullName

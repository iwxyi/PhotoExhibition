@echo off
setlocal DisableDelayedExpansion
title PhotoExhibition Production Deploy

REM Optional instance settings: remove REM to enable an override.
REM Leave commented to use application defaults or inherited environment values.
REM set "SERVER_PORT=6061"
REM set "PHOTO_DB_HOST=127.0.0.1"
REM set "PHOTO_DB_PORT=3306"
REM set "PHOTO_DB_NAME=photo2"
REM set "PHOTO_DB_USER=photo_user_2"
REM set "PHOTO_DB_PASSWORD=your-password"
REM set "PHOTO_SCAN_BASE_PATH=D:\PhotoExhibition2\data\photos"
REM set "APP_SECURITY_CONFIG_KEY_FILE=D:\PhotoExhibition2\secrets\config.key"
REM set "JWT_SECRET=replace-with-an-independent-long-random-secret"

set "APP_ROOT=%~dp0"
set "FRONTEND_DIR=%APP_ROOT%frontend"
set "BACKEND_DIR=%APP_ROOT%backend"
set "JAR_FILE=%BACKEND_DIR%\target\photo-exhibition-backend-1.0.0.jar"

REM Keep machine-specific settings in this ignored file to avoid pull conflicts.
if exist "%APP_ROOT%start.local.bat" (
    call "%APP_ROOT%start.local.bat"
    if errorlevel 1 goto failed
)
if not defined SPRING_REDIS_HOST set "SPRING_REDIS_HOST=disabled"

echo ========================================
echo PhotoExhibition Production Deploy
echo ========================================
echo.
echo [1/4] Pulling latest code...
cd /d "%APP_ROOT%"
if errorlevel 1 goto failed
git pull --ff-only
if errorlevel 1 (
    echo WARNING: Git pull failed. Continuing with current local code.
)

echo.
echo [2/4] Building frontend...
cd /d "%FRONTEND_DIR%"
if errorlevel 1 goto failed
if not exist "package.json" (
    echo ERROR: frontend\package.json not found.
    goto failed
)
if not exist "node_modules" (
    if exist "package-lock.json" (
        call npm ci
    ) else (
        call npm install
    )
    if errorlevel 1 goto failed
)
call npm run build
if errorlevel 1 goto failed

echo.
echo [3/4] Building backend...
cd /d "%BACKEND_DIR%"
if errorlevel 1 goto failed
if not exist "pom.xml" (
    echo ERROR: backend\pom.xml not found.
    goto failed
)
call mvn -DskipTests clean package
if errorlevel 1 goto failed
if not exist "%JAR_FILE%" (
    echo ERROR: Backend JAR not found: %JAR_FILE%
    goto failed
)

echo.
echo [4/4] Starting backend...
echo Frontend build is ready in frontend\dist for your web server.
echo Backend port: application default, unless overridden above.
if defined SERVER_PORT echo Configured backend port: %SERVER_PORT%
if defined PHOTO_DB_NAME echo Configured database: %PHOTO_DB_NAME%
echo.
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -jar "%JAR_FILE%"
if errorlevel 1 goto failed
echo.
echo Backend process stopped.
pause
exit /b 0

:failed
echo.
echo ERROR: Deployment or backend startup failed. Check the output above.
pause
exit /b 1

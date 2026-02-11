@echo off
REM Stop script for JasperReports Server (Windows)

echo Stopping JasperReports Server...

REM Check if container is running
docker ps --filter "name=jasper-report-server" --filter "status=running" | findstr jasper-report-server >nul
IF %ERRORLEVEL%==0 (
    docker-compose down
    echo JasperReports Server stopped successfully!
    echo Data is preserved in .\data directory
) ELSE (
    echo Container is not running
)

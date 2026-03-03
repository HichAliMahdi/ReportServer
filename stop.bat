@echo off
REM Stop script for ReportServer (Windows)

echo Stopping ReportServer...

REM Check if container is running
docker ps --filter "name=report-server" --filter "status=running" | findstr report-server >nul
IF %ERRORLEVEL%==0 (
    docker-compose down
    echo ReportServer stopped successfully!
    echo Data is preserved in .\data directory
) ELSE (
    echo Container is not running
)

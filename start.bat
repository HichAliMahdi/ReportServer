@echo off
REM Quick start script for ReportServer (Windows)
REM Usage: start.bat [dev|prod]

SET MODE=%1
IF "%MODE%"=="" SET MODE=dev

echo Starting ReportServer in %MODE% mode...

REM Create data directory if it doesn't exist
IF NOT EXIST data (
    echo Creating data directory...
    mkdir data\reports
    mkdir data\datasource_files
)

REM Start the application
IF "%MODE%"=="prod" (
    echo Starting in PRODUCTION mode...
    docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
) ELSE (
    echo Starting in DEVELOPMENT mode...
    docker-compose up -d
)

echo Waiting for application to start...
timeout /t 5 /nobreak >nul

REM Check container status
docker ps --filter "name=report-server" --filter "status=running" | findstr report-server >nul
IF %ERRORLEVEL%==0 (
    echo Container is running!
    echo.
    echo ReportServer is starting!
    echo Access the application at: http://localhost:8080
    echo Default credentials: admin / admin123
    echo.
    echo Useful commands:
    echo   View logs:        docker-compose logs -f
    echo   Stop:             stop.bat
    echo   Restart:          docker-compose restart
    echo.
) ELSE (
    echo Failed to start container!
    echo Check logs: docker-compose logs
    exit /b 1
)

#!/bin/bash
# Quick start script for ReportServer
# Usage: ./start.sh [dev|prod]

set -e

MODE=${1:-dev}

echo "🚀 Starting ReportServer in $MODE mode..."

# Create data directory if it doesn't exist
if [ ! -d "data" ]; then
    echo "📁 Creating data directory..."
    mkdir -p data/reports data/datasource_files
fi

# Start the application
if [ "$MODE" = "prod" ]; then
    echo "🏭 Starting in PRODUCTION mode..."
    docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
else
    echo "🛠️  Starting in DEVELOPMENT mode..."
    docker-compose up -d
fi

# Wait for health check
echo "⏳ Waiting for application to be healthy..."
sleep 5

# Check container status
if docker ps --filter "name=report-server" --filter "status=running" | grep -q report-server; then
    echo "✅ Container is running!"
    
    # Wait for health check
    for i in {1..30}; do
        if docker inspect report-server --format='{{.State.Health.Status}}' 2>/dev/null | grep -q "healthy"; then
            echo "✅ Application is healthy!"
            echo ""
            echo "🎉 ReportServer is ready!"
            echo "🌐 Access the application at: http://localhost:8080"
            echo "👤 Default credentials: admin / admin123"
            echo ""
            echo "📋 Useful commands:"
            echo "  View logs:        docker-compose logs -f"
            echo "  Stop:             docker-compose down"
            echo "  Restart:          docker-compose restart"
            echo ""
            exit 0
        fi
        echo -n "."
        sleep 2
    done
    
    echo ""
    echo "⚠️  Application started but health check is pending..."
    echo "🔍 Check logs: docker-compose logs -f"
else
    echo "❌ Failed to start container!"
    echo "🔍 Check logs: docker-compose logs"
    exit 1
fi

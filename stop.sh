#!/bin/bash
# Stop script for JasperReports Server

set -e

echo "🛑 Stopping JasperReports Server..."

# Check if container is running
if docker ps --filter "name=jasper-report-server" --filter "status=running" | grep -q jasper-report-server; then
    docker-compose down
    echo "✅ JasperReports Server stopped successfully!"
    echo "💾 Data is preserved in ./data directory"
else
    echo "ℹ️  Container is not running"
fi

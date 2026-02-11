# Docker Quick Start Guide

This guide will help you quickly deploy the JasperReports Server using Docker.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose 2.0+ (usually bundled with Docker Desktop)

## Quick Start (3 steps)

### 1. Start the Application

```bash
docker-compose up -d
```

This command will:
- Build the Docker image (first time only, ~2-3 minutes)
- Create and start the container
- Map port 8080 to your host
- Create data volumes for persistence

### 2. Access the Application

Open your browser and navigate to:
```
http://localhost:8080
```

### 3. Login

Default credentials:
- **Username**: `admin`
- **Password**: `admin123`

⚠️ **Important**: You'll be prompted to change the password on first login.

## Common Commands

### View Logs
```bash
docker-compose logs -f reportserver
```

### Stop the Application
```bash
docker-compose down
```

### Restart the Application
```bash
docker-compose restart
```

### Rebuild After Code Changes
```bash
docker-compose up -d --build
```

### Check Container Status
```bash
docker-compose ps
```

### Access Container Shell
```bash
docker-compose exec reportserver sh
```

## Data Persistence

All data is stored in the `./data` directory:
- `data/reportserver.*.db` - H2 database files
- `data/reports/` - Uploaded JRXML templates
- `data/datasource_files/` - Uploaded CSV/XML/JSON files

**Backup**: Simply copy the entire `data/` directory to back up your reports and configurations.

## Customization

### Change Port

Edit `docker-compose.yml`:
```yaml
ports:
  - "9090:8080"  # Change 9090 to your desired port
```

Then restart:
```bash
docker-compose up -d
```

### Increase Memory (for large reports)

Edit `docker-compose.yml`:
```yaml
environment:
  - JAVA_OPTS=-Xmx2g -Xms1g  # 2GB max, 1GB min
```

Then restart:
```bash
docker-compose restart
```

### Change File Upload Limits

Edit `docker-compose.yml`:
```yaml
environment:
  - SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=100MB
  - SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=100MB
```

Then restart:
```bash
docker-compose restart
```

## Production Deployment

For production, consider:

1. **Use environment-specific compose file**:
```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

2. **Set resource limits**:
```yaml
services:
  reportserver:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 512M
```

3. **Enable HTTPS**: Use a reverse proxy like Nginx or Traefik

4. **Regular backups**: Automate `./data` directory backups

5. **Monitor logs**:
```bash
docker-compose logs --tail=100 -f reportserver
```

## Troubleshooting

### Container won't start

Check logs:
```bash
docker-compose logs reportserver
```

### Port already in use

Change the port in `docker-compose.yml` or stop the conflicting service:
```bash
# Find what's using port 8080
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows

# Kill the process or change the port in docker-compose.yml
```

### Out of memory errors

Increase memory allocation in `docker-compose.yml`:
```yaml
environment:
  - JAVA_OPTS=-Xmx2g -Xms1g
```

### Permission issues with data directory

Fix permissions:
```bash
sudo chown -R 1000:1000 ./data
```

### Health check failing

Wait 60 seconds for the application to fully start, then check:
```bash
docker inspect jasper-report-server --format='{{.State.Health.Status}}'
```

If still unhealthy:
```bash
docker-compose logs reportserver
```

## Need Help?

- Check the application logs: `docker-compose logs -f`
- Review the main README.md for detailed documentation
- Ensure all prerequisites are installed
- Verify Docker and Docker Compose versions

## Next Steps

After deployment:
1. Change the admin password (required on first login)
2. Create user accounts
3. Upload JRXML templates
4. Configure datasources
5. Generate your first report

Enjoy using JasperReports Server! 🎉

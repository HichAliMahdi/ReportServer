# Quick Reference - Security Configuration

## 🔐 Critical Environment Variables for Production

```bash
# 1. Password Encryption - MUST SET
DATASOURCE_ENCRYPTION_KEY="$(openssl rand -base64 32)"

# 2. JWT Authentication - MUST SET
JWT_SECRET="$(openssl rand -base64 48)"

# 3. H2 Console - Keep disabled (default)
SPRING_H2_CONSOLE_ENABLED="false"

# 4. Optional - Token expiration in milliseconds (default 1 hour)
JWT_EXPIRATION_MS="3600000"

# 5. Optional - Custom upload directory
REPORT_UPLOAD_DIR="/data/reports"

# 6. Optional - Allowed JRXML classes
JRXML_ALLOWED_CLASSES="java.lang.String,java.lang.Integer,java.lang.Double,java.lang.Boolean,java.lang.Math"
```

## 📋 Deployment Template

### Docker
```bash
docker run \
  -e DATASOURCE_ENCRYPTION_KEY="$ENCRYPTION_KEY" \
  -e JWT_SECRET="$JWT_SECRET" \
  -e SPRING_H2_CONSOLE_ENABLED=false \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://db:5432/reportserver" \
  -e SPRING_DATASOURCE_USERNAME="reportserver" \
  -e SPRING_DATASOURCE_PASSWORD="$DB_PASS" \
  -e REPORT_UPLOAD_DIR=/data/reports \
  -v /persistent-storage:/data \
  -p 8080:8080 \
  reportserver:latest
```

### Kubernetes
```yaml
env:
  - name: DATASOURCE_ENCRYPTION_KEY
    valueFrom:
      secretKeyRef:
        name: reportserver-secrets
        key: encryption-key
  - name: JWT_SECRET
    valueFrom:
      secretKeyRef:
        name: reportserver-secrets
        key: jwt-secret
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: db-secrets
        key: password
```

## 🔑 How to Generate Keys

```bash
# 32-byte encryption key for passwords
openssl rand -base64 32

# 48-byte JWT secret for tokens
openssl rand -base64 48
```

## 🧪 Quick API Test

```bash
#!/bin/bash
API_URL="http://localhost:8080"

# Get token
TOKEN=$(curl -s -X POST "$API_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.token')

echo "Token: $TOKEN"

# Validate token
curl -X GET "$API_URL/api/auth/validate?token=$TOKEN"

# Use token for API calls
curl -X GET "$API_URL/api/datasources" \
  -H "Authorization: Bearer $TOKEN"
```

## ✅ Security Checklist

- [ ] `DATASOURCE_ENCRYPTION_KEY` environment variable set
- [ ] `JWT_SECRET` environment variable set
- [ ] H2 console disabled (`SPRING_H2_CONSOLE_ENABLED=false`)
- [ ] Using PostgreSQL/MySQL, not H2 in production
- [ ] SSL/TLS enabled for all connections
- [ ] Database backups configured
- [ ] API JWT tokens tested and working
- [ ] JRXML upload validation working
- [ ] Upload directory is on persistent storage
- [ ] Logs being monitored and archived

## 🚀 Migration Checklist

From Development (H2) → Production (PostgreSQL):

1. **Create PostgreSQL Database**
   ```bash
   createdb reportserver
   createuser -P reportserver  # Set password when prompted
   ```

2. **Update Configuration**
   ```properties
   spring.datasource.url=jdbc:postgresql://host:5432/reportserver
   spring.datasource.username=reportserver
   spring.datasource.password=${DB_PASSWORD}
   spring.datasource.driver-class-name=org.postgresql.Driver
   spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
   ```

3. **Set All Security Environment Variables**
   ```bash
   export DATASOURCE_ENCRYPTION_KEY="..."
   export JWT_SECRET="..."
   export SPRING_DATASOURCE_PASSWORD="..."
   ```

4. **Start Application**
   ```bash
   java -jar report-server-1.0.0.jar
   ```

5. **Verify**
   - Login works
   - API endpoints accessible with JWT
   - JRXML files can be uploaded
   - Datasources can be created and tested

## 🔍 Monitoring & Troubleshooting

### Check if secrets are set
```bash
curl http://localhost:8080/api/auth/validate?token=test
# If error about token validation, JWT_SECRET is not set correctly
```

### Verify password encryption
1. Create a datasource via UI
2. SSH to database server
3. Check the password field - should be base64-encoded, not plaintext

### Test JRXML validation
```bash
# Try uploading a test JRXML with dangerous code
# Should be rejected with "Security issues detected"
curl -F "file=@malicious.jrxml" \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/upload
```

## ℹ️ Default Credentials

| User | Password | Role |
|------|----------|------|
| admin | admin123 | ADMIN |
| operator | operator | OPERATOR |
| viewer | viewer | READ_ONLY |

⚠️ **CHANGE THESE IN PRODUCTION**

## 📚 Links
- Full Documentation: See `SECURITY_FIXES.md`
- API Documentation: Available at `/api/auth` endpoints
- Source Code: Check security package for implementation details

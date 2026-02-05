# JasperReports Server

A lightweight JasperReports Server application built with Spring Boot that supports JRXML report generation with MySQL database connectivity.

## Features

- Upload and manage JRXML report templates
- Generate reports in multiple formats (PDF, XLSX)
- MySQL database connection support for data-driven reports
- RESTful API for report generation
- Simple web interface for report management

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- MySQL 8.0+ (optional, for database-driven reports)

## Database Configuration

The application is pre-configured to connect to a MySQL database. You can customize the connection settings in `src/main/resources/application.properties`:

```properties
# Database configuration (MySQL)
spring.datasource.url=jdbc:mysql://localhost:3306/reportserver?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

### Setting up MySQL Database

1. Install MySQL Server if not already installed
2. Create a database for the application:
   ```sql
   CREATE DATABASE reportserver;
   ```
3. Update the database credentials in `application.properties`
4. The application will automatically create necessary tables on startup

### Database Connection Testing

Test your database connection using the built-in endpoint:

```bash
curl http://localhost:8080/db/test
```

Expected response on success:
```json
{
  "status": "success",
  "message": "Database connection successful"
}
```

## Building the Application

```bash
mvn clean package
```

## Running the Application

```bash
mvn spring-boot:run
```

Or run the JAR file:

```bash
java -jar target/jasper-report-server-1.0.0.jar
```

The application will start on `http://localhost:8080`

## API Endpoints

### Upload Report Template

```bash
POST /upload
Content-Type: multipart/form-data

curl -F "file=@report.jrxml" http://localhost:8080/upload
```

### Generate Report

Generate a report without database connection:
```bash
POST /generate?reportName=report.jrxml&format=pdf

curl -X POST "http://localhost:8080/generate?reportName=report.jrxml&format=pdf" -o output.pdf
```

Generate a report with database connection:
```bash
POST /generate?reportName=report.jrxml&format=pdf&useDatabase=true

curl -X POST "http://localhost:8080/generate?reportName=report.jrxml&format=pdf&useDatabase=true" -o output.pdf
```

Supported formats:
- `pdf` - PDF format (default)
- `xlsx` or `excel` - Excel format

### List Available Reports

```bash
GET /reports

curl http://localhost:8080/reports
```

### Test Database Connection

```bash
GET /db/test

curl http://localhost:8080/db/test
```

## Using Database in Reports

To use database connection in your JRXML reports:

1. Design your report with a SQL query in JasperReports Studio or iReport
2. Upload the JRXML file to the server
3. Generate the report with `useDatabase=true` parameter

Example JRXML with database query:
```xml
<queryString>
    <![CDATA[SELECT * FROM users WHERE active = 1]]>
</queryString>
```

The application will automatically provide the database connection to JasperReports engine when `useDatabase=true`.

## Configuration Options

All configuration options are in `src/main/resources/application.properties`:

- **Server Port**: `server.port=8080`
- **Max File Upload Size**: `spring.servlet.multipart.max-file-size=10MB`
- **Database URL**: `spring.datasource.url`
- **Database Username**: `spring.datasource.username`
- **Database Password**: `spring.datasource.password`
- **JPA Settings**: `spring.jpa.*` for Hibernate configuration

## Troubleshooting

### Database Connection Issues

If you encounter database connection errors:

1. Verify MySQL is running: `sudo systemctl status mysql`
2. Check database credentials in `application.properties`
3. Ensure the database exists: `SHOW DATABASES;`
4. Test connection using `/db/test` endpoint
5. Check MySQL user permissions

### Common Errors

**"Access denied for user"**: Update username/password in application.properties
**"Unknown database"**: Create the database using `CREATE DATABASE reportserver;`
**"Connection refused"**: Ensure MySQL is running and accepting connections

## License

This project is licensed under the MIT License.

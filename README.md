# JasperReports Server

A lightweight JasperReports Server application built with Spring Boot that supports JRXML report generation with multiple datasource management and MySQL database connectivity.

## Features

- Upload and manage JRXML report templates
- Generate reports in multiple formats (PDF, XLSX)
- **Multiple datasource management with UI** - Configure and manage multiple database connections
- **Dynamic datasource selection** - Select which datasource to use when generating reports
- MySQL and PostgreSQL database support for data-driven reports
- RESTful API for report generation and datasource management
- Simple web interface for report and datasource management

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- MySQL 8.0+ (optional, for database-driven reports)

## Database Configuration

The application now uses an embedded H2 database to store datasource configurations. This allows you to manage multiple database connections through the web interface without editing configuration files.

### Managing Datasources

1. Open the web interface at `http://localhost:8080`
2. Click on the "Datasources" tab
3. Click "Add New Datasource" to configure a new database connection
4. Fill in the connection details:
   - **Name**: A friendly name for the datasource
   - **JDBC URL**: The database connection URL (e.g., `jdbc:mysql://localhost:3306/mydb`)
   - **Username**: Database username
   - **Password**: Database password
   - **Driver Class**: Select MySQL or PostgreSQL driver
5. Click "Test Connection" to verify the connection
6. Click "Save" to store the datasource configuration

**Security Note**: Currently, passwords are stored in the H2 database. For production environments, consider implementing additional encryption or using environment variables for sensitive credentials. API responses do not include password fields to prevent exposure.

### Supported Databases

- **MySQL**: `jdbc:mysql://host:port/database`
- **PostgreSQL**: `jdbc:postgresql://host:port/database`

### Legacy Configuration (Optional)

You can still view the application's internal H2 database configuration in `src/main/resources/application.properties`. However, for report generation, you should use the datasource management UI to configure your external databases.

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

Generate a report with database connection using a specific datasource:
```bash
POST /generate?reportName=report.jrxml&format=pdf&useDatabase=true&datasourceId=1

curl -X POST "http://localhost:8080/generate?reportName=report.jrxml&format=pdf&useDatabase=true&datasourceId=1" -o output.pdf
```

Supported formats:
- `pdf` - PDF format (default)
- `xlsx` or `excel` - Excel format

### Datasource Management API

#### List All Datasources
```bash
GET /api/datasources

curl http://localhost:8080/api/datasources
```

#### Get Datasource by ID
```bash
GET /api/datasources/{id}

curl http://localhost:8080/api/datasources/1
```

#### Create Datasource
```bash
POST /api/datasources
Content-Type: application/json

curl -X POST http://localhost:8080/api/datasources \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Database",
    "url": "jdbc:mysql://localhost:3306/mydb",
    "username": "user",
    "password": "pass",
    "driverClassName": "com.mysql.cj.jdbc.Driver"
  }'
```

#### Update Datasource
```bash
PUT /api/datasources/{id}
Content-Type: application/json

curl -X PUT http://localhost:8080/api/datasources/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Database",
    "url": "jdbc:mysql://localhost:3306/mydb",
    "username": "user",
    "password": "pass",
    "driverClassName": "com.mysql.cj.jdbc.Driver"
  }'
```

#### Delete Datasource
```bash
DELETE /api/datasources/{id}

curl -X DELETE http://localhost:8080/api/datasources/1
```

#### Test Datasource Connection
```bash
POST /api/datasources/test
Content-Type: application/json

curl -X POST http://localhost:8080/api/datasources/test \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test",
    "url": "jdbc:mysql://localhost:3306/mydb",
    "username": "user",
    "password": "pass",
    "driverClassName": "com.mysql.cj.jdbc.Driver"
  }'
```

### List Available Reports

```bash
GET /reports

curl http://localhost:8080/reports
```

## Using Database in Reports

To use database connection in your JRXML reports:

1. Design your report with a SQL query in JasperReports Studio or iReport
2. Upload the JRXML file to the server
3. Create a datasource in the "Datasources" tab
4. When generating the report, check "Use Database Connection" and select your datasource

Example JRXML with database query:
```xml
<queryString>
    <![CDATA[SELECT * FROM users WHERE active = 1]]>
</queryString>
```

The application will automatically provide the selected database connection to JasperReports engine.

## Configuration Options

All configuration options are in `src/main/resources/application.properties`:

- **Server Port**: `server.port=8080`
- **Max File Upload Size**: `spring.servlet.multipart.max-file-size=10MB`
- **H2 Database (Internal)**: Used for storing datasource configurations
  - `spring.datasource.url=jdbc:h2:file:./data/reportserver`
  - Access H2 Console at `/h2-console` (if enabled)

## Troubleshooting

### Datasource Connection Issues

If you encounter datasource connection errors:

1. Verify the database server is running
2. Check the JDBC URL format is correct
3. Ensure database credentials are accurate
4. Test connection using the "Test Connection" button in the datasource form
5. Check the database user has appropriate permissions

### Common Errors

**"Please select a datasource when using database connection"**: You must select a datasource from the dropdown when "Use Database Connection" is checked
**"Datasource with name 'X' already exists"**: Choose a unique name for each datasource
**"Connection refused"**: Ensure the database server is running and accepting connections
**"Access denied for user"**: Verify username and password are correct

## License

This project is licensed under the MIT License.

# Reports Server

A comprehensive Reports Server application built with Spring Boot that supports JRXML report generation, visual report building, user management, and multiple datasource connectivity.

## Features

### Report Management
- **Upload and manage JRXML report templates** - Upload .jrxml files through the web interface
- **Visual Report Builder** - Build reports from database tables without writing JRXML code
- **JRXML Editor** - Edit report templates directly in the browser with Monaco Editor
- **Download JRXML files** - Download report templates for external editing
- **Delete reports** - Remove unwanted report templates with confirmation dialog
- **Generate reports in 10+ formats** - PDF, HTML, Excel (XLSX/XLS), Word (DOCX), RTF, ODT, CSV, XML, TXT

### Database & Datasources
- **Multiple datasource types** - Support for JDBC, CSV, XML, JSON, empty, and collection datasources
- **JDBC databases** - MySQL, PostgreSQL, and other relational databases
- **File-based sources** - CSV, XML, and JSON file data sources
- **JavaBeans & Collections** - Support for POJOs and Java collections
- **Dynamic datasource selection** - Select which datasource to use when generating reports
- **Test connections** - Verify database connectivity and file accessibility
- **Schema introspection** - Automatically discover tables and columns for Report Builder (JDBC)

### User Management & Security
- **User authentication** - Secure login system with encrypted passwords
- **User registration** - Self-service user registration
- **Role-based access control** - Admin and regular user roles
- **Password management** - Change password functionality with first-login enforcement
- **Password reset** - Forgot password with email-based reset flow
- **Admin panel** - Manage users, reset passwords, toggle admin privileges

### Advanced Features
- **Report parameters** - Dynamic report parameters with automatic type detection
- **Database-driven reports** - Execute SQL queries within JRXML templates
- **Scheduled reports** - Automate report generation on Hourly, Daily, Weekly, Monthly, or Yearly intervals
- **RESTful API** - Complete API for programmatic access
- **Modern web interface** - Clean, responsive UI with intuitive navigation
- **CSRF protection** - Secure forms and API endpoints

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- MySQL 8.0+ or PostgreSQL 12+ (optional, for database-driven reports)

## Quick Start

### First Time Setup

1. Clone the repository
2. Build the application:
   ```bash
   mvn clean package
   ```
3. Run the application:
   ```bash
   mvn spring-boot:run
   ```
4. Open your browser to `http://localhost:8080`
5. **Register a new account** - The first user is automatically assigned admin privileges
6. Log in and start creating reports!

### Default Admin Account

After registration, the first user will have admin privileges. Subsequent users will have regular user privileges unless promoted by an admin.

## User Authentication

### Registration
- Navigate to `http://localhost:8080/register`
- Provide username, email, and password (minimum 8 characters)
- First registered user automatically becomes an admin

### Login
- Navigate to `http://localhost:8080/login`
- Enter your username and password
- New users must change their password on first login

### Password Management
- **Change Password**: Available to all users via profile menu
- **Forgot Password**: Email-based password reset flow
- **First Login**: New users are prompted to change password
- **Admin Reset**: Admins can reset any user's password

### User Roles
- **Admin**: Full access to all features including user management
- **User**: Access to reports, datasources, and report builder

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

## Using the Web Interface

### Reports Tab

The Reports tab provides three main sections:

#### 1. Generate Report
- Select an existing report template from the dropdown
- Choose output format (PDF, HTML, Excel, Word, etc.)
- Optionally select a database connection
- Fill in any required report parameters
- Click "Generate Report" to download

#### 2. Upload JRXML Report  
- Click the upload area or drag-and-drop a .jrxml file
- Supported file types: .jrxml only
- Maximum file size: 10MB

#### 3. Available Reports
- View all uploaded report templates
- **Download**: Download the .jrxml file for external editing
- **Edit**: Open the report in the built-in JRXML editor
- **Delete**: Remove the report (with confirmation)

### Report Builder Tab

The Report Builder allows you to create reports visually without writing JRXML code:

1. **Enter Report Name**: Give your report a unique name (e.g., "SalesReport")
2. **Select Datasource**: Choose the database connection to use
3. **Select Table**: Pick a table from the database
4. **Select Columns**: Choose which columns to include in the report
5. **Add Parameters** (Optional): Define input parameters for filtering
   - Support for String, Integer, BigDecimal, Date, Boolean types
   - Parameters can be used in SQL WHERE clauses
6. **Add Variables** (Optional): Define calculated fields
   - Aggregations: Sum, Average, Count, Min, Max, etc.
   - Custom expressions using JasperReports syntax
7. **Generate**: Creates a .jrxml file automatically

**Example Use Case**: Create a customer report with date range parameters:
```
Report Name: CustomerOrders
Table: orders
Columns: customer_name, order_date, amount
Parameters: startDate (Date), endDate (Date)
SQL: SELECT * FROM orders WHERE order_date BETWEEN $P{startDate} AND $P{endDate}
```

### JRXML Editor

Edit report templates directly in the browser:

- **Syntax Highlighting**: XML syntax highlighting powered by Monaco Editor
- **Auto-save**: Save changes with a single click
- **Download**: Download the edited file
- **Cancel**: Discard changes and return

**Access**: Click the "✏️ Edit" button next to any report in the Available Reports section

### Datasources Tab

Manage multiple types of datasources:

#### Datasource Types

1. **JDBC (Relational Databases)**
   - Connect to MySQL, PostgreSQL, H2, and other JDBC-compliant databases
   - Requires: JDBC URL, username, password, driver class
   - Supports full SQL queries and database operations

2. **CSV (Comma-Separated Values)**
   - Use CSV files as data sources
   - Upload CSV files via the file upload feature
   - Automatically uses first row as column headers
   - Configure field delimiters if needed

3. **XML (eXtensible Markup Language)**
   - Use XML files as structured data sources
   - Upload XML files via the file upload feature
   - Configure XPath expressions to select data nodes
   - Default XPath: `/data/record`

4. **JSON (JavaScript Object Notation)**
   - Use JSON files as data sources
   - Upload JSON files via the file upload feature
   - Configure JSONPath expressions to select data
   - Default JSONPath: `$.*`

5. **EMPTY**
   - No external data source
   - Useful for static reports, charts without data, or parameter-driven reports

6. **COLLECTION (JavaBeans/POJOs)**
   - Pass Java collections or custom objects programmatically
   - Used primarily via API
   - Supports List, ArrayList, and custom Java objects

#### Operations

- **Add Datasource**: Configure new datasources of any supported type
- **Edit**: Modify existing datasource settings
- **Delete**: Remove unused datasources
- **Test Connection**: Verify JDBC connectivity or file accessibility
- **Upload Files**: Upload CSV, XML, or JSON data files
- **View**: See all configured datasources with their types

### Schedules Tab

Automate report generation on a recurring schedule:

1. **Create a Schedule**: Click "Create New Schedule" to open the schedule form
2. **Configure Details**:
   - **Schedule Name**: A descriptive name for the schedule
   - **Report Template**: Select which JRXML report to generate
   - **Output Format**: Choose the output format (PDF, Excel, Word, CSV, etc.)
   - **Frequency**: Select how often the report should run:
     - **Hourly** – Runs every hour at the specified minute
     - **Daily** – Runs once per day at the specified time
     - **Weekly** – Runs on a specific day of the week at the specified time
     - **Monthly** – Runs on a specific day of the month at the specified time
     - **Yearly** – Runs on a specific month and day at the specified time
   - **Datasource** (optional): Select a database connection for database-driven reports
   - **Output Directory** (optional): Custom path for generated files (default: `data/scheduled_output/`)
3. **Manage Schedules**:
   - **Pause/Resume**: Toggle a schedule on or off without deleting it
   - **Run Now**: Trigger an immediate execution of a scheduled report
   - **Edit**: Modify schedule configuration
   - **Delete**: Remove a schedule permanently
4. The scheduler checks for due reports every 60 seconds and generates them automatically
5. Generated files are saved to the output directory with a timestamp in the filename

### User Management (Admin Only)

Admins can manage users via the "User Management" link:

- View all registered users
- Toggle admin privileges
- Reset user passwords
- Delete users
- View user roles and status

## Building the Application

```bash
# Clean and build
mvn clean package

# Skip tests (faster build)
mvn clean package -DskipTests
```

The JAR file will be created in `target/jasper-report-server-1.0.0.jar`

## Running the Application

### Option 1: Using Maven
```bash
mvn spring-boot:run
```

### Option 2: Using the JAR file
```bash
java -jar target/jasper-report-server-1.0.0.jar
```

### Option 3: With custom port
```bash
java -jar target/jasper-report-server-1.0.0.jar --server.port=9090
```

The application will start on `http://localhost:8080` (or your custom port)

## Docker Deployment

### Using Docker Compose (Recommended)

The easiest way to run the application is with Docker Compose:

```bash
# Build and start the container
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the container
docker-compose down

# Rebuild after code changes
docker-compose up -d --build
```

The application will be available at `http://localhost:8080`

**Data Persistence**: The `./data` directory is mounted as a volume, so your database, reports, and uploaded files persist between container restarts.

### Using Docker Manually

```bash
# Build the image
docker build -t reportserver:latest .

# Run the container
docker run -d \
  --name jasper-report-server \
  -p 8080:8080 \
  -v $(pwd)/data:/app/data \
  -e JAVA_OPTS="-Xmx1g -Xms512m" \
  reportserver:latest

# View logs
docker logs -f jasper-report-server

# Stop the container
docker stop jasper-report-server

# Remove the container
docker rm jasper-report-server
```

### Configuration

You can customize the deployment by modifying `docker-compose.yml`:

**Memory Settings**:
```yaml
environment:
  - JAVA_OPTS=-Xmx2g -Xms1g  # Increase memory for large reports
```

**Port Mapping**:
```yaml
ports:
  - "9090:8080"  # Run on port 9090 instead
```

**File Upload Limits**:
```yaml
environment:
  - SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=100MB
  - SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=100MB
```

### Multi-Stage Build

The Dockerfile uses a multi-stage build for optimal image size:
- **Stage 1**: Builds the application with Maven (builder)
- **Stage 2**: Creates a lightweight runtime image with only the JRE

Final image size: ~350MB (compared to ~800MB with full JDK)

### Health Check

The container includes a health check that monitors application status:
- **Interval**: 30 seconds
- **Timeout**: 10 seconds
- **Start Period**: 60 seconds (time for app to start)

Check container health:
```bash
docker ps  # Look for "healthy" status
docker inspect jasper-report-server --format='{{.State.Health.Status}}'
```

## API Endpoints

### Authentication API

#### Login
- Handled by Spring Security form login
- POST to `/login` with `username` and `password`

#### Register
```bash
POST /register
Content-Type: application/x-www-form-urlencoded

username=newuser&email=user@example.com&password=securepass&confirmPassword=securepass
```

#### Logout
```bash
POST /logout
```

### Report Management API

#### Upload Report Template

```bash
POST /upload
Content-Type: multipart/form-data

curl -F "file=@report.jrxml" http://localhost:8080/upload
```

#### Generate Report

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

Generate with parameters:
```bash
POST /generate?reportName=report.jrxml&format=pdf&useDatabase=true&datasourceId=1&startDate=2024-01-01&customerName=John

curl -X POST "http://localhost:8080/generate?reportName=report.jrxml&format=pdf&useDatabase=true&datasourceId=1&startDate=2024-01-01&customerName=John" -o output.pdf
```

Supported formats:
- `pdf` - PDF format (default)
- `html` - HTML format
- `xlsx` - Excel 2007+ format
- `xls` - Excel 97-2003 format
- `docx` - Microsoft Word format
- `rtf` - Rich Text Format
- `odt` - OpenDocument Text format
- `csv` - Comma-separated values
- `xml` - XML format
- `txt` - Plain text format

#### List Available Reports
```bash
GET /reports

curl http://localhost:8080/reports
```

#### Delete Report
```bash
DELETE /reports/{reportName}

curl -X DELETE http://localhost:8080/reports/MyReport.jrxml \
  -H "X-CSRF-TOKEN: your-csrf-token"
```

### Report Builder API

#### Get Tables from Datasource
```bash
GET /api/builder/datasources/{datasourceId}/tables

curl http://localhost:8080/api/builder/datasources/1/tables
```

#### Get Columns from Table
```bash
GET /api/builder/datasources/{datasourceId}/tables/{tableName}/columns

curl http://localhost:8080/api/builder/datasources/1/tables/customers/columns
```

#### Generate Report from Builder
```bash
POST /api/builder/generate
Content-Type: application/json

curl -X POST http://localhost:8080/api/builder/generate \
  -H "Content-Type: application/json" \
  -d '{
    "reportName": "SalesReport",
    "datasourceId": 1,
    "tableName": "sales",
    "columns": ["sale_date", "customer", "amount"],
    "parameters": [
      {
        "name": "startDate",
        "type": "java.util.Date",
        "defaultValue": "new java.util.Date()"
      }
    ],
    "variables": [
      {
        "name": "TotalSales",
        "class": "java.math.BigDecimal",
        "calculation": "Sum",
        "expression": "$F{amount}"
      }
    ]
  }'
```

#### Download JRXML File
```bash
GET /api/builder/download/{reportName}

curl http://localhost:8080/api/builder/download/MyReport.jrxml -o MyReport.jrxml
```

### JRXML Editor API

#### Load JRXML Content
```bash
GET /api/jrxml/load/{fileName}

curl http://localhost:8080/api/jrxml/load/report.jrxml
```

#### Save JRXML Content
```bash
POST /api/jrxml/save
Content-Type: application/x-www-form-urlencoded

curl -X POST http://localhost:8080/api/jrxml/save \
  -d "fileName=report.jrxml" \
  --data-urlencode "content=<?xml version=\"1.0\"?>..."
```

#### Get Report Parameters
```bash
GET /api/jrxml/parameters/{reportName}

curl http://localhost:8080/api/jrxml/parameters/report.jrxml
```

Response includes parameter name, type, and input type (text, number, date, checkbox).

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

# Create JDBC datasource
curl -X POST http://localhost:8080/api/datasources \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My MySQL Database",
    "type": "JDBC",
    "url": "jdbc:mysql://localhost:3306/mydb",
    "username": "user",
    "password": "pass",
    "driverClassName": "com.mysql.cj.jdbc.Driver"
  }'

# Create CSV datasource
curl -X POST http://localhost:8080/api/datasources \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Sales Data CSV",
    "type": "CSV",
    "filePath": "sales_2024.csv"
  }'

# Create XML datasource with XPath
curl -X POST http://localhost:8080/api/datasources \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Customer XML",
    "type": "XML",
    "filePath": "customers.xml",
    "configuration": "/customers/customer"
  }'

# Create JSON datasource
curl -X POST http://localhost:8080/api/datasources \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Orders JSON",
    "type": "JSON",
    "filePath": "orders.json",
    "configuration": "$.orders[*]"
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

# Test JDBC connection
curl -X POST http://localhost:8080/api/datasources/test \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test MySQL",
    "type": "JDBC",
    "url": "jdbc:mysql://localhost:3306/mydb",
    "username": "user",
    "password": "pass",
    "driverClassName": "com.mysql.cj.jdbc.Driver"
  }'

# Test CSV file datasource
curl -X POST http://localhost:8080/api/datasources/test \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Sales CSV",
    "type": "CSV",
    "filePath": "sales_data.csv"
  }'
```

#### Upload Data File (CSV/XML/JSON)
```bash
POST /api/datasources/upload-file
Content-Type: multipart/form-data

curl -F "file=@data.csv" http://localhost:8080/api/datasources/upload-file
curl -F "file=@records.xml" http://localhost:8080/api/datasources/upload-file
curl -F "file=@data.json" http://localhost:8080/api/datasources/upload-file
```
Files are uploaded to `data/datasource_files/` directory.

### List Available Reports

```bash
GET /reports

curl http://localhost:8080/reports
```

### User Management API (Admin Only)

#### List All Users
```bash
GET /api/users

curl http://localhost:8080/api/users \
  -H "Cookie: JSESSIONID=your-session-id"
```

#### Create User
```bash
POST /api/users
Content-Type: application/json

curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "email": "user@example.com",
    "password": "securepass123",
    "role": "USER"
  }'
```

#### Update User
```bash
PUT /api/users/{id}
Content-Type: application/json

curl -X PUT http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{
    "username": "updateduser",
    "email": "updated@example.com",
    "role": "ADMIN"
  }'
```

#### Delete User
```bash
DELETE /api/users/{id}

curl -X DELETE http://localhost:8080/api/users/1
```

#### Reset User Password
```bash
POST /api/users/{id}/reset-password

curl -X POST http://localhost:8080/api/users/1/reset-password
```

#### Toggle Admin Status
```bash
POST /api/users/{id}/toggle-admin

curl -X POST http://localhost:8080/api/users/1/toggle-admin
```

### Report Schedule API

#### List All Schedules
```bash
GET /api/schedules

curl http://localhost:8080/api/schedules
```
Returns all schedules (admins see all; regular users see only their own).

#### Get Schedule by ID
```bash
GET /api/schedules/{id}

curl http://localhost:8080/api/schedules/1
```

#### Create Schedule
```bash
POST /api/schedules
Content-Type: application/json

curl -X POST http://localhost:8080/api/schedules \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Weekly Sales Report",
    "reportName": "SalesReport.jrxml",
    "format": "pdf",
    "scheduleType": "WEEKLY",
    "dayOfWeek": 1,
    "hourOfDay": 8,
    "minuteOfHour": 0,
    "datasourceId": 1,
    "outputPath": "data/scheduled_output/",
    "description": "Generates sales PDF every Monday at 08:00"
  }'
```

Schedule type options: `HOURLY`, `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`.

| Field | Required | Used by |
|-------|----------|---------|
| `minuteOfHour` | All | Minute of the hour (0-59) |
| `hourOfDay` | DAILY, WEEKLY, MONTHLY, YEARLY | Hour of the day (0-23) |
| `dayOfWeek` | WEEKLY | Day of the week (1=Monday … 7=Sunday) |
| `dayOfMonth` | MONTHLY, YEARLY | Day of the month (1-31) |
| `month` | YEARLY | Month of the year (1-12) |

#### Update Schedule
```bash
PUT /api/schedules/{id}
Content-Type: application/json

curl -X PUT http://localhost:8080/api/schedules/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Daily Sales Report",
    "reportName": "SalesReport.jrxml",
    "format": "xlsx",
    "scheduleType": "DAILY",
    "hourOfDay": 6,
    "minuteOfHour": 30
  }'
```

#### Delete Schedule
```bash
DELETE /api/schedules/{id}

curl -X DELETE http://localhost:8080/api/schedules/1
```

#### Toggle Schedule (Enable/Disable)
```bash
POST /api/schedules/{id}/toggle?enabled=false

curl -X POST "http://localhost:8080/api/schedules/1/toggle?enabled=false"
```

#### Execute Schedule Now (On-Demand)
```bash
POST /api/schedules/{id}/execute

curl -X POST http://localhost:8080/api/schedules/1/execute
```
Triggers an immediate execution regardless of the next scheduled time.

#### Change Own Password
```bash
POST /api/change-password
Content-Type: application/x-www-form-urlencoded

curl -X POST http://localhost:8080/api/change-password \
  -d "currentPassword=oldpass" \
  -d "newPassword=newpass123" \
  -d "confirmPassword=newpass123"
```

## Using Multiple Datasource Types in Reports

The application supports various datasource types beyond JDBC databases:

### JDBC Database Sources

Standard relational database connectivity:

1. Design your report with a SQL query in JasperReports Studio or iReport
2. Upload the JRXML file to the server
3. Create a JDBC datasource in the "Datasources" tab
4. When generating the report, select the JDBC datasource

### CSV File Sources

Use CSV files as data sources:

1. **Upload CSV File**: Upload your CSV file via `/api/datasources/upload-file`
2. **Create Datasource**: Create a CSV-type datasource with the filename
3. **Design Report**: In your JRXML, define fields matching your CSV column names
4. **No Query Needed**: CSV datasources don't use SQL queries

**Example JRXML for CSV**:
```xml
<field name="ProductName" class="java.lang.String"/>
<field name="Price" class="java.math.BigDecimal"/>
<field name="Quantity" class="java.lang.Integer"/>

<!-- No queryString needed for CSV -->

<detail>
    <band height="20">
        <textField>
            <reportElement x="0" y="0" width="200" height="20"/>
            <textFieldExpression><![CDATA[$F{ProductName}]]></textFieldExpression>
        </textField>
    </band>
</detail>
```

### XML File Sources

Use XML files with XPath expressions:

1. **Upload XML File**: Upload your XML file
2. **Create Datasource**: Specify the file and XPath expression (e.g., `/data/record`)
3. **Design Report**: Define fields matching XML node names

**Example XML Data**:
```xml
<data>
    <record>
        <name>John Doe</name>
        <email>john@example.com</email>
        <age>30</age>
    </record>
    <record>
        <name>Jane Smith</name>
        <email>jane@example.com</email>
        <age>25</age>
    </record>
</data>
```

**Example JRXML for XML**:
```xml
<field name="name" class="java.lang.String">
    <fieldDescription><![CDATA[name]]></fieldDescription>
</field>
<field name="email" class="java.lang.String">
    <fieldDescription><![CDATA[email]]></fieldDescription>
</field>
<field name="age" class="java.lang.Integer">
    <fieldDescription><![CDATA[age]]></fieldDescription>
</field>
```

### JSON File Sources

Use JSON files with JSONPath expressions:

1. **Upload JSON File**: Upload your JSON file
2. **Create Datasource**: Specify the file and JSONPath expression (e.g., `$.users[*]`)
3. **Design Report**: Define fields matching JSON property names

**Example JSON Data**:
```json
{
    "users": [
        {"id": 1, "name": "Alice", "role": "Admin"},
        {"id": 2, "name": "Bob", "role": "User"}
    ]
}
```

**Example JRXML for JSON**:
```xml
<field name="id" class="java.lang.Integer">
    <fieldDescription><![CDATA[id]]></fieldDescription>
</field>
<field name="name" class="java.lang.String">
    <fieldDescription><![CDATA[name]]></fieldDescription>
</field>
<field name="role" class="java.lang.String">
    <fieldDescription><![CDATA[role]]></fieldDescription>
</field>
```

### Empty Datasource

For static reports without external data:

1. Create an EMPTY-type datasource or don't select any datasource
2. Use parameters and hard-coded values
3. Useful for certificates, forms, or parameter-driven reports

### Collection Datasource (API Only)

Pass Java collections programmatically:

```java
List<MyBean> dataList = getMyData();
JRBeanCollectionDataSource beanDS = new JRBeanCollectionDataSource(dataList);
// Pass to report API
```

## Using Database in Reports

To use database connection in your JRXML reports:

1. Design your report with a SQL query in JasperReports Studio or iReport
2. Upload the JRXML file to the server
3. Create a datasource in the "Datasources" tab
4. When generating the report, check "Use Database Connection" and select your datasource

### SQL Queries

Example JRXML with database query:
```xml
<queryString>
    <![CDATA[SELECT * FROM users WHERE active = 1]]>
</queryString>
```

### Using Parameters in SQL

Define parameters in your JRXML:
```xml
<parameter name="startDate" class="java.util.Date"/>
<parameter name="endDate" class="java.util.Date"/>
<parameter name="minAmount" class="java.math.BigDecimal"/>

<queryString>
    <![CDATA[
        SELECT * FROM orders 
        WHERE order_date BETWEEN $P{startDate} AND $P{endDate}
        AND amount >= $P{minAmount}
    ]]>
</queryString>
```

When generating the report, pass parameters in the URL:
```bash
/generate?reportName=orders.jrxml&useDatabase=true&datasourceId=1&startDate=2024-01-01&endDate=2024-12-31&minAmount=100
```

### Parameter Types

The application automatically detects and handles these parameter types:
- **String**: Text values
- **Integer/Long**: Whole numbers
- **BigDecimal/Double**: Decimal numbers
- **Date**: Date values (format: yyyy-MM-dd)
- **Boolean**: true/false values
- **Timestamp**: Date and time values

### Fields and Variables

Access database fields in your report:
```xml
<field name="customer_name" class="java.lang.String"/>
<field name="amount" class="java.math.BigDecimal"/>

<!-- Use in text field -->
<textFieldExpression><![CDATA[$F{customer_name}]]></textFieldExpression>
```

Create calculated variables:
```xml
<variable name="TotalAmount" class="java.math.BigDecimal" calculation="Sum">
    <variableExpression><![CDATA[$F{amount}]]></variableExpression>
</variable>
```

The application will automatically provide the selected database connection to JasperReports engine.

## Configuration Options

All configuration options are in `src/main/resources/application.properties`:

### Server Configuration
- **Server Port**: `server.port=8080`
- **Context Path**: `server.servlet.context-path=/` (optional)
- **Session Timeout**: `server.servlet.session.timeout=30m`

### File Upload Configuration
- **Max File Size**: `spring.servlet.multipart.max-file-size=10MB`
- **Max Request Size**: `spring.servlet.multipart.max-request-size=10MB`
- **Upload Directory**: Configured in code as `data/reports/`

### Database Configuration (Internal H2)
Used for storing datasource configurations and user data:
- **URL**: `spring.datasource.url=jdbc:h2:file:./data/reportserver`
- **Driver**: `spring.datasource.driver-class-name=org.h2.Driver`
- **Username**: `spring.datasource.username=sa`
- **Password**: `spring.datasource.password=` (empty by default)
- **H2 Console**: Access at `/h2-console` (if enabled in development)

### JPA/Hibernate Configuration
- **DDL Auto**: `spring.jpa.hibernate.ddl-auto=update`
- **Show SQL**: `spring.jpa.show-sql=false`
- **Database Platform**: `spring.jpa.database-platform=org.hibernate.dialect.H2Dialect`

### Security Configuration
- **Password Encoding**: BCrypt
- **CSRF Protection**: Enabled (with exceptions for API endpoints)
- **Session Management**: Form-based authentication
- **Default Login Page**: `/login`
- **Default Logout URL**: `/logout`

### JasperReports Configuration
Configured in `jasperreports.properties` and `jasperreports_extension.properties`:
- **Font Extensions**: Support for custom fonts
- **Export Configurations**: Settings for various export formats
- **Query Executers**: Database query execution settings

### Custom Configuration

To change the reports directory, modify the `UPLOAD_DIR` constant in:
- `ReportController.java`
- `BuilderController.java`
- `JrxmlEditorController.java`

To enable H2 Console (development only):
```properties
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

## Troubleshooting

### Authentication Issues

**"Invalid username or password"**
- Verify credentials are correct
- Check if account exists (use forgot password if needed)
- Ensure password meets minimum 8 character requirement

**"Access Denied" or 403 Forbidden**
- Check if CSRF token is included in API requests
- Verify user has necessary permissions (admin vs regular user)
- For API calls, include valid session cookie or authentication

**"Must change password"**
- New users must change password on first login
- Complete the password change flow before accessing other features

### Report Generation Issues

**"Report file not found"**
- Ensure the .jrxml file was successfully uploaded
- Check the exact filename including extension
- Verify file exists in Available Reports list

**"Failed to compile report"**
- JRXML file has syntax errors
- Open in JRXML editor to check and fix errors
- Validate XML structure
- Test in JasperReports Studio first

**"Error generating report"**
- Check application logs for detailed error message
- Verify all required parameters are provided
- Ensure parameter types match JRXML definition

**Report generation timeout**
- Large datasets may take time to process
- Consider adding WHERE clauses to limit data
- Optimize SQL queries in JRXML
- Increase JVM heap size if needed: `java -Xmx2g -jar ...`

### Datasource Connection Issues

**"Failed to connect to datasource"**
- Verify the database server is running
- Check the JDBC URL format is correct
- Ensure database credentials are accurate
- Test connection using the "Test Connection" button
- Check the database user has appropriate permissions
- Verify network connectivity to database server

**"Connection refused"**
- Database server is not running
- Wrong host or port in JDBC URL
- Firewall blocking connection

**"Access denied for user"**
- Incorrect username or password
- User doesn't have permission to access database
- User doesn't have permission for specific tables

**"No suitable driver"**
- JDBC driver not included in classpath
- Check `pom.xml` includes MySQL or PostgreSQL driver
- Verify driver class name is correct

### Report Builder Issues

**"No tables found"**
- Datasource connection failed
- User lacks permission to view schema
- Database is empty
- Wrong schema/catalog selected

**"Error loading columns"**
- Table doesn't exist or name is incorrect
- User lacks SELECT permission on table
- Connection was lost

**"Generated report not working"**
- Check SQL query syntax in generated JRXML
- Verify table and column names are correct
- Test parameters are working as expected
- Review generated JRXML in editor

### JRXML Editor Issues

**"Failed to load file"**
- File may have been deleted
- Check file permissions
- Refresh the reports list

**"Failed to save"**
- Check file is not read-only
- Ensure valid XML syntax
- Verify disk space is available

### File Upload Issues

**"Only .jrxml files are allowed"**
- Ensure file has .jrxml extension
- Don't upload .jasper (compiled) files
- File must be XML-based JRXML source

**"File upload failed"**
- Check file size is under 10MB limit
- Increase limit in `application.properties` if needed
- Verify `data/reports/` directory exists and is writable

### Common Errors

**"Please select a datasource when using database connection"**
- You must select a datasource from the dropdown when "Use Database Connection" is checked
- Create a datasource first if none exist

**"Datasource with name 'X' already exists"**
- Choose a unique name for each datasource
- Cannot have duplicate datasource names

**"Parameter type mismatch"**
- Ensure parameter values match the expected type
- Dates should be in yyyy-MM-dd format
- Numbers should not include currency symbols or commas

**405 Method Not Allowed**
- Check HTTP method (GET, POST, PUT, DELETE) matches endpoint
- Verify endpoint URL is correct

**CSRF Token errors**
- For API calls, include CSRF token in headers
- Token is available in meta tag: `<meta name="_csrf" ...>`
- JavaScript automatically includes token for fetch requests

## Best Practices

### Report Design
- **Test JRXML locally first**: Use JasperReports Studio to design and test reports before uploading
- **Use parameters for filtering**: Makes reports reusable with different criteria
- **Optimize SQL queries**: Add indexes, use WHERE clauses, limit result sets
- **Name fields clearly**: Use descriptive names for fields and variables
- **Document parameters**: Add descriptions in JRXML for maintainability

### Datasource Management
- **Descriptive names**: Use clear names like "Production_MySQL" or "Analytics_PostgreSQL"
- **Dedicated users**: Create database users specifically for reporting with read-only permissions
- **Test before saving**: Always use "Test Connection" to verify settings
- **Monitor connections**: Close connections properly, don't leave them idle
- **Regular backups**: Backup H2 database containing datasource configurations

### Security
- **Strong passwords**: Enforce minimum 8 characters, use complexity
- **Regular password changes**: Encourage users to update passwords periodically
- **Limit admin accounts**: Only grant admin privileges when necessary
- **Review user access**: Regularly audit user accounts and permissions
- **Secure database credentials**: Use read-only database users for reports
- **HTTPS in production**: Deploy behind HTTPS proxy in production environments

### Performance
- **Limit result sets**: Use pagination or date ranges in SQL queries
- **Cache compiled reports**: JasperReports compiles reports on first use
- **Connection pooling**: Consider implementing connection pooling for high-load scenarios
- **Monitor resources**: Watch CPU, memory, and disk usage
- **Regular cleanup**: Remove unused reports and datasources

### Report Builder Tips
- **Start simple**: Begin with basic reports, add complexity gradually
- **Use parameters wisely**: Define parameters before selecting columns
- **Test generated JRXML**: Review and test the generated JRXML file
- **Edit after generation**: Use JRXML editor to fine-tune generated reports
- **Naming conventions**: Use consistent naming for reports (e.g., Department_Report_Name)

## Project Structure

```
ReportServer/
├── src/
│   ├── main/
│   │   ├── java/com/reportserver/
│   │   │   ├── JasperReportServerApplication.java  # Main application class
│   │   │   ├── config/
│   │   │   │   ├── DataInitializer.java            # First-time setup
│   │   │   │   ├── FirstLoginFilter.java           # Password change enforcement
│   │   │   │   ├── JasperReportsConfig.java        # JasperReports configuration
│   │   │   │   └── SecurityConfig.java             # Spring Security configuration
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java             # Login, register, password reset
│   │   │   │   ├── BuilderController.java          # Report Builder API
│   │   │   │   ├── DataSourceController.java       # Datasource management
│   │   │   │   ├── JrxmlEditorController.java     # JRXML editor functionality
│   │   │   │   ├── ReportController.java           # Report generation & management
│   │   │   │   ├── ScheduleController.java         # Report scheduling API
│   │   │   │   └── UserController.java             # User management (Admin)
│   │   │   ├── dto/
│   │   │   │   ├── DataSourceDTO.java              # Datasource data transfer object
│   │   │   │   ├── ParameterDTO.java               # Report parameter DTO
│   │   │   │   ├── ScheduledReportDTO.java          # Scheduled report DTO
│   │   │   │   ├── UserCreationDTO.java            # User creation DTO
│   │   │   │   ├── UserRegistrationDTO.java        # User registration DTO
│   │   │   │   └── VariableDTO.java                # Report variable DTO
│   │   │   ├── model/
│   │   │   │   ├── DataSource.java                 # Datasource entity
│   │   │   │   ├── DataSourceType.java             # Datasource type enum
│   │   │   │   ├── ScheduledReport.java            # Scheduled report entity
│   │   │   │   └── User.java                       # User entity
│   │   │   ├── repository/
│   │   │   │   ├── DataSourceRepository.java       # Datasource JPA repository
│   │   │   │   ├── ScheduledReportRepository.java  # Scheduled report JPA repository
│   │   │   │   └── UserRepository.java             # User JPA repository
│   │   │   └── service/
│   │   │       ├── DatabaseConnectionService.java  # Database connectivity
│   │   │       ├── DataSourceService.java          # Datasource business logic
│   │   │       ├── JRDataSourceProviderService.java # Multi-type datasource provider
│   │   │       ├── JrxmlBuilderService.java        # Report generation from builder
│   │   │       ├── ReportSchedulerService.java     # Scheduled report execution engine
│   │   │       ├── ReportService.java              # Report compilation & rendering
│   │   │       ├── ScheduledReportService.java     # Schedule CRUD & next-run calculation
│   │   │       ├── SchemaIntrospectionService.java # Database schema discovery
│   │   │       └── UserService.java                # User management business logic
│   │   └── resources/
│   │       ├── application.properties              # Application configuration
│   │       ├── jasperreports.properties            # JasperReports settings
│   │       ├── jasperreports_extension.properties  # JasperReports extensions
│   │       └── templates/                          # Thymeleaf HTML templates
│   │           ├── change-password.html            # Password change page
│   │           ├── forgot-password.html            # Password reset request
│   │           ├── index.html                      # Main application UI
│   │           ├── login.html                      # Login page
│   │           ├── register.html                   # Registration page
│   │           ├── reset-password.html             # Password reset page
│   │           └── user-management.html            # Admin user management
├── data/
│   ├── reports/                                    # Uploaded JRXML files
│   ├── datasource_files/                           # CSV, XML, JSON data files
│   ├── scheduled_output/                           # Generated scheduled report files
│   └── reportserver.mv.db                         # H2 database files
├── pom.xml                                         # Maven dependencies
└── README.md                                       # This file
```

## Advanced Data Handling Patterns

This section covers advanced datasource usage patterns, multiple datasources per report, and custom implementations.

### Hibernate Integration

The Hibernate datasource type allows seamless integration with Hibernate ORM:

**Option 1: Using Hibernate Session (Recommended)**
```java
// In your custom report generation logic
Session session = sessionFactory.openSession();
Map<String, Object> parameters = new HashMap<>();
parameters.put("HIBERNATE_SESSION", session);

// Use HIBERNATE datasource type - it will use the session from parameters
reportService.generateReport(reportName, parameters, datasourceId);
```

**Option 2: JDBC Connection with Hibernate**
Configure the datasource with Hibernate JDBC settings:
```json
{
  "type": "HIBERNATE",
  "name": "Hibernate DB",
  "url": "jdbc:mysql://localhost:3306/mydb",
  "username": "user",
  "password": "pass",
  "driverClassName": "com.mysql.cj.jdbc.Driver"
}
```

### Custom Datasource Implementation

To implement a custom datasource provider:

1. **Extend JRDataSource** or use existing implementations
2. **Modify JRDataSourceProviderService** to add your custom type

Example: adding a custom MongoDB implementation:
```java
private JRDataSource getMongoDBDataSource(DataSource dataSource, Map<String, Object> parameters) {
    // Parse configuration JSON
    String config = dataSource.getConfiguration();
    JSONObject jsonConfig = new JSONObject(config);
    
    // Connect to MongoDB
    MongoClient mongoClient = MongoClients.create(dataSource.getUrl());
    MongoDatabase database = mongoClient.getDatabase(jsonConfig.getString("database"));
    MongoCollection<Document> collection = database.getCollection(jsonConfig.getString("collection"));
    
    // Query documents
    String queryString = jsonConfig.optString("query", "{}");
    Bson query = BsonDocument.parse(queryString);
    List<Document> documents = collection.find(query).into(new ArrayList<>());
    
    // Convert to Map list for JRBeanCollectionDataSource
    List<Map<String, Object>> data = documents.stream()
        .map(doc -> new HashMap<String, Object>(doc))
        .collect(Collectors.toList());
    
    return new JRBeanCollectionDataSource(data);
}
```

### Multiple Datasources in One Report with Subreports

Use subreports to combine data from different sources:

**Main Report JRXML** (main_report.jrxml):
```xml
<jasperReport>
    <parameter name="SUBREPORT_DIR" class="java.lang.String"/>
    <parameter name="SUBREPORT_DATASOURCE" class="net.sf.jasperreports.engine.JRDataSource"/>
    
    <detail>
        <band height="200">
            <!-- Main datasource data -->
            <textField>
                <reportElement x="0" y="0" width="200" height="20"/>
                <textFieldExpression>
                    <![CDATA[$F{mainField}]]>
                </textFieldExpression>
            </textField>
            
            <!-- Subreport with different datasource -->
            <subreport>
                <reportElement x="0" y="30" width="500" height="150"/>
                <dataSourceExpression>
                    <![CDATA[$P{SUBREPORT_DATASOURCE}]]>
                </dataSourceExpression>
                <subreportExpression>
                    <![CDATA[$P{SUBREPORT_DIR} + "subreport.jasper"]]>
                </subreportExpression>
            </subreport>
        </band>
    </detail>
</jasperReport>
```

**Generate Report with Multiple Datasources:**
```bash
# Create parameters for subreport datasource
curl -X POST http://localhost:8080/api/reports/generate \
  -H "Content-Type: application/json" \
  -d '{
    "reportName": "main_report",
    "format": "PDF",
    "datasourceId": 1,
    "parameters": {
      "SUBREPORT_DIR": "/path/to/reports/",
      "SUBREPORT_DATASOURCE": "@datasource:2"
    }
  }'
```

### Using Datasets for Complex Reports

Datasets allow organizing multiple queries within a single report:

```xml
<jasperReport>
    <!-- Main query -->
    <queryString>
        <![CDATA[SELECT * FROM customers WHERE active = true]]>
    </queryString>
    
    <!-- Subdataset for orders -->
    <subDataset name="OrdersDataset">
        <parameter name="CustomerId" class="java.lang.Integer"/>
        <queryString>
            <![CDATA[SELECT * FROM orders WHERE customer_id = $P{CustomerId}]]>
        </queryString>
        <field name="order_id" class="java.lang.Integer"/>
        <field name="order_date" class="java.util.Date"/>
        <field name="amount" class="java.math.BigDecimal"/>
    </subDataset>
    
    <!-- Main dataset fields -->
    <field name="customer_id" class="java.lang.Integer"/>
    <field name="customer_name" class="java.lang.String"/>
    
    <detail>
        <band height="300">
            <textField>
                <reportElement x="0" y="0" width="200" height="20"/>
                <textFieldExpression>
                    <![CDATA[$F{customer_name}]]>
                </textFieldExpression>
            </textField>
            
            <!-- Table using subdataset -->
            <componentElement>
                <reportElement x="0" y="30" width="500" height="250"/>
                <jr:table xmlns:jr="http://jasperreports.sourceforge.net/jasperreports/components">
                    <datasetRun subDataset="OrdersDataset">
                        <datasetParameter name="CustomerId">
                            <datasetParameterExpression>
                                <![CDATA[$F{customer_id}]]>
                            </datasetParameterExpression>
                        </datasetParameter>
                        <connectionExpression>
                            <![CDATA[$P{REPORT_CONNECTION}]]>
                        </connectionExpression>
                    </datasetRun>
                    <jr:column width="100">
                        <jr:detailCell height="20">
                            <textField>
                                <reportElement x="0" y="0" width="100" height="20"/>
                                <textFieldExpression>
                                    <![CDATA[$F{order_id}]]>
                                </textFieldExpression>
                            </textField>
                        </jr:detailCell>
                    </jr:column>
                </jr:table>
            </componentElement>
        </band>
    </detail>
</jasperReport>
```

### EJB and JavaBeans Support

Use the COLLECTION datasource type for JavaBeans and EJBs:

**Example: Passing POJOs to Report**
```java
// Define your POJO
public class Customer {
    private String name;
    private String email;
    private Double totalPurchases;
    // Getters and setters
}

// Create collection
List<Customer> customers = customerService.findAll();

// Pass to JasperReports
Map<String, Object> parameters = new HashMap<>();
parameters.put("REPORT_DATA_SOURCE", new JRBeanCollectionDataSource(customers));

// Generate report using COLLECTION datasource type
reportService.generateReport("customer_report", parameters, collectionDatasourceId);
```

**JRXML for POJO fields:**
```xml
<jasperReport>
    <field name="name" class="java.lang.String"/>
    <field name="email" class="java.lang.String"/>
    <field name="totalPurchases" class="java.lang.Double"/>
    
    <detail>
        <band height="20">
            <textField>
                <reportElement x="0" y="0" width="150" height="20"/>
                <textFieldExpression>
                    <![CDATA[$F{name}]]>
                </textFieldExpression>
            </textField>
            <textField>
                <reportElement x="150" y="0" width="150" height="20"/>
                <textFieldExpression>
                    <![CDATA[$F{email}]]>
                </textFieldExpression>
            </textField>
            <textField pattern="#,##0.00">
                <reportElement x="300" y="0" width="100" height="20"/>
                <textFieldExpression>
                    <![CDATA[$F{totalPurchases}]]>
                </textFieldExpression>
            </textField>
        </band>
    </detail>
</jasperReport>
```

### REST API with Authentication

For REST APIs requiring authentication:

```json
{
  "type": "REST_API",
  "name": "External API",
  "url": "https://api.example.com/data",
  "username": "api_user",
  "password": "api_token",
  "configuration": "$.results[*]"
}
```

The system will:
- Add Basic Authentication headers automatically
- Parse JSON response using JSONPath expression
- Support both JSON and XML responses (auto-detected from Content-Type)

### Big Data and Non-Relational Sources

**For Big Data sources:**
1. Query data from your Big Data platform (Hadoop, Spark, etc.)
2. Convert to List<Map<String, Object>> or List<YourPOJO>
3. Use COLLECTION datasource type
4. Pass collection via parameters

**Example with Spark:**
```java
// Query Spark
Dataset<Row> df = sparkSession.sql("SELECT * FROM big_table WHERE date = current_date()");
List<Row> rows = df.collectAsList();

// Convert to Maps
List<Map<String, Object>> data = rows.stream()
    .map(row -> {
        Map<String, Object> map = new HashMap<>();
        for (String field : row.schema().fieldNames()) {
            map.put(field, row.getAs(field));
        }
        return map;
    })
    .collect(Collectors.toList());

// Create datasource
JRDataSource dataSource = new JRBeanCollectionDataSource(data);
parameters.put("REPORT_DATA_SOURCE", dataSource);
```

### Rewindable Data Sources

JasperReports automatically handles rewindable datasources for features like:
- **Crosstabs** - Require multiple passes through data
- **Charts with calculations** - May need to scan data twice
- **Report totals** - Calculate before detail rendering

For custom datasources, ensure they implement the rewind capability:
```java
public class CustomRewindableDataSource implements JRRewindableDataSource {
    private List<Record> records;
    private int index = -1;
    
    @Override
    public boolean next() {
        index++;
        return index < records.size();
    }
    
    @Override
    public Object getFieldValue(JRField field) {
        return records.get(index).get(field.getName());
    }
    
    @Override
    public void moveFirst() {
        index = -1; // Reset for rewind
    }
}
```

## Technologies Used

- **Spring Boot 3.x** - Application framework
- **Spring Security** - Authentication and authorization
- **Spring Data JPA** - Database access layer
- **H2 Database** - Embedded database for application data
- **JasperReports 6.20.6** - Report generation engine with multi-datasource support
- **JRDataSource API** - Support for JDBC, CSV, XML, JSON, MongoDB, REST API, Hibernate, and collection datasources
- **Thymeleaf** - Server-side templating
- **Monaco Editor** - Browser-based code editor
- **MySQL Connector** - MySQL database driver
- **PostgreSQL Driver** - PostgreSQL database driver
- **Maven** - Build and dependency management

### Supported Datasource Types

1. **JDBC** - MySQL, PostgreSQL, H2, Oracle, SQL Server, and any JDBC-compliant database
2. **CSV** - Comma-separated values files with configurable delimiters
3. **XML** - XML files with XPath selector expressions
4. **JSON** - JSON files and APIs with JSONPath selector expressions
5. **MongoDB** - NoSQL database (requires custom implementation or REST API wrapper)
6. **REST API** - HTTP/HTTPS endpoints returning JSON or XML (with authentication support)
7. **Hibernate** - Hibernate ORM sessions or JDBC connections
8. **Empty** - Static reports without external data
9. **Collection** - JavaBeans, POJOs, EJBs, Lists, and Java collections

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Development Setup

1. Clone the repository
2. Import as Maven project in your IDE
3. Run `mvn clean install`
4. Run `JasperReportServerApplication.java`
5. Access at `http://localhost:8080`

## License

This project is licensed under the MIT License.

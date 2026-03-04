# Role-Based Access Control Changes

## Overview
This document summarizes the changes made to implement proper role-based access control for the Report Server application.

## Changes Made

### 1. **Added Current User Endpoint** (UserController.java)
- **Endpoint**: `GET /api/current-user`
- **Purpose**: Returns information about the currently authenticated user, including their role
- **Access**: All authenticated users
- **Response**: JSON with status, username, role, email, and enabled flag

```json
{
  "status": "success",
  "username": "john_doe",
  "role": "ADMIN",
  "email": "john@example.com",
  "enabled": true
}
```

### 2. **Added Report Download Endpoint for READ_ONLY Users** (ReportController.java)
- **Endpoint**: `POST /download-report`
- **Purpose**: Allows READ_ONLY, OPERATOR, and ADMIN users to download reports in PDF format
- **Parameters**: 
  - `reportName` (string): Name of the report file
  - `format` (string, default="pdf"): Output format
- **Features**: 
  - No parameters required
  - Simplified report generation for READ_ONLY users
  - Output as PDF for easy viewing

### 3. **Updated Frontend - Role Detection** (index-core.js)
- **New Variable**: `currentUserRole` - Stores the current user's role
- **New Function**: `fetchCurrentUser()` - Fetches the current user's information from `/api/current-user`
- **Integration**: Called during page load before loading reports and datasources
- **Side Effect**: Sets `currentUserRole` for use throughout the application

### 4. **Updated Report List Display** (index-core.js)
- **Changes in `loadReports()` function**:
  - Edit button (✏️) - Now only visible for ADMIN and OPERATOR roles
  - Delete button (🗑️) - Now only visible for ADMIN and OPERATOR roles
  - Download button (📥) - Available for all users (downloads JRXML file)
  - New View button (👁️) - For READ_ONLY users to download reports as PDF

### 5. **New Download Function for READ_ONLY** (index-core.js)
- **Function**: `downloadReportForReadOnly(reportName)`
- **Purpose**: Downloads reports for READ_ONLY users
- **Features**:
  - Calls `/download-report` endpoint
  - Shows loading indicator during generation
  - Downloads as PDF format
  - Shows success/error messages

## Role Permissions Matrix

| Feature | ADMIN | OPERATOR | READ_ONLY |
|---------|-------|----------|-----------|
| View Reports List | ✅ | ✅ | ✅ |
| Upload Reports | ✅ | ✅ | ❌ |
| Generate Reports (with parameters) | ✅ | ✅ | ❌ |
| Download Reports (simple/no params) | ✅ | ✅ | ✅ |
| Edit Reports | ✅ | ✅ | ❌ |
| Delete Reports | ✅ | ✅ | ❌ |
| Use Report Builder | ✅ | ✅ | ❌ |
| Manage Datasources | ✅ | ✅ | ❌ |
| Manage Schedules | ✅ | ✅ | ✅ |
| User Management | ✅ | ❌ | ❌ |

## Existing Role Restrictions (Already in Place)

### Sidebar Navigation (index-sidebar.html)
- **User Management**: Only visible for ADMIN (via `sec:authorize="hasRole('ADMIN')"`)
- **Report Builder**: Only visible for ADMIN and OPERATOR
- **Datasources**: Only visible for ADMIN and OPERATOR
- **Reports**: Visible to all authenticated users
- **Schedules**: Visible to all authenticated users

### Backend Controllers
- **UserController.java**:
  - GET `/users` - ADMIN only
  - GET/POST/PUT/DELETE `/api/users/*` - ADMIN only
  - POST `/api/change-password` - All authenticated users

- **ReportController.java**:
  - POST `/upload` - ADMIN, OPERATOR
  - POST `/generate` - ADMIN, OPERATOR
  - POST `/download-report` - ADMIN, OPERATOR, READ_ONLY (new)
  - DELETE `/reports/{name}` - ADMIN, OPERATOR

## Testing Checklist

### As READ_ONLY User:
- [ ] Can see "Reports" tab
- [ ] Can see list of available reports
- [ ] Can download JRXML files
- [ ] Can see "View" button (👁️) to download as PDF
- [ ] Cannot see Edit button (✏️)
- [ ] Cannot see Delete button (🗑️)
- [ ] Cannot see "Report Builder" tab
- [ ] Cannot see "Datasources" tab
- [ ] Cannot see "User Management" tab
- [ ] Can see "Schedules" tab
- [ ] Can see "Change Password" link

### As OPERATOR User:
- [ ] Can see "Reports" tab
- [ ] Can see Edit button (✏️)
- [ ] Can see Delete button (🗑️)
- [ ] Can see "Report Builder" tab
- [ ] Can see "Datasources" tab
- [ ] Cannot see "User Management" tab
- [ ] Can use Report Builder
- [ ] Can upload reports
- [ ] Can generate reports

### As ADMIN User:
- [ ] Can see all tabs including "User Management"
- [ ] Can access User Management
- [ ] Can create/edit/delete users
- [ ] Can assign roles to users
- [ ] Can do all OPERATOR operations
- [ ] Can upload, generate, edit, delete reports

## Files Modified

1. `/home/hichem/ReportServer/src/main/java/com/reportserver/controller/UserController.java`
   - Added `getCurrentUser()` endpoint

2. `/home/hichem/ReportServer/src/main/java/com/reportserver/controller/ReportController.java`
   - Added `downloadReport()` endpoint for READ_ONLY users

3. `/home/hichem/ReportServer/src/main/resources/static/js/index-core.js`
   - Added `currentUserRole` variable
   - Added `fetchCurrentUser()` function
   - Updated page load to fetch user role
   - Updated `loadReports()` to conditionally show Edit/Delete buttons
   - Added `downloadReportForReadOnly()` function

## Deployment Notes

1. Build the application: `mvn clean install`
2. Run the application: `java -jar target/report-server-1.0.0.jar`
3. No database migration needed - changes are backward compatible
4. Existing users will continue to work with their assigned roles

## Future Enhancements

1. Add role-based filtering to API endpoints
2. Implement audit logging for role-based access
3. Add more granular permissions (e.g., report-level access control)
4. Add role templates for easier role management
5. Implement JWT tokens with role claims for API access

package com.reportserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SchemaIntrospectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(SchemaIntrospectionService.class);

    /**
     * Get all tables from a database connection
     */
    public List<String> getTables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Get tables (TABLE type only, not VIEWs)
            try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    tables.add(tableName);
                }
            }
            
            logger.info("Found {} tables in database", tables.size());
        } catch (SQLException e) {
            logger.error("Error getting tables from database", e);
            throw e;
        }
        
        return tables;
    }

    /**
     * Get all columns for a specific table
     */
    public List<Map<String, String>> getColumns(Connection connection, String tableName) throws SQLException {
        List<Map<String, String>> columns = new ArrayList<>();
        
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            
            try (ResultSet rs = metaData.getColumns(null, null, tableName, "%")) {
                while (rs.next()) {
                    Map<String, String> columnInfo = new HashMap<>();
                    columnInfo.put("name", rs.getString("COLUMN_NAME"));
                    columnInfo.put("type", rs.getString("TYPE_NAME"));
                    columnInfo.put("size", String.valueOf(rs.getInt("COLUMN_SIZE")));
                    columnInfo.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable ? "YES" : "NO");
                    
                    // Map SQL type to Java class name for report generation
                    int sqlType = rs.getInt("DATA_TYPE");
                    columnInfo.put("javaClass", mapSqlTypeToJavaClass(sqlType));
                    
                    columns.add(columnInfo);
                }
            }
            
            logger.info("Found {} columns for table {}", columns.size(), tableName);
        } catch (SQLException e) {
            logger.error("Error getting columns for table: {}", tableName, e);
            throw e;
        }
        
        return columns;
    }

    /**
     * Map SQL data type to Java class name
     */
    private String mapSqlTypeToJavaClass(int sqlType) {
        switch (sqlType) {
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
            case Types.NVARCHAR:
            case Types.NCHAR:
            case Types.LONGNVARCHAR:
                return "java.lang.String";
                
            case Types.INTEGER:
                return "java.lang.Integer";
                
            case Types.BIGINT:
                return "java.lang.Long";
                
            case Types.SMALLINT:
            case Types.TINYINT:
                return "java.lang.Short";
                
            case Types.DOUBLE:
                return "java.lang.Double";
                
            case Types.FLOAT:
            case Types.REAL:
                return "java.lang.Float";
                
            case Types.DECIMAL:
            case Types.NUMERIC:
                return "java.math.BigDecimal";
                
            case Types.DATE:
                return "java.sql.Date";
                
            case Types.TIME:
                return "java.sql.Time";
                
            case Types.TIMESTAMP:
                return "java.sql.Timestamp";
                
            case Types.BOOLEAN:
            case Types.BIT:
                return "java.lang.Boolean";
                
            case Types.BLOB:
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return "java.io.InputStream";
                
            default:
                return "java.lang.Object";
        }
    }
}

package com.reportserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SchemaIntrospectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(SchemaIntrospectionService.class);

    /**
     * Get all tables from a database connection
     */
    public List<String> getTables(Connection connection) throws SQLException {
        Set<String> tables = new LinkedHashSet<>();
        
        try {
            DatabaseMetaData metaData = connection.getMetaData();

            collectTables(metaData, tables, connection.getCatalog(), connection.getSchema(), connection.getSchema());

            if (tables.isEmpty()) {
                collectTables(metaData, tables, connection.getCatalog(), null, connection.getSchema());
            }

            if (tables.isEmpty()) {
                collectTables(metaData, tables, null, connection.getSchema(), connection.getSchema());
            }

            if (tables.isEmpty()) {
                collectTables(metaData, tables, null, null, connection.getSchema());
            }

            if (tables.isEmpty()) {
                try (ResultSet schemas = metaData.getSchemas()) {
                    while (schemas.next()) {
                        collectTables(metaData, tables, connection.getCatalog(), schemas.getString("TABLE_SCHEM"), connection.getSchema());
                    }
                }
            }

            if (tables.isEmpty()) {
                try (ResultSet catalogs = metaData.getCatalogs()) {
                    while (catalogs.next()) {
                        collectTables(metaData, tables, catalogs.getString(1), connection.getSchema(), connection.getSchema());
                    }
                }
            }
            
            logger.info("Found {} tables in database", tables.size());
        } catch (SQLException e) {
            logger.error("Error getting tables from database", e);
            throw e;
        }
        
        return new ArrayList<>(tables);
    }

    /**
     * Get all columns for a specific table
     */
    public List<Map<String, String>> getColumns(Connection connection, String tableName) throws SQLException {
        List<Map<String, String>> columns = new ArrayList<>();
        
        try {
            DatabaseMetaData metaData = connection.getMetaData();

            TableReference tableReference = TableReference.parse(tableName);

            collectColumns(metaData, columns, tableReference.catalog, tableReference.schema, tableReference.table);

            if (columns.isEmpty()) {
                collectColumns(metaData, columns, connection.getCatalog(), connection.getSchema(), tableReference.table);
            }

            if (columns.isEmpty()) {
                collectColumns(metaData, columns, connection.getCatalog(), null, tableReference.table);
            }

            if (columns.isEmpty()) {
                collectColumns(metaData, columns, null, connection.getSchema(), tableReference.table);
            }

            if (columns.isEmpty()) {
                collectColumns(metaData, columns, null, null, tableReference.table);
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

    private void collectTables(
            DatabaseMetaData metaData,
            Set<String> tables,
            String catalog,
            String schema,
            String currentSchema) throws SQLException {
        try (ResultSet rs = metaData.getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String tableSchema = rs.getString("TABLE_SCHEM");

                if (isSystemSchema(tableSchema)) {
                    continue;
                }

                tables.add(qualifyTableName(tableSchema, currentSchema, tableName));
            }
        }
    }

    private void collectColumns(
            DatabaseMetaData metaData,
            List<Map<String, String>> columns,
            String catalog,
            String schema,
            String tableName) throws SQLException {
        try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                Map<String, String> columnInfo = new HashMap<>();
                columnInfo.put("name", rs.getString("COLUMN_NAME"));
                columnInfo.put("type", rs.getString("TYPE_NAME"));
                columnInfo.put("size", String.valueOf(rs.getInt("COLUMN_SIZE")));
                columnInfo.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable ? "YES" : "NO");

                int sqlType = rs.getInt("DATA_TYPE");
                columnInfo.put("javaClass", mapSqlTypeToJavaClass(sqlType));

                columns.add(columnInfo);
            }
        }
    }

    private String qualifyTableName(String schema, String currentSchema, String tableName) {
        if (schema == null || schema.isBlank() || schema.equalsIgnoreCase(currentSchema)) {
            return tableName;
        }
        return schema + "." + tableName;
    }

    private boolean isSystemSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return false;
        }

        String normalized = schema.toLowerCase();
        return normalized.equals("information_schema")
            || normalized.equals("pg_catalog")
            || normalized.equals("sys")
            || normalized.equals("mysql")
            || normalized.equals("performance_schema");
    }

    private static class TableReference {
        private final String catalog;
        private final String schema;
        private final String table;

        private TableReference(String catalog, String schema, String table) {
            this.catalog = catalog;
            this.schema = schema;
            this.table = table;
        }

        private static TableReference parse(String tableName) {
            String[] parts = tableName.split("\\.");
            if (parts.length >= 3) {
                return new TableReference(parts[0], parts[1], parts[2]);
            }
            if (parts.length == 2) {
                return new TableReference(null, parts[0], parts[1]);
            }
            return new TableReference(null, null, tableName);
        }
    }
}

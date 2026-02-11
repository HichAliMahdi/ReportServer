package com.reportserver.service;

import com.reportserver.model.DataSource;
import com.reportserver.model.DataSourceType;
import com.reportserver.repository.DataSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.*;

@Service
public class DataSourceService {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceService.class);

    @Autowired
    private DataSourceRepository dataSourceRepository;
    
    @Autowired
    private JRDataSourceProviderService jrDataSourceProviderService;

    /**
     * Get all datasources
     */
    public List<DataSource> getAllDataSources() {
        return dataSourceRepository.findAll();
    }

    /**
     * Get datasource by ID
     */
    public Optional<DataSource> getDataSourceById(Long id) {
        return dataSourceRepository.findById(id);
    }

    /**
     * Get datasource by name
     */
    public Optional<DataSource> getDataSourceByName(String name) {
        return dataSourceRepository.findByName(name);
    }

    /**
     * Create a new datasource
     */
    @Transactional
    public DataSource createDataSource(DataSource dataSource) {
        if (dataSourceRepository.existsByName(dataSource.getName())) {
            throw new IllegalArgumentException("Datasource with name '" + dataSource.getName() + "' already exists");
        }
        return dataSourceRepository.save(dataSource);
    }

    /**
     * Update an existing datasource
     */
    @Transactional
    public DataSource updateDataSource(Long id, DataSource dataSource) {
        DataSource existing = dataSourceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Datasource not found with id: " + id));
        
        // Check if name is being changed and if new name already exists
        if (!existing.getName().equals(dataSource.getName()) && 
            dataSourceRepository.existsByName(dataSource.getName())) {
            throw new IllegalArgumentException("Datasource with name '" + dataSource.getName() + "' already exists");
        }
        
        existing.setName(dataSource.getName());
        existing.setType(dataSource.getType());
        existing.setUrl(dataSource.getUrl());
        existing.setUsername(dataSource.getUsername());
        existing.setFilePath(dataSource.getFilePath());
        existing.setConfiguration(dataSource.getConfiguration());
        
        // Only update password if provided (not empty)
        if (dataSource.getPassword() != null && !dataSource.getPassword().trim().isEmpty()) {
            existing.setPassword(dataSource.getPassword());
        }
        
        existing.setDriverClassName(dataSource.getDriverClassName());
        
        return dataSourceRepository.save(existing);
    }

    /**
     * Delete a datasource
     */
    @Transactional
    public void deleteDataSource(Long id) {
        if (!dataSourceRepository.existsById(id)) {
            throw new IllegalArgumentException("Datasource not found with id: " + id);
        }
        dataSourceRepository.deleteById(id);
    }

    /**
     * Test connection for a datasource
     */
    public boolean testConnection(DataSource dataSource) {
        return jrDataSourceProviderService.testDataSource(dataSource);
    }

    /**
     * Get a connection for a specific datasource (JDBC only)
     */
    public Connection getConnection(Long datasourceId) throws SQLException {
        DataSource dataSource = dataSourceRepository.findById(datasourceId)
            .orElseThrow(() -> new IllegalArgumentException("Datasource not found with id: " + datasourceId));
        
        if (dataSource.getType() != DataSourceType.JDBC) {
            throw new IllegalArgumentException("Datasource is not a JDBC type: " + dataSource.getType());
        }
        
        try {
            Class.forName(dataSource.getDriverClassName());
            return DriverManager.getConnection(
                dataSource.getUrl(),
                dataSource.getUsername(),
                dataSource.getPassword()
            );
        } catch (ClassNotFoundException e) {
            logger.error("Driver class not found: " + dataSource.getDriverClassName(), e);
            throw new SQLException("Driver not found: " + dataSource.getDriverClassName(), e);
        }
    }

    /**
     * Execute a query on a datasource and return results
     */
    public Map<String, Object> executeQuery(DataSource dataSource, String query, int maxRows) throws SQLException {
        if (dataSource.getType() != DataSourceType.JDBC) {
            throw new IllegalArgumentException("Query execution is only supported for JDBC datasources");
        }

        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, String>> columns = new ArrayList<>();
        boolean truncated = false;
        
        try {
            // Load the JDBC driver
            Class.forName(dataSource.getDriverClassName());
        } catch (ClassNotFoundException e) {
            logger.error("Driver class not found: " + dataSource.getDriverClassName(), e);
            throw new SQLException("Driver not found: " + dataSource.getDriverClassName(), e);
        }
        
        try (Connection connection = DriverManager.getConnection(
                dataSource.getUrl(),
                dataSource.getUsername(),
                dataSource.getPassword());
             Statement statement = connection.createStatement()) {
            
            // Set max rows to prevent memory issues with large result sets
            statement.setMaxRows(maxRows);
            
            try (ResultSet resultSet = statement.executeQuery(query)) {
            
                // Extract column metadata
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                for (int i = 1; i <= columnCount; i++) {
                    Map<String, String> column = new HashMap<>();
                    column.put("name", metaData.getColumnName(i));
                    column.put("type", metaData.getColumnTypeName(i));
                    column.put("label", metaData.getColumnLabel(i));
                    columns.add(column);
                }
                
                // Extract rows (up to maxRows)
                int rowCount = 0;
                while (resultSet.next()) {
                    rowCount++;
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = resultSet.getObject(i);
                        row.put(columnName, value);
                    }
                    rows.add(row);
                }
                
                // Check if results were truncated
                truncated = rowCount >= maxRows;
            }
            
        } catch (SQLException e) {
            logger.error("Error executing query: " + query, e);
            throw e;
        }
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        Map<String, Object> result = new HashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("executionTime", executionTime);
        result.put("truncated", truncated);
        
        return result;
    }
}

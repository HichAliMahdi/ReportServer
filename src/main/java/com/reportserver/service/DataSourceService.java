package com.reportserver.service;

import com.reportserver.model.DataSource;
import com.reportserver.repository.DataSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Service
public class DataSourceService {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceService.class);

    @Autowired
    private DataSourceRepository dataSourceRepository;

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
        existing.setUrl(dataSource.getUrl());
        existing.setUsername(dataSource.getUsername());
        existing.setPassword(dataSource.getPassword());
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
        Connection connection = null;
        try {
            Class.forName(dataSource.getDriverClassName());
            connection = DriverManager.getConnection(
                dataSource.getUrl(),
                dataSource.getUsername(),
                dataSource.getPassword()
            );
            return connection != null && !connection.isClosed();
        } catch (ClassNotFoundException e) {
            logger.error("Driver class not found: " + dataSource.getDriverClassName(), e);
            return false;
        } catch (SQLException e) {
            logger.error("Failed to connect to datasource: " + dataSource.getName(), e);
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    /**
     * Get a connection for a specific datasource
     */
    public Connection getConnection(Long datasourceId) throws SQLException {
        DataSource dataSource = dataSourceRepository.findById(datasourceId)
            .orElseThrow(() -> new IllegalArgumentException("Datasource not found with id: " + datasourceId));
        
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
}

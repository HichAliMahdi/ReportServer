package com.reportserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Service
public class DatabaseConnectionService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionService.class);

    @Autowired
    private DataSource dataSource;

    /**
     * Get a database connection from the configured datasource
     * @return Connection object
     * @throws SQLException if connection cannot be established
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Close a database connection
     * @param connection Connection to close
     */
    public void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.error("Error closing database connection", e);
            }
        }
    }

    /**
     * Test if database connection is available
     * @return true if connection is successful, false otherwise
     */
    public boolean testConnection() {
        Connection connection = null;
        try {
            connection = getConnection();
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            logger.error("Database connection test failed", e);
            return false;
        } finally {
            closeConnection(connection);
        }
    }
}

package com.reportserver.service;

import com.reportserver.model.DataSource;
import com.reportserver.model.DataSourceType;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.data.JRCsvDataSource;
import net.sf.jasperreports.engine.data.JRXmlDataSource;
import net.sf.jasperreports.engine.data.JsonDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

/**
 * Service for providing different types of JRData Sources for JasperReports
 */
@Service
public class JRDataSourceProviderService {

    private static final Logger logger = LoggerFactory.getLogger(JRDataSourceProviderService.class);
    private static final String DATA_FILES_DIR = "data/datasource_files/";

    /**
     * Get a JRDataSource based on the datasource type and configuration
     */
    public Object getDataSource(DataSource dataSource, Map<String, Object> parameters) throws Exception {
        if (dataSource == null) {
            return new JREmptyDataSource();
        }

        switch (dataSource.getType()) {
            case JDBC:
                return getJdbcConnection(dataSource);

            case CSV:
                return getCsvDataSource(dataSource);

            case XML:
                return getXmlDataSource(dataSource, parameters);

            case JSON:
                return getJsonDataSource(dataSource);

            case EMPTY:
                return new JREmptyDataSource();

            case COLLECTION:
                // For collection data sources, the collection should be passed in parameters
                Object collectionData = parameters.get("REPORT_DATA_SOURCE");
                if (collectionData instanceof JRDataSource) {
                    return collectionData;
                }
                return new JREmptyDataSource();

            case MONGODB:
                return getMongoDBDataSource(dataSource, parameters);

            case REST_API:
                return getRestApiDataSource(dataSource);

            case HIBERNATE:
                return getHibernateConnection(dataSource, parameters);

            default:
                logger.warn("Unknown datasource type: {}, using empty datasource", dataSource.getType());
                return new JREmptyDataSource();
        }
    }

    /**
     * Get JDBC connection
     */
    private Connection getJdbcConnection(DataSource dataSource) throws SQLException {
        try {
            Class.forName(dataSource.getDriverClassName());
            Connection conn = DriverManager.getConnection(
                    dataSource.getUrl(),
                    dataSource.getUsername(),
                    dataSource.getPassword()
            );
            logger.info("JDBC connection established for datasource: {}", dataSource.getName());
            return conn;
        } catch (ClassNotFoundException e) {
            logger.error("Driver class not found: {}", dataSource.getDriverClassName(), e);
            throw new SQLException("Driv not found: " + dataSource.getDriverClassName(), e);
        }
    }

    /**
     * Get CSV data source
     */
    private JRCsvDataSource getCsvDataSource(DataSource dataSource) throws Exception {
        String filePath = dataSource.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("CSV file path is required");
        }

        File file = new File(DATA_FILES_DIR + filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("CSV file not found: " + filePath);
        }

        JRCsvDataSource csvDataSource = new JRCsvDataSource(file);
        csvDataSource.setUseFirstRowAsHeader(true);
        
        // Parse configuration if provided (e.g., field delimiter, record delimiter)
        if (dataSource.getConfiguration() != null) {
            // Simple parsing of configuration (can be enhanced with JSON parsing)
            String config = dataSource.getConfiguration();
            if (config.contains("fieldDelimiter")) {
                // Extract delimiter (simple approach)
                csvDataSource.setFieldDelimiter(',');
            }
        }

        logger.info("CSV datasource created for file: {}", filePath);
        return csvDataSource;
    }

    /**
     * Get XML data source
     */
    private JRXmlDataSource getXmlDataSource(DataSource dataSource, Map<String, Object> parameters) throws Exception {
        String filePath = dataSource.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("XML file path is required");
        }

        File file = new File(DATA_FILES_DIR + filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("XML file not found: " + filePath);
        }

        // Get XPath select expression from configuration or use default
        String selectExpression = "/data/record";
        if (dataSource.getConfiguration() != null && !dataSource.getConfiguration().isEmpty()) {
            selectExpression = dataSource.getConfiguration();
        }

        JRXmlDataSource xmlDataSource = new JRXmlDataSource(file, selectExpression);
        logger.info("XML datasource created for file: {} with XPath: {}", filePath, selectExpression);
        return xmlDataSource;
    }

    /**
     * Get JSON data source
     */
    private JsonDataSource getJsonDataSource(DataSource dataSource) throws Exception {
        String filePath = dataSource.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("JSON file path is required");
        }

        File file = new File(DATA_FILES_DIR + filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("JSON file not found: " + filePath);
        }

        // Get JSON select expression from configuration or use default
        String selectExpression = "$.*";
        if (dataSource.getConfiguration() != null && !dataSource.getConfiguration().isEmpty()) {
            selectExpression = dataSource.getConfiguration();
        }

        FileInputStream fis = new FileInputStream(file);
        JsonDataSource jsonDataSource = new JsonDataSource(fis, selectExpression);
        logger.info("JSON datasource created for file: {} with expression: {}", filePath, selectExpression);
        return jsonDataSource;
    }

    /**
     * Get MongoDB data source
     * Configuration format: {"connectionString": "mongodb://...", "database": "dbname", "collection": "collname", "query": "{}"}
     */
    private JRDataSource getMongoDBDataSource(DataSource dataSource, Map<String, Object> parameters) throws Exception {
        // MongoDB support requires additional dependencies
        // For now, we support it via REST API wrapper or return empty with instructions
        logger.warn("MongoDB datasource requires custom implementation. URL: {}, Configuration: {}", 
                    dataSource.getUrl(), dataSource.getConfiguration());
        
        // Expected configuration format in configuration field:
        // {"database": "mydb", "collection": "mycollection", "query": "{\"status\": \"active\"}"}
        
        if (dataSource.getUrl() == null || dataSource.getUrl().isEmpty()) {
            throw new IllegalArgumentException("MongoDB connection string (URL) is required. Example: mongodb://localhost:27017");
        }
        
        // Implementation note: To use MongoDB, you need to:
        // 1. Add MongoDB Java driver dependency to pom.xml
        // 2. Fetch data from MongoDB and convert to List<Map<String, Object>>
        // 3. Use JRBeanCollectionDataSource with the list
        
        throw new UnsupportedOperationException(
            "MongoDB support requires additional configuration. " +
            "Please implement custom MongoDB query logic and pass results as COLLECTION type. " +
            "Connection string: " + dataSource.getUrl()
        );
    }

    /**
     * Get REST API data source
     * Fetches JSON or XML from HTTP endpoint and creates appropriate datasource
     */
    private JRDataSource getRestApiDataSource(DataSource dataSource) throws Exception {
        String apiUrl = dataSource.getUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            throw new IllegalArgumentException("REST API URL is required");
        }

        logger.info("Fetching data from REST API: {}", apiUrl);

        // Create connection with timeout
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);

        // Add authentication if username/password provided  
        if (dataSource.getUsername() != null && !dataSource.getUsername().isEmpty()) {
            String auth = dataSource.getUsername() + ":" + 
                         (dataSource.getPassword() != null ? dataSource.getPassword() : "");
            String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("REST API returned error code: " + responseCode);
        }

        // Determine content type
        String contentType = connection.getContentType();
        InputStream inputStream = connection.getInputStream();

        // Get select expression from configuration
        String selectExpression = dataSource.getConfiguration();

        if (contentType != null && contentType.contains("json")) {
            // JSON response
            if (selectExpression == null || selectExpression.isEmpty()) {
                selectExpression = "$.*";
            }
            JsonDataSource jsonDataSource = new JsonDataSource(inputStream, selectExpression);
            logger.info("REST API datasource created (JSON) with expression: {}", selectExpression);
            return jsonDataSource;
        } else if (contentType != null && contentType.contains("xml")) {
            // XML response
            if (selectExpression == null || selectExpression.isEmpty()) {
                selectExpression = "/data/record";
            }
            JRXmlDataSource xmlDataSource = new JRXmlDataSource(inputStream, selectExpression);
            logger.info("REST API datasource created (XML) with XPath: {}", selectExpression);
            return xmlDataSource;
        } else {
            // Default to JSON
            if (selectExpression == null || selectExpression.isEmpty()) {
                selectExpression = "$.*";
            }
            JsonDataSource jsonDataSource = new JsonDataSource(inputStream, selectExpression);
            logger.info("REST API datasource created (default JSON) with expression: {}", selectExpression);
            return jsonDataSource;
        }
    }

    /**
     * Get Hibernate connection/session
     * For Hibernate, we expect the session to be provided via parameters
     * or we use JDBC connection with Hibernate configuration
     */
    private Object getHibernateConnection(DataSource dataSource, Map<String, Object> parameters) throws Exception {
        // Check if Hibernate session is provided in parameters
        Object hibernateSession = parameters.get("HIBERNATE_SESSION");
        if (hibernateSession != null) {
            logger.info("Using Hibernate session from parameters for datasource: {}", dataSource.getName());
            return hibernateSession;
        }

        // If no session provided, use JDBC connection with Hibernate driver
        if (dataSource.getUrl() != null && !dataSource.getUrl().isEmpty()) {
            logger.info("Using JDBC connection for Hibernate datasource: {}", dataSource.getName());
            return getJdbcConnection(dataSource);
        }

        throw new IllegalArgumentException(
            "Hibernate datasource requires either a session in parameters (HIBERNATE_SESSION) " +
            "or JDBC configuration (URL, username, password, driver)"
        );
    }

    /**
     * Test connection/datasource validity
     */
    public boolean testDataSource(DataSource dataSource) {
        try {
            switch (dataSource.getType()) {
                case JDBC:
                case HIBERNATE:
                    Connection conn = getJdbcConnection(dataSource);
                    boolean valid = conn != null && !conn.isClosed();
                    if (conn != null) conn.close();
                    return valid;

                case CSV:
                case XML:
                case JSON:
                    File file = new File(DATA_FILES_DIR + dataSource.getFilePath());
                    return file.exists() && file.canRead();

                case REST_API:
                    try {
                        URL url = new URL(dataSource.getUrl());
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("HEAD");
                        connection.setConnectTimeout(5000);
                        int responseCode = connection.getResponseCode();
                        return responseCode == 200 || responseCode == 301 || responseCode == 302;
                    } catch (Exception e) {
                        return false;
                    }

                case MONGODB:
                    // MongoDB test would require MongoDB driver
                    logger.warn("MongoDB test connection not implemented, returning true");
                    return true;

                case EMPTY:
                    return true;

                default:
                    return false;
            }
        } catch (Exception e) {
            logger.error("Error testing datasource: {}", dataSource.getName(), e);
            return false;
        }
    }

    /**
     * Create the data files directory if it doesn't exist
     */
    public void ensureDataFilesDirectoryExists() {
        try {
            Files.createDirectories(Paths.get(DATA_FILES_DIR));
        } catch (Exception e) {
            logger.error("Error creating data files directory", e);
        }
    }

    public String getDataFilesDirectory() {
        return DATA_FILES_DIR;
    }
}

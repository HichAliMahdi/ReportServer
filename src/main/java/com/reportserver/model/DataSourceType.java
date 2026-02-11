package com.reportserver.model;

/**
 * Enum representing different types of data sources supported by JasperReports
 */
public enum DataSourceType {
    /**
     * JDBC connection to relational databases (MySQL, PostgreSQL, etc.)
     */
    JDBC,
    
    /**
     * CSV (Comma-Separated Values) file data source
     */
    CSV,
    
    /**
     * XML file data source
     */
    XML,
    
    /**
     * JSON file data source
     */
    JSON,
    
    /**
     * Empty data source (no external data, useful for static reports)
     */
    EMPTY,
    
    /**
     * Java Collection data source (List, ArrayList, etc.)
     */
    COLLECTION,
    
    /**
     * MongoDB NoSQL database data source
     */
    MONGODB,
    
    /**
     * REST API data source (fetches JSON/XML from HTTP endpoints)
     */
    REST_API,
    
    /**
     * Hibernate ORM data source
     */
    HIBERNATE
}

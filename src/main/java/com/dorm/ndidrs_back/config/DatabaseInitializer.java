package com.dorm.ndidrs_back.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class DatabaseInitializer {

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void init() {
        try (Connection conn = dataSource.getConnection()) {
            migrateDormHazardImage(conn);
            migrateDormCheckRecordImage(conn);
            migrateDormLeaveProofImage(conn);
            System.out.println("Database migration completed successfully");
        } catch (SQLException e) {
            System.err.println("Database migration failed: " + e.getMessage());
        }
    }

    private void migrateDormHazardImage(Connection conn) throws SQLException {
        if (!isColumnType(conn, "dorm_hazard", "image", "MEDIUMTEXT")) {
            executeSQL(conn, "ALTER TABLE dorm_hazard MODIFY COLUMN image MEDIUMTEXT DEFAULT NULL COMMENT '隐患图片'");
            System.out.println("migrated dorm_hazard.image to MEDIUMTEXT");
        }
    }

    private void migrateDormCheckRecordImage(Connection conn) throws SQLException {
        if (!isColumnType(conn, "dorm_check_record", "image", "MEDIUMTEXT")) {
            executeSQL(conn, "ALTER TABLE dorm_check_record MODIFY COLUMN image MEDIUMTEXT DEFAULT NULL COMMENT '现场图片'");
            System.out.println("migrated dorm_check_record.image to MEDIUMTEXT");
        }
    }

    private void migrateDormLeaveProofImage(Connection conn) throws SQLException {
        if (!isColumnType(conn, "dorm_leave", "proof_image", "MEDIUMTEXT")) {
            executeSQL(conn, "ALTER TABLE dorm_leave MODIFY COLUMN proof_image MEDIUMTEXT DEFAULT NULL COMMENT '凭证照片'");
            System.out.println("migrated dorm_leave.proof_image to MEDIUMTEXT");
        }
    }

    private boolean isColumnType(Connection conn, String tableName, String columnName, String expectedType) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        java.sql.ResultSet rs = metaData.getColumns(null, null, tableName, columnName);
        if (rs.next()) {
            String typeName = rs.getString("TYPE_NAME").toUpperCase();
            return typeName.contains(expectedType);
        }
        return false;
    }

    private void executeSQL(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
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
            createChatMessageTable(conn);
            System.out.println("Database migration completed successfully");
        } catch (SQLException e) {
            System.err.println("Database migration failed: " + e.getMessage());
        }
    }

    private void createChatMessageTable(Connection conn) throws SQLException {
        if (!tableExists(conn, "chat_message")) {
            executeSQL(conn, "CREATE TABLE chat_message ("
                    + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                    + "room_id BIGINT NOT NULL COMMENT '宿舍ID', "
                    + "sender_id BIGINT NOT NULL COMMENT '发送者ID', "
                    + "sender_name VARCHAR(64) COMMENT '发送者姓名', "
                    + "sender_avatar VARCHAR(255) COMMENT '发送者头像', "
                    + "msg_type VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT '消息类型', "
                    + "content MEDIUMTEXT COMMENT '消息内容', "
                    + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP, "
                    + "INDEX idx_room_time (room_id, create_time)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宿舍聊天消息'");
            System.out.println("created chat_message table");
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        java.sql.ResultSet rs = metaData.getTables(null, null, tableName, null);
        return rs.next();
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
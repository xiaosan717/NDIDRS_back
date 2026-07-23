CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(128),
    real_name VARCHAR(64),
    role VARCHAR(32),
    class_name VARCHAR(64),
    grade VARCHAR(32),
    building VARCHAR(64),
    room VARCHAR(32),
    college VARCHAR(64),
    phone VARCHAR(20),
    avatar VARCHAR(255),
    status INT DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_college (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    college_name VARCHAR(64) NOT NULL,
    sort_order INT DEFAULT 0,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_class (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    class_name VARCHAR(64) NOT NULL,
    college_name VARCHAR(64),
    grade VARCHAR(32),
    sort_order INT DEFAULT 0,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_building (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    building_name VARCHAR(64) NOT NULL,
    sort_order INT DEFAULT 0,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(64) NOT NULL UNIQUE,
    config_value VARCHAR(255),
    config_desc VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dorm_room (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_number VARCHAR(32) NOT NULL,
    building VARCHAR(64),
    floor INT,
    leader_id BIGINT,
    capacity INT DEFAULT 4,
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dorm_check_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT,
    check_date DATE,
    student_id BIGINT,
    status VARCHAR(32),
    remark VARCHAR(255),
    image VARCHAR(255),
    submitter_id BIGINT,
    submit_time DATETIME,
    modifier_id BIGINT,
    modify_time DATETIME,
    modify_remark VARCHAR(255),
    is_modified INT DEFAULT 0,
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dorm_leave (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT,
    leave_type VARCHAR(32),
    reason TEXT,
    proof_image VARCHAR(255),
    start_time DATETIME,
    end_time DATETIME,
    status VARCHAR(32),
    approver_id BIGINT,
    approve_time DATETIME,
    approve_comment VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dorm_hazard (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT,
    room_id BIGINT,
    hazard_type VARCHAR(32),
    description TEXT,
    image VARCHAR(255),
    status VARCHAR(32),
    handler_id BIGINT,
    handle_time DATETIME,
    handle_remark VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL COMMENT '宿舍ID',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    sender_name VARCHAR(64) COMMENT '发送者姓名',
    sender_avatar VARCHAR(255) COMMENT '发送者头像',
    msg_type VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT '消息类型: TEXT/IMAGE/VIDEO/EMOJI',
    content MEDIUMTEXT COMMENT '消息内容(文本/HTML/URL)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_time (room_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宿舍聊天消息';

-- ===================== 测试数据 =====================

-- 学院
INSERT IGNORE INTO sys_college (id, college_name, sort_order, create_time) VALUES
(1, '计算机学院', 1, NOW()),
(2, '机械学院', 2, NOW()),
(3, '经济管理学院', 3, NOW());

-- 班级
INSERT IGNORE INTO sys_class (id, class_name, college_name, grade, sort_order, create_time) VALUES
(1, '软件工程1班', '计算机学院', '2022级', 1, NOW()),
(2, '软件工程2班', '计算机学院', '2022级', 2, NOW()),
(3, '机械设计1班', '机械学院', '2022级', 1, NOW()),
(4, '工商管理1班', '经济管理学院', '2022级', 1, NOW());

-- 楼栋
INSERT IGNORE INTO sys_building (id, building_name, sort_order, create_time) VALUES
(1, '1号楼', 1, NOW()),
(2, '2号楼', 2, NOW()),
(3, '3号楼', 3, NOW());

-- 宿舍（先不设 leader_id，后面 UPDATE）
INSERT IGNORE INTO dorm_room (id, room_number, building, floor, leader_id, capacity, create_time, update_time) VALUES
(1, '101', '1号楼', 1, NULL, 4, NOW(), NOW()),
(2, '102', '1号楼', 1, NULL, 4, NOW(), NOW()),
(3, '201', '1号楼', 2, NULL, 4, NOW(), NOW()),
(4, '101', '2号楼', 1, NULL, 4, NOW(), NOW()),
(5, '102', '2号楼', 1, NULL, 4, NOW(), NOW());

-- 用户（所有人密码都是 123456）
-- 管理员
INSERT IGNORE INTO sys_user (id, username, password, email, real_name, role, class_name, grade, building, room, college, phone, status, create_time, update_time) VALUES
(1, 'admin', '123456', 'admin@school.edu', '系统管理员', 'ADMIN', NULL, NULL, NULL, NULL, NULL, '13800000001', 1, NOW(), NOW());

-- 辅导员（计算机学院软件工程1班）
INSERT IGNORE INTO sys_user (id, username, password, email, real_name, role, class_name, grade, building, room, college, phone, status, create_time, update_time) VALUES
(2, '计算机学院2022级软件工程1班counselor', '123456', 'counselor1@school.edu', '李辅导员', 'COUNSELOR', '软件工程1班', '2022级', NULL, NULL, '计算机学院', '13800000002', 1, NOW(), NOW()),
(3, '机械学院2022级机械设计1班counselor', '123456', 'counselor2@school.edu', '王辅导员', 'COUNSELOR', '机械设计1班', '2022级', NULL, NULL, '机械学院', '13800000003', 1, NOW(), NOW());

-- 宿管
INSERT IGNORE INTO sys_user (id, username, password, email, real_name, role, class_name, grade, building, room, college, phone, status, create_time, update_time) VALUES
(4, '1号楼manager', '123456', 'manager1@school.edu', '张宿管', 'DORM_MANAGER', NULL, NULL, '1号楼', NULL, NULL, '13800000004', 1, NOW(), NOW()),
(5, '2号楼manager', '123456', 'manager2@school.edu', '赵宿管', 'DORM_MANAGER', NULL, NULL, '2号楼', NULL, NULL, '13800000005', 1, NOW(), NOW());

-- 宿舍长
INSERT IGNORE INTO sys_user (id, username, password, email, real_name, role, class_name, grade, building, room, college, phone, status, create_time, update_time) VALUES
(6, 'dormleader01', '123456', 'leader01@school.edu', '陈宿舍长', 'DORM_LEADER', '软件工程1班', '2022级', '1号楼', '101', '计算机学院', '13800000006', 1, NOW(), NOW()),
(7, 'dormleader02', '123456', 'leader02@school.edu', '刘宿舍长', 'DORM_LEADER', '软件工程1班', '2022级', '1号楼', '102', '计算机学院', '13800000007', 1, NOW(), NOW());

-- 学生
INSERT IGNORE INTO sys_user (id, username, password, email, real_name, role, class_name, grade, building, room, college, phone, status, create_time, update_time) VALUES
(8,  'student01', '123456', 'stu01@school.edu', '张三', 'STUDENT', '软件工程1班', '2022级', '1号楼', '101', '计算机学院', '13800000008', 1, NOW(), NOW()),
(9,  'student02', '123456', 'stu02@school.edu', '李四', 'STUDENT', '软件工程1班', '2022级', '1号楼', '101', '计算机学院', '13800000009', 1, NOW(), NOW()),
(10, 'student03', '123456', 'stu03@school.edu', '王五', 'STUDENT', '软件工程1班', '2022级', '1号楼', '101', '计算机学院', '13800000010', 1, NOW(), NOW()),
(11, 'student04', '123456', 'stu04@school.edu', '赵六', 'STUDENT', '软件工程1班', '2022级', '1号楼', '102', '计算机学院', '13800000011', 1, NOW(), NOW()),
(12, 'student05', '123456', 'stu05@school.edu', '孙七', 'STUDENT', '软件工程2班', '2022级', '1号楼', '201', '计算机学院', '13800000012', 1, NOW(), NOW()),
(13, 'student06', '123456', 'stu06@school.edu', '周八', 'STUDENT', '机械设计1班', '2022级', '2号楼', '101', '机械学院', '13800000013', 1, NOW(), NOW());

-- 绑定宿舍长到宿舍
UPDATE dorm_room SET leader_id = 6 WHERE id = 1;
UPDATE dorm_room SET leader_id = 7 WHERE id = 2;

-- 系统配置
INSERT IGNORE INTO sys_config (id, config_key, config_value, config_desc, create_time, update_time) VALUES
(1, 'check_time_start', '21:00', '查寝开始时间', NOW(), NOW()),
(2, 'check_time_end', '23:00', '查寝结束时间', NOW(), NOW()),
(3, 'system_name', '夜查寝数据报送系统', '系统名称', NOW(), NOW());

-- 查寝记录（近7天，覆盖多种状态）
INSERT IGNORE INTO dorm_check_record (room_id, check_date, student_id, status, remark, submitter_id, submit_time, is_modified, create_time) VALUES
(1, CURDATE(),       8,  'IN_ROOM', NULL,       6, NOW(), 0, NOW()),
(1, CURDATE(),       9,  'LATE',    '22:30才回', 6, NOW(), 0, NOW()),
(1, CURDATE(),       10, 'IN_ROOM', NULL,       6, NOW(), 0, NOW()),
(2, CURDATE(),       11, 'ABSENT',  '未归宿',   7, NOW(), 0, NOW()),
(1, DATE_SUB(CURDATE(),INTERVAL 1 DAY), 8,  'IN_ROOM', NULL, 6, NOW(), 0, NOW()),
(1, DATE_SUB(CURDATE(),INTERVAL 1 DAY), 9,  'LEAVE',   NULL, 6, NOW(), 0, NOW()),
(1, DATE_SUB(CURDATE(),INTERVAL 1 DAY), 10, 'IN_ROOM', NULL, 6, NOW(), 0, NOW()),
(2, DATE_SUB(CURDATE(),INTERVAL 1 DAY), 11, 'IN_ROOM', NULL, 7, NOW(), 0, NOW()),
(1, DATE_SUB(CURDATE(),INTERVAL 2 DAY), 8,  'IN_ROOM', NULL, 6, NOW(), 0, NOW()),
(1, DATE_SUB(CURDATE(),INTERVAL 2 DAY), 9,  'IN_ROOM', NULL, 6, NOW(), 0, NOW()),
(1, DATE_SUB(CURDATE(),INTERVAL 3 DAY), 10, 'LATE',    NULL, 6, NOW(), 0, NOW()),
(2, DATE_SUB(CURDATE(),INTERVAL 3 DAY), 11, 'IN_ROOM', NULL, 7, NOW(), 0, NOW());

-- 请假记录（不同状态）
INSERT IGNORE INTO dorm_leave (student_id, leave_type, reason, start_time, end_time, status, approver_id, approve_time, approve_comment, create_time, update_time) VALUES
(9,  'SICK',     '感冒发烧需要回家休养', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), 'APPROVED',          2, NOW(), '已批准，注意休息', NOW(), NOW()),
(10, 'PERSONAL', '家中有事需要请假',     NOW(),                           DATE_ADD(NOW(), INTERVAL 1 DAY), 'PENDING',           NULL, NULL, NULL,               NOW(), NOW()),
(11, 'OTHER',    '参加校外比赛',         DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 'REJECTED',          2, NOW(), '材料不齐，驳回',   NOW(), NOW()),
(8,  'PERSONAL', '回家处理个人事务',     DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY), 'COUNSELOR_APPROVED',2, NOW(), '辅导员已批准',     NOW(), NOW());

-- 隐患记录（不同状态）
INSERT IGNORE INTO dorm_hazard (student_id, room_id, hazard_type, description, status, handler_id, handle_time, handle_remark, create_time, update_time) VALUES
(8,  1, 'ELECTRICAL', '宿舍插座有烧焦气味',   'REPORTED',      NULL, NULL, NULL,         NOW(), NOW()),
(9,  1, 'FIRE',       '走廊灭火器已过期',     'PROCESSING',    4,    NOW(), '已联系维修', NOW(), NOW()),
(11, 2, 'HYGIENE',    '宿舍卫生较差',         'COMPLETED',     4,    NOW(), '已整改完毕', NOW(), NOW()),
(10, 1, 'FACILITY',   '宿舍门锁损坏无法上锁', 'MANAGER_APPROVED', 4, NOW(), '已上报处理', NOW(), NOW()),
(13, 4, 'ELECTRICAL', '违规使用大功率电器',   'REPORTED',      NULL, NULL, NULL,         NOW(), NOW());

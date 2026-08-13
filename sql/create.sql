/*
 Navicat Premium Data Transfer

 Source Server         : 本地测试环境8.0
 Source Server Type    : MySQL
 Source Server Version : 80027
 Source Host           : localhost:3306
 Source Schema         : fishhub

 Target Server Type    : MySQL
 Target Server Version : 80027
 File Encoding         : 65001

 Clean bootstrap script: schema plus required system seed data only.
 It intentionally contains no users, notes, comments, relationships, counts, or media URLs.
*/

SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS `fishhub` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fishhub`;

SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_channel
-- ----------------------------
DROP TABLE IF EXISTS `t_channel`;
CREATE TABLE `t_channel`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '频道名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '频道表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_channel
-- ----------------------------
INSERT INTO `t_channel` VALUES (3, '穿搭', '2024-08-16 08:42:25', '2024-08-16 08:42:25', b'0');
INSERT INTO `t_channel` VALUES (4, '美食', '2024-08-16 08:42:25', '2024-08-16 08:42:25', b'0');
INSERT INTO `t_channel` VALUES (5, '彩妆', '2025-02-27 02:58:18', '2025-02-27 02:58:18', b'0');
INSERT INTO `t_channel` VALUES (6, '影视', '2025-02-27 02:58:25', '2025-02-27 02:58:25', b'0');
INSERT INTO `t_channel` VALUES (7, '职场', '2025-02-27 02:58:31', '2025-02-27 02:58:31', b'0');
INSERT INTO `t_channel` VALUES (8, '情感', '2025-02-27 02:58:38', '2025-02-27 02:58:38', b'0');
INSERT INTO `t_channel` VALUES (9, '家居', '2025-02-27 02:58:48', '2025-02-27 02:58:48', b'0');
INSERT INTO `t_channel` VALUES (10, '游戏', '2025-02-27 02:58:54', '2025-02-27 02:58:54', b'0');
INSERT INTO `t_channel` VALUES (11, '旅行', '2025-02-27 02:58:59', '2025-02-27 02:58:59', b'0');
INSERT INTO `t_channel` VALUES (12, '健身', '2025-02-27 02:59:10', '2025-02-27 02:59:10', b'0');

-- ----------------------------
-- Table structure for t_channel_topic_rel
-- ----------------------------
DROP TABLE IF EXISTS `t_channel_topic_rel`;
CREATE TABLE `t_channel_topic_rel`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `channel_id` bigint UNSIGNED NOT NULL COMMENT '频道ID',
  `topic_id` bigint UNSIGNED NOT NULL COMMENT '话题ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '频道-话题关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- t_channel_topic_rel starts empty. Topics can be associated by normal business operations.
-- ----------------------------

-- ----------------------------
-- Table structure for t_mq_send_failure
-- ----------------------------
DROP TABLE IF EXISTS `t_mq_send_failure`;
CREATE TABLE `t_mq_send_failure` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_key` varchar(64) NOT NULL COMMENT '消息幂等键',
  `topic` varchar(255) NOT NULL COMMENT 'RocketMQ Topic',
  `body` mediumtext NOT NULL COMMENT '消息体',
  `retry_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '补发次数',
  `next_retry_time` datetime NOT NULL COMMENT '下次补发时间',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态(0：待补发 1：补发中)',
  `locked_at` datetime NULL DEFAULT NULL COMMENT '任务认领时间',
  `last_error` varchar(1000) NULL DEFAULT NULL COMMENT '最近一次错误',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_message_key` (`message_key`),
  INDEX `idx_retry_scan` (`status`, `next_retry_time`, `locked_at`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'MQ事务Outbox及发送补偿表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for t_mq_consume_record
-- ----------------------------
DROP TABLE IF EXISTS `t_mq_consume_record`;
CREATE TABLE `t_mq_consume_record` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `consumer_group` varchar(128) NOT NULL,
  `message_key` char(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consumer_message` (`consumer_group`, `message_key`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RocketMQ 消费幂等记录';

-- ----------------------------
-- Table structure for t_comment
-- ----------------------------
DROP TABLE IF EXISTS `t_comment`;
CREATE TABLE `t_comment`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `note_id` bigint UNSIGNED NOT NULL COMMENT '关联的笔记ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '发布者用户ID',
  `content_uuid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '评论内容UUID',
  `is_content_empty` bit(1) NOT NULL DEFAULT b'0' COMMENT '内容是否为空(0：不为空 1：为空)',
  `image_url` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '评论附加图片URL',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '级别(1：一级评论 2：二级评论)',
  `reply_total` bigint NULL DEFAULT 0 COMMENT '评论被回复次数，仅一级评论需要',
  `like_total` bigint NULL DEFAULT 0 COMMENT '评论被点赞次数',
  `parent_id` bigint UNSIGNED NULL DEFAULT 0 COMMENT '父ID (若是对笔记的评论，则此字段存储笔记ID; 若是二级评论，则此字段存储一级评论的ID)',
  `reply_comment_id` bigint UNSIGNED NULL DEFAULT 0 COMMENT '回复哪个的评论 (0表示是对笔记的评论，若是对他人评论的回复，则存储回复评论的ID)',
  `reply_user_id` bigint UNSIGNED NULL DEFAULT 0 COMMENT '回复的哪个用户, 存储用户ID',
  `is_top` tinyint NOT NULL DEFAULT 0 COMMENT '是否置顶(0：不置顶 1：置顶)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `child_comment_total` bigint NULL DEFAULT 0 COMMENT '二级评论总数（只有一级评论才需要统计）',
  `heat` decimal(10, 2) NULL DEFAULT 0.00 COMMENT '评论热度',
  `first_reply_comment_id` bigint UNSIGNED NULL DEFAULT 0 COMMENT '最早回复的评论ID (只有一级评论需要)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_note_id`(`note_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_reply_comment_id`(`reply_comment_id` ASC) USING BTREE,
  INDEX `idx_reply_user_id`(`reply_user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38003 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评论表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- t_comment starts empty.
-- ----------------------------

-- ----------------------------
-- Table structure for t_comment_like
-- ----------------------------
DROP TABLE IF EXISTS `t_comment_like`;
CREATE TABLE `t_comment_like`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `comment_id` bigint NOT NULL COMMENT '评论ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id_comment_id`(`user_id` ASC, `comment_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 51 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评论点赞表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- t_comment_like starts empty.
-- ----------------------------

-- ----------------------------
-- Table structure for t_fans
-- ----------------------------
DROP TABLE IF EXISTS `t_fans`;
CREATE TABLE `t_fans`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `fans_user_id` bigint UNSIGNED NOT NULL COMMENT '粉丝用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id_fans_user_id`(`user_id` ASC, `fans_user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 50310 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户粉丝列表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- t_fans starts empty.
-- ----------------------------

-- ----------------------------
-- Table structure for t_following
-- ----------------------------
DROP TABLE IF EXISTS `t_following`;
CREATE TABLE `t_following`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `following_user_id` bigint UNSIGNED NOT NULL COMMENT '关注用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id_following_user_id`(`user_id` ASC, `following_user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 91309 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户关注列表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- t_following starts empty.
-- ----------------------------

-- ----------------------------
-- Table structure for t_note
-- ----------------------------
DROP TABLE IF EXISTS `t_note`;
CREATE TABLE `t_note`  (
  `id` bigint UNSIGNED NOT NULL COMMENT '主键ID',
  `title` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `is_content_empty` bit(1) NOT NULL DEFAULT b'0' COMMENT '内容是否为空(0：不为空 1：空)',
  `creator_id` bigint UNSIGNED NOT NULL COMMENT '发布者ID',
  `topic_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '话题ID',
  `topic_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '话题名称',
  `is_top` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否置顶(0：未置顶 1：置顶)',
  `type` tinyint NULL DEFAULT 0 COMMENT '类型(0：图文 1：视频)',
  `img_uris` varchar(660) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '笔记图片链接(逗号隔开)',
  `video_uri` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '视频链接',
  `visible` tinyint NULL DEFAULT 0 COMMENT '可见范围(0：公开,所有人可见 1：仅对自己可见)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态(0：待审核 1：正常展示 2：被删除(逻辑删除) 3：被下架)',
  `content_uuid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '笔记内容UUID',
  `channel_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '频道ID',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_creator_id`(`creator_id` ASC) USING BTREE,
  INDEX `idx_topic_id`(`topic_id` ASC) USING BTREE,
  INDEX `idx_channel_id`(`channel_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- t_note starts empty. Media is uploaded after user registration.
-- ----------------------------

-- ----------------------------
-- Table structure for t_note_collection
-- ----------------------------
DROP TABLE IF EXISTS `t_note_collection`;
CREATE TABLE `t_note_collection`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `note_id` bigint NOT NULL COMMENT '笔记ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '收藏状态(0：取消收藏 1：收藏)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id_note_id`(`user_id` ASC, `note_id` ASC) USING BTREE,
  INDEX `idx_note_id`(`note_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记收藏表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- t_note_collection starts empty.
-- ----------------------------

-- ----------------------------
-- Table structure for t_note_count
-- ----------------------------
DROP TABLE IF EXISTS `t_note_count`;
CREATE TABLE `t_note_count`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `note_id` bigint UNSIGNED NOT NULL COMMENT '笔记ID',
  `like_total` bigint NULL DEFAULT 0 COMMENT '获得点赞总数',
  `collect_total` bigint NULL DEFAULT 0 COMMENT '获得收藏总数',
  `comment_total` bigint NULL DEFAULT 0 COMMENT '被评论总数',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_note_id`(`note_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 82 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记计数表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- t_note_count starts empty.
-- ----------------------------

-- ----------------------------
-- Table structure for t_note_like
-- ----------------------------
DROP TABLE IF EXISTS `t_note_like`;
CREATE TABLE `t_note_like`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `note_id` bigint NOT NULL COMMENT '笔记ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '点赞状态(0：取消点赞 1：点赞)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id_note_id`(`user_id` ASC, `note_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记点赞表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- t_note_like starts empty.
-- ----------------------------

-- ----------------------------
-- Table structure for t_permission
-- ----------------------------
DROP TABLE IF EXISTS `t_permission`;
CREATE TABLE `t_permission`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '父ID',
  `name` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
  `type` tinyint UNSIGNED NOT NULL COMMENT '类型(1：目录 2：菜单 3：按钮)',
  `menu_url` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '菜单路由',
  `menu_icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '菜单图标',
  `sort` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '管理系统中的显示顺序',
  `permission_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限标识',
  `status` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态(0：启用；1：禁用)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '权限表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_permission
-- ----------------------------
INSERT INTO `t_permission` VALUES (1, 0, '发布笔记', 3, '', '', 1, 'app:note:publish', 0, '2024-05-29 07:26:02', '2024-05-29 07:26:02', b'0');
INSERT INTO `t_permission` VALUES (2, 0, '发布评论111', 3, '', '', 2, 'app:comment:publish', 0, '2024-05-29 07:27:17', '2024-05-29 07:27:17', b'0');
INSERT INTO `t_permission` VALUES (3, 0, '测试1111', 3, '', '', 0, 'test', 0, '2024-06-04 09:41:07', '2024-06-04 09:41:07', b'0');

-- ----------------------------
-- Table structure for t_role
-- ----------------------------
DROP TABLE IF EXISTS `t_role`;
CREATE TABLE `t_role`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名',
  `role_key` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色唯一标识',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态(0：启用 1：禁用)',
  `sort` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '管理系统中的显示顺序',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后一次更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_key`(`role_key` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_role
-- ----------------------------
INSERT INTO `t_role` VALUES (1, '普通用户', 'common_user', 0, 1, '', '2024-05-29 07:28:42', '2024-05-29 07:28:42', b'0');

-- ----------------------------
-- Table structure for t_role_permission_rel
-- ----------------------------
DROP TABLE IF EXISTS `t_role_permission_rel`;
CREATE TABLE `t_role_permission_rel`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint UNSIGNED NOT NULL COMMENT '角色ID',
  `permission_id` bigint UNSIGNED NOT NULL COMMENT '权限ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户权限表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_role_permission_rel
-- ----------------------------
INSERT INTO `t_role_permission_rel` VALUES (1, 1, 1, '2024-05-29 07:29:06', '2024-05-29 07:29:06', b'0');
INSERT INTO `t_role_permission_rel` VALUES (2, 1, 2, '2024-05-29 07:29:15', '2024-05-29 07:29:15', b'0');

-- ----------------------------
-- Table structure for t_topic
-- ----------------------------
DROP TABLE IF EXISTS `t_topic`;
CREATE TABLE `t_topic`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '话题名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 39 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '话题表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of t_topic
-- ----------------------------
INSERT INTO `t_topic` VALUES (3, '高分美剧推荐', '2024-08-16 08:43:57', '2024-08-16 08:43:57', b'0');
INSERT INTO `t_topic` VALUES (4, '下饭综艺推荐', '2024-08-16 08:43:57', '2024-08-16 08:43:57', b'0');
INSERT INTO `t_topic` VALUES (5, '游戏推荐', '2025-02-27 11:01:25', '2025-02-27 11:01:25', b'0');
INSERT INTO `t_topic` VALUES (6, '黑神话', '2025-02-27 12:16:27', '2025-02-27 12:16:27', b'0');
INSERT INTO `t_topic` VALUES (7, '黑猴', '2025-02-27 12:16:27', '2025-02-27 12:16:27', b'0');
INSERT INTO `t_topic` VALUES (8, '黑神话1', '2025-02-27 12:22:11', '2025-02-27 12:22:11', b'0');
INSERT INTO `t_topic` VALUES (9, '黑猴1', '2025-02-27 12:22:11', '2025-02-27 12:22:11', b'0');
INSERT INTO `t_topic` VALUES (12, '黑神话11', '2025-02-27 12:56:00', '2025-02-27 12:56:00', b'0');
INSERT INTO `t_topic` VALUES (13, '黑猴11', '2025-02-27 12:56:00', '2025-02-27 12:56:00', b'0');
INSERT INTO `t_topic` VALUES (14, '胡连馨', '2025-02-27 13:03:06', '2025-02-27 13:03:06', b'0');
INSERT INTO `t_topic` VALUES (15, '吃水果的仪式感', '2025-03-01 08:31:34', '2025-03-01 08:31:34', b'0');
INSERT INTO `t_topic` VALUES (16, '满满的水果', '2025-03-01 08:31:34', '2025-03-01 08:31:34', b'0');
INSERT INTO `t_topic` VALUES (17, '壁纸', '2025-03-01 08:39:03', '2025-03-01 08:39:03', b'0');
INSERT INTO `t_topic` VALUES (18, '当年轻人开始反向消费', '2025-03-01 08:41:11', '2025-03-01 08:41:11', b'0');
INSERT INTO `t_topic` VALUES (19, '编程', '2025-03-01 10:18:26', '2025-03-01 10:18:26', b'0');
INSERT INTO `t_topic` VALUES (20, '工作', '2025-03-01 10:18:26', '2025-03-01 10:18:26', b'0');
INSERT INTO `t_topic` VALUES (21, '程序员', '2025-03-01 10:18:26', '2025-03-01 10:18:26', b'0');
INSERT INTO `t_topic` VALUES (22, '桌面搭建', '2025-03-01 10:28:48', '2025-03-01 10:28:48', b'0');
INSERT INTO `t_topic` VALUES (23, '显示器', '2025-03-01 10:28:48', '2025-03-01 10:28:48', b'0');
INSERT INTO `t_topic` VALUES (24, '视频测试', '2025-03-01 11:00:14', '2025-03-01 11:00:14', b'0');
INSERT INTO `t_topic` VALUES (25, '妹子', '2025-03-01 11:00:14', '2025-03-01 11:00:14', b'0');
INSERT INTO `t_topic` VALUES (26, '治愈', '2025-03-01 11:01:21', '2025-03-01 11:01:21', b'0');
INSERT INTO `t_topic` VALUES (27, '高光', '2025-03-01 11:05:30', '2025-03-01 11:05:30', b'0');
INSERT INTO `t_topic` VALUES (28, '奋斗', '2025-03-01 11:05:30', '2025-03-01 11:05:30', b'0');
INSERT INTO `t_topic` VALUES (29, '医学', '2025-03-01 11:10:04', '2025-03-01 11:10:04', b'0');
INSERT INTO `t_topic` VALUES (30, '绿色', '2025-03-01 11:11:16', '2025-03-01 11:11:16', b'0');
INSERT INTO `t_topic` VALUES (31, '绿色治愈系', '2025-03-01 11:11:16', '2025-03-01 11:11:16', b'0');
INSERT INTO `t_topic` VALUES (32, '桌面好物', '2025-03-01 11:13:02', '2025-03-01 11:13:02', b'0');
INSERT INTO `t_topic` VALUES (33, '游戏', '2025-03-01 11:13:02', '2025-03-01 11:13:02', b'0');
INSERT INTO `t_topic` VALUES (34, '狗狗', '2025-03-01 11:15:13', '2025-03-01 11:15:13', b'0');
INSERT INTO `t_topic` VALUES (35, '看剧', '2025-03-01 11:16:09', '2025-03-01 11:16:09', b'0');
INSERT INTO `t_topic` VALUES (36, '剧荒', '2025-03-01 11:16:09', '2025-03-01 11:16:09', b'0');
INSERT INTO `t_topic` VALUES (37, '水果自由', '2025-03-01 11:17:05', '2025-03-01 11:17:05', b'0');
INSERT INTO `t_topic` VALUES (38, '车厘子', '2025-03-01 11:17:05', '2025-03-01 11:17:05', b'0');

-- ----------------------------
-- Table structure for t_user
-- ----------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `fishhub_id` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '飞鱼社区号(唯一凭证)',
  `password` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '密码',
  `nickname` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '昵称',
  `avatar` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '头像',
  `birthday` date NULL DEFAULT NULL COMMENT '生日',
  `background_img` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '背景图',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '手机号',
  `sex` tinyint NULL DEFAULT 0 COMMENT '性别(0：女 1：男)',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态(0：启用 1：禁用)',
  `introduction` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '个人简介',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_fishhub_id`(`fishhub_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_phone`(`phone` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2101 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- t_user starts empty. Register the first account through the application.
-- ----------------------------

-- ----------------------------
-- Table structure for t_user_count
-- ----------------------------
DROP TABLE IF EXISTS `t_user_count`;
CREATE TABLE `t_user_count`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `fans_total` bigint NULL DEFAULT 0 COMMENT '粉丝总数',
  `following_total` bigint NULL DEFAULT 0 COMMENT '关注总数',
  `note_total` bigint NULL DEFAULT 0 COMMENT '发布笔记总数',
  `like_total` bigint NULL DEFAULT 0 COMMENT '获得点赞总数',
  `collect_total` bigint NULL DEFAULT 0 COMMENT '获得收藏总数',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6503 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户计数表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- t_user_count starts empty. It is created on the first count update or scheduled alignment.
-- ----------------------------

-- ----------------------------
-- Table structure for t_user_role_rel
-- ----------------------------
DROP TABLE IF EXISTS `t_user_role_rel`;
CREATE TABLE `t_user_role_rel`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` bigint UNSIGNED NOT NULL COMMENT '角色ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- t_user_role_rel starts empty. Registration assigns the common-user role.
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;


-- ============================
-- XXL-JOB scheduler database
-- ============================

#
# XXL-JOB v2.4.1
# Copyright (c) 2015-present, xuxueli.

CREATE database if NOT EXISTS `xxl_job` default character set utf8mb4 collate utf8mb4_unicode_ci;
use `xxl_job`;

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `xxl_job_info`;

CREATE TABLE `xxl_job_info` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `job_group` int(11) NOT NULL COMMENT '执行器主键ID',
  `job_desc` varchar(255) NOT NULL,
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `author` varchar(64) DEFAULT NULL COMMENT '作者',
  `alarm_email` varchar(255) DEFAULT NULL COMMENT '报警邮件',
  `schedule_type` varchar(50) NOT NULL DEFAULT 'NONE' COMMENT '调度类型',
  `schedule_conf` varchar(128) DEFAULT NULL COMMENT '调度配置，值含义取决于调度类型',
  `misfire_strategy` varchar(50) NOT NULL DEFAULT 'DO_NOTHING' COMMENT '调度过期策略',
  `executor_route_strategy` varchar(50) DEFAULT NULL COMMENT '执行器路由策略',
  `executor_handler` varchar(255) DEFAULT NULL COMMENT '执行器任务handler',
  `executor_param` varchar(512) DEFAULT NULL COMMENT '执行器任务参数',
  `executor_block_strategy` varchar(50) DEFAULT NULL COMMENT '阻塞处理策略',
  `executor_timeout` int(11) NOT NULL DEFAULT '0' COMMENT '任务执行超时时间，单位秒',
  `executor_fail_retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '失败重试次数',
  `glue_type` varchar(50) NOT NULL COMMENT 'GLUE类型',
  `glue_source` mediumtext COMMENT 'GLUE源代码',
  `glue_remark` varchar(128) DEFAULT NULL COMMENT 'GLUE备注',
  `glue_updatetime` datetime DEFAULT NULL COMMENT 'GLUE更新时间',
  `child_jobid` varchar(255) DEFAULT NULL COMMENT '子任务ID，多个逗号分隔',
  `trigger_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '调度状态：0-停止，1-运行',
  `trigger_last_time` bigint(13) NOT NULL DEFAULT '0' COMMENT '上次调度时间',
  `trigger_next_time` bigint(13) NOT NULL DEFAULT '0' COMMENT '下次调度时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `xxl_job_log`;

CREATE TABLE `xxl_job_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `job_group` int(11) NOT NULL COMMENT '执行器主键ID',
  `job_id` int(11) NOT NULL COMMENT '任务，主键ID',
  `executor_address` varchar(255) DEFAULT NULL COMMENT '执行器地址，本次执行的地址',
  `executor_handler` varchar(255) DEFAULT NULL COMMENT '执行器任务handler',
  `executor_param` varchar(512) DEFAULT NULL COMMENT '执行器任务参数',
  `executor_sharding_param` varchar(20) DEFAULT NULL COMMENT '执行器任务分片参数，格式如 1/2',
  `executor_fail_retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '失败重试次数',
  `trigger_time` datetime DEFAULT NULL COMMENT '调度-时间',
  `trigger_code` int(11) NOT NULL COMMENT '调度-结果',
  `trigger_msg` text COMMENT '调度-日志',
  `handle_time` datetime DEFAULT NULL COMMENT '执行-时间',
  `handle_code` int(11) NOT NULL COMMENT '执行-状态',
  `handle_msg` text COMMENT '执行-日志',
  `alarm_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '告警状态：0-默认、1-无需告警、2-告警成功、3-告警失败',
  PRIMARY KEY (`id`),
  KEY `I_trigger_time` (`trigger_time`),
  KEY `I_handle_code` (`handle_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `xxl_job_log_report`;

CREATE TABLE `xxl_job_log_report` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `trigger_day` datetime DEFAULT NULL COMMENT '调度-时间',
  `running_count` int(11) NOT NULL DEFAULT '0' COMMENT '运行中-日志数量',
  `suc_count` int(11) NOT NULL DEFAULT '0' COMMENT '执行成功-日志数量',
  `fail_count` int(11) NOT NULL DEFAULT '0' COMMENT '执行失败-日志数量',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_trigger_day` (`trigger_day`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `xxl_job_logglue`;

CREATE TABLE `xxl_job_logglue` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `job_id` int(11) NOT NULL COMMENT '任务，主键ID',
  `glue_type` varchar(50) DEFAULT NULL COMMENT 'GLUE类型',
  `glue_source` mediumtext COMMENT 'GLUE源代码',
  `glue_remark` varchar(128) NOT NULL COMMENT 'GLUE备注',
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `xxl_job_registry`;

CREATE TABLE `xxl_job_registry` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `registry_group` varchar(50) NOT NULL,
  `registry_key` varchar(255) NOT NULL,
  `registry_value` varchar(255) NOT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `i_g_k_v` (`registry_group`,`registry_key`,`registry_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `xxl_job_group`;

CREATE TABLE `xxl_job_group` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `app_name` varchar(64) NOT NULL COMMENT '执行器AppName',
  `title` varchar(12) NOT NULL COMMENT '执行器名称',
  `address_type` tinyint(4) NOT NULL DEFAULT '0' COMMENT '执行器地址类型：0=自动注册、1=手动录入',
  `address_list` text COMMENT '执行器地址列表，多地址逗号分隔',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `xxl_job_user`;

CREATE TABLE `xxl_job_user` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '账号',
  `password` varchar(50) NOT NULL COMMENT '密码',
  `role` tinyint(4) NOT NULL COMMENT '角色：0-普通用户、1-管理员',
  `permission` varchar(255) DEFAULT NULL COMMENT '权限：执行器ID列表，多个逗号分割',
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_username` (`username`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `xxl_job_lock`;

CREATE TABLE `xxl_job_lock` (
  `lock_name` varchar(50) NOT NULL COMMENT '锁名称',
  PRIMARY KEY (`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `xxl_job_group`(`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`) VALUES (1, 'xxl-job-executor-fishhub', 'FishHub 数据对齐执行器', 0, NULL, '2018-11-03 22:21:31' );
INSERT INTO `xxl_job_info` (`id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`, `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`, `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`, `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`) VALUES
(3, 1, '建当日分片临时表', '2026-08-11 06:41:37', '2026-08-11 06:41:37', 'fishhub', '', 'CRON', '0 0 0 * * ?', 'DO_NOTHING', 'FIRST', 'createTableJobHandler', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '', '2026-08-11 06:41:37', '', 1, 0, 0),
(4, 1, '清理过期分片临时表', '2026-08-11 06:41:37', '2026-08-11 06:41:37', 'fishhub', '', 'CRON', '0 0 1 * * ?', 'DO_NOTHING', 'FIRST', 'deleteTableJobHandler', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '', '2026-08-11 06:41:37', '', 1, 0, 0),
(5, 1, '笔记点赞计数对齐', '2026-08-11 06:41:37', '2026-08-11 06:41:37', 'fishhub', '', 'CRON', '0 0 2 * * ?', 'DO_NOTHING', 'SHARDING_BROADCAST', 'noteLikeCountShardingJobHandler', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '', '2026-08-11 06:41:37', '', 1, 0, 0),
(6, 1, '笔记收藏计数对齐', '2026-08-11 06:41:37', '2026-08-11 06:41:37', 'fishhub', '', 'CRON', '0 3 2 * * ?', 'DO_NOTHING', 'SHARDING_BROADCAST', 'noteCollectCountShardingJobHandler', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '', '2026-08-11 06:41:37', '', 1, 0, 0),
(7, 1, '笔记发布计数对齐', '2026-08-11 06:41:37', '2026-08-11 06:41:37', 'fishhub', '', 'CRON', '0 6 2 * * ?', 'DO_NOTHING', 'SHARDING_BROADCAST', 'notePublishCountShardingJobHandler', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '', '2026-08-11 06:41:37', '', 1, 0, 0),
(8, 1, '关注计数对齐', '2026-08-11 06:41:37', '2026-08-11 06:41:37', 'fishhub', '', 'CRON', '0 9 2 * * ?', 'DO_NOTHING', 'SHARDING_BROADCAST', 'followingCountShardingJobHandler', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '', '2026-08-11 06:41:37', '', 1, 0, 0),
(9, 1, '粉丝计数对齐', '2026-08-11 06:41:37', '2026-08-11 06:41:37', 'fishhub', '', 'CRON', '0 12 2 * * ?', 'DO_NOTHING', 'SHARDING_BROADCAST', 'fansCountShardingJobHandler', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '', '2026-08-11 06:41:37', '', 1, 0, 0),
(10, 1, '用户点赞计数对齐', '2026-08-11 06:41:37', '2026-08-11 06:41:37', 'fishhub', '', 'CRON', '0 15 2 * * ?', 'DO_NOTHING', 'SHARDING_BROADCAST', 'userLikeCountShardingJobHandler', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '', '2026-08-11 06:41:37', '', 1, 0, 0),
(11, 1, '用户收藏计数对齐', '2026-08-11 06:41:37', '2026-08-11 06:41:37', 'fishhub', '', 'CRON', '0 18 2 * * ?', 'DO_NOTHING', 'SHARDING_BROADCAST', 'userCollectCountShardingJobHandler', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '', '2026-08-11 06:41:37', '', 1, 0, 0);
INSERT INTO `xxl_job_user`(`id`, `username`, `password`, `role`, `permission`) VALUES (1, 'admin', 'e10adc3949ba59abbe56e057f20f883e', 1, NULL);
INSERT INTO `xxl_job_lock` ( `lock_name`) VALUES ( 'schedule_lock');

commit;



-- ============================
-- Leaf ID generator database
-- ============================

create database if not exists leaf;

use leaf;

DROP TABLE IF EXISTS `leaf_alloc`;

CREATE TABLE `leaf_alloc` (
                              `biz_tag` varchar(128)  NOT NULL DEFAULT '',
                              `max_id` bigint(20) NOT NULL DEFAULT '1',
                              `step` int(11) NOT NULL,
                              `description` varchar(256)  DEFAULT NULL,
                              `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`biz_tag`)
) ENGINE=InnoDB;


INSERT INTO `leaf`.`leaf_alloc` (`biz_tag`, `max_id`, `step`, `description`, `update_time`) VALUES ('leaf-segment-fishhub-id', 10100, 2000, 'fishhub ID', now());

INSERT INTO `leaf`.`leaf_alloc` (`biz_tag`, `max_id`, `step`, `description`, `update_time`) VALUES ('leaf-segment-user-id', 100, 2000, '用户 ID', now());

INSERT INTO `leaf`.`leaf_alloc` (`biz_tag`, `max_id`, `step`, `description`, `update_time`) VALUES ('leaf-segment-comment-id', 1, 2000, '评论 ID', now());

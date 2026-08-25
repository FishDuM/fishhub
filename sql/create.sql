SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ========================================================
-- 1. 用户与权限服务数据库 (fishhub_user)
-- ========================================================
CREATE DATABASE IF NOT EXISTS `fishhub_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fishhub_user`;

CREATE TABLE IF NOT EXISTS `t_user` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `fishhub_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '飞鱼社区号(唯一凭证)',
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

CREATE TABLE IF NOT EXISTS `t_role` (
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

CREATE TABLE IF NOT EXISTS `t_permission` (
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

CREATE TABLE IF NOT EXISTS `t_role_permission_rel` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint UNSIGNED NOT NULL COMMENT '角色ID',
  `permission_id` bigint UNSIGNED NOT NULL COMMENT '权限ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户权限表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_user_role_rel` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` bigint UNSIGNED NOT NULL COMMENT '角色ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_following` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `following_user_id` bigint UNSIGNED NOT NULL COMMENT '关注用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id_following_user_id`(`user_id` ASC, `following_user_id` ASC) USING BTREE,
  INDEX `idx_following_user_id_id`(`user_id` ASC, `id` DESC) USING BTREE,
  INDEX `idx_following_target_id`(`following_user_id` ASC, `id` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 91309 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户关注列表（粉丝由 following_user_id 反向查询）' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_mq_consume_record` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `consumer_group` varchar(128) NOT NULL,
  `message_key` char(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consumer_message` (`consumer_group`, `message_key`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RocketMQ 消费幂等记录';

CREATE TABLE IF NOT EXISTS `t_tx_journal` (
  `tx_id` varchar(64) NOT NULL COMMENT '事务消息 txId（回查判定键）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`tx_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '事务消息本地事务回查日志';

-- fishhub_user 初始基础数据
INSERT INTO `t_role` (`id`, `role_name`, `role_key`, `status`, `sort`, `remark`, `create_time`, `update_time`, `is_deleted`) VALUES
(1, '普通用户', 'common_user', 0, 1, '', '2024-05-29 07:28:42', '2024-05-29 07:28:42', b'0')
ON DUPLICATE KEY UPDATE `role_name`=VALUES(`role_name`);

INSERT INTO `t_permission` (`id`, `parent_id`, `name`, `type`, `menu_url`, `menu_icon`, `sort`, `permission_key`, `status`, `create_time`, `update_time`, `is_deleted`) VALUES
(1, 0, '发布笔记', 3, '', '', 1, 'app:note:publish', 0, '2024-05-29 07:26:02', '2024-05-29 07:26:02', b'0'),
(2, 0, '发布评论', 3, '', '', 2, 'app:comment:publish', 0, '2024-05-29 07:27:17', '2024-05-29 07:27:17', b'0'),
(3, 0, '测试1111', 3, '', '', 0, 'test', 0, '2024-06-04 09:41:07', '2024-06-04 09:41:07', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);

INSERT INTO `t_role_permission_rel` (`id`, `role_id`, `permission_id`, `create_time`, `update_time`, `is_deleted`) VALUES
(1, 1, 1, '2024-05-29 07:29:06', '2024-05-29 07:29:06', b'0'),
(2, 1, 2, '2024-05-29 07:29:15', '2024-05-29 07:29:15', b'0')
ON DUPLICATE KEY UPDATE `permission_id`=VALUES(`permission_id`);

INSERT INTO `t_user` (`fishhub_id`, `password`, `nickname`, `phone`, `status`) VALUES
('smoke001', '$2a$10$mw1jq3XPBKtAEancOOJsLuSAPhxpwbnPhf6m/gb5gWvJhCuYl.CAC', 'SmokeUser1', '13811110001', 0),
('smoke002', '$2a$10$mw1jq3XPBKtAEancOOJsLuSAPhxpwbnPhf6m/gb5gWvJhCuYl.CAC', 'SmokeUser2', '13811110002', 0)
ON DUPLICATE KEY UPDATE `password`=VALUES(`password`), `nickname`=VALUES(`nickname`);


-- ========================================================
-- 2. 笔记服务数据库 (fishhub_note)
-- ========================================================
CREATE DATABASE IF NOT EXISTS `fishhub_note` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fishhub_note`;

CREATE TABLE IF NOT EXISTS `t_note` (
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
  `revision` bigint UNSIGNED NOT NULL DEFAULT 1 COMMENT '笔记聚合版本（编辑乐观锁与缓存版本）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_creator_id`(`creator_id` ASC) USING BTREE,
  INDEX `idx_topic_id`(`topic_id` ASC) USING BTREE,
  INDEX `idx_channel_id`(`channel_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_discover_visible_status_id`(`visible` ASC, `status` ASC, `id` DESC) USING BTREE,
  INDEX `idx_discover_channel_visible_status_id`(`channel_id` ASC, `visible` ASC, `status` ASC, `id` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_topic` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '话题名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 39 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '话题表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_channel` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '频道名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '频道表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_note_like` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `note_id` bigint NOT NULL COMMENT '笔记ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '点赞状态(0：取消点赞 1：点赞)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id_note_id`(`user_id` ASC, `note_id` ASC) USING BTREE,
  INDEX `idx_user_status_create_id`(`user_id` ASC, `status` ASC, `create_time` ASC, `id` ASC) USING BTREE,
  INDEX `idx_note_id_status`(`note_id` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记点赞表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_note_collection` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `note_id` bigint NOT NULL COMMENT '笔记ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '收藏状态(0：取消收藏 1：收藏)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id_note_id`(`user_id` ASC, `note_id` ASC) USING BTREE,
  INDEX `idx_note_id`(`note_id` ASC) USING BTREE,
  INDEX `idx_user_status_create_id`(`user_id` ASC, `status` ASC, `create_time` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记收藏表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_mq_consume_record` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `consumer_group` varchar(128) NOT NULL,
  `message_key` char(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consumer_message` (`consumer_group`, `message_key`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RocketMQ 消费幂等记录';

CREATE TABLE IF NOT EXISTS `t_tx_journal` (
  `tx_id` varchar(64) NOT NULL COMMENT '事务消息 txId（回查判定键）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`tx_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '事务消息本地事务回查日志';

-- fishhub_note 初始基础数据
INSERT INTO `t_channel` (`id`, `name`, `create_time`, `update_time`, `is_deleted`) VALUES
(3, '穿搭', '2024-08-16 08:42:25', '2024-08-16 08:42:25', b'0'),
(4, '美食', '2024-08-16 08:42:25', '2024-08-16 08:42:25', b'0'),
(5, '彩妆', '2025-02-27 02:58:18', '2025-02-27 02:58:18', b'0'),
(6, '影视', '2025-02-27 02:58:25', '2025-02-27 02:58:25', b'0'),
(7, '职场', '2025-02-27 02:58:31', '2025-02-27 02:58:31', b'0'),
(8, '情感', '2025-02-27 02:58:38', '2025-02-27 02:58:38', b'0'),
(9, '家居', '2025-02-27 02:58:48', '2025-02-27 02:58:48', b'0'),
(10, '游戏', '2025-02-27 02:58:54', '2025-02-27 02:58:54', b'0'),
(11, '旅行', '2025-02-27 02:58:59', '2025-02-27 02:58:59', b'0'),
(12, '健身', '2025-02-27 02:59:10', '2025-02-27 02:59:10', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);

INSERT INTO `t_topic` (`id`, `name`, `create_time`, `update_time`, `is_deleted`) VALUES
(3, '高分美剧推荐', '2024-08-16 08:43:57', '2024-08-16 08:43:57', b'0'),
(4, '下饭综艺推荐', '2024-08-16 08:43:57', '2024-08-16 08:43:57', b'0'),
(5, '游戏推荐', '2025-02-27 11:01:25', '2025-02-27 11:01:25', b'0'),
(6, '黑神话', '2025-02-27 12:16:27', '2025-02-27 12:16:27', b'0'),
(7, '黑猴', '2025-02-27 12:16:27', '2025-02-27 12:16:27', b'0'),
(8, '黑神话1', '2025-02-27 12:22:11', '2025-02-27 12:22:11', b'0'),
(9, '黑猴1', '2025-02-27 12:22:11', '2025-02-27 12:22:11', b'0'),
(12, '黑神话11', '2025-02-27 12:56:00', '2025-02-27 12:56:00', b'0'),
(13, '黑猴11', '2025-02-27 12:56:00', '2025-02-27 12:56:00', b'0'),
(14, '胡连馨', '2025-02-27 13:03:06', '2025-02-27 13:03:06', b'0'),
(15, '吃水果的仪式感', '2025-03-01 08:31:34', '2025-03-01 08:31:34', b'0'),
(16, '满满的水果', '2025-03-01 08:31:34', '2025-03-01 08:31:34', b'0'),
(17, '壁纸', '2025-03-01 08:39:03', '2025-03-01 08:39:03', b'0'),
(18, '当年轻人开始反向消费', '2025-03-01 08:41:11', '2025-03-01 08:41:11', b'0'),
(19, '编程', '2025-03-01 10:18:26', '2025-03-01 10:18:26', b'0'),
(20, '工作', '2025-03-01 10:18:26', '2025-03-01 10:18:26', b'0'),
(21, '程序员', '2025-03-01 10:18:26', '2025-03-01 10:18:26', b'0'),
(22, '桌面搭建', '2025-03-01 10:28:48', '2025-03-01 10:28:48', b'0'),
(23, '显示器', '2025-03-01 10:28:48', '2025-03-01 10:28:48', b'0'),
(24, '视频测试', '2025-03-01 11:00:14', '2025-03-01 11:00:14', b'0'),
(25, '妹子', '2025-03-01 11:00:14', '2025-03-01 11:00:14', b'0'),
(26, '治愈', '2025-03-01 11:01:21', '2025-03-01 11:01:21', b'0'),
(27, '高光', '2025-03-01 11:05:30', '2025-03-01 11:05:30', b'0'),
(28, '奋斗', '2025-03-01 11:05:30', '2025-03-01 11:05:30', b'0'),
(29, '医学', '2025-03-01 11:10:04', '2025-03-01 11:10:04', b'0'),
(30, '绿色', '2025-03-01 11:11:16', '2025-03-01 11:11:16', b'0'),
(31, '绿色治愈系', '2025-03-01 11:11:16', '2025-03-01 11:11:16', b'0'),
(32, '桌面好物', '2025-03-01 11:13:02', '2025-03-01 11:13:02', b'0'),
(33, '游戏', '2025-03-01 11:13:02', '2025-03-01 11:13:02', b'0'),
(34, '狗狗', '2025-03-01 11:15:13', '2025-03-01 11:15:13', b'0'),
(35, '看剧', '2025-03-01 11:16:09', '2025-03-01 11:16:09', b'0'),
(36, '剧荒', '2025-03-01 11:16:09', '2025-03-01 11:16:09', b'0'),
(37, '水果自由', '2025-03-01 11:17:05', '2025-03-01 11:17:05', b'0'),
(38, '车厘子', '2025-03-01 11:17:05', '2025-03-01 11:17:05', b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`);


-- ========================================================
-- 3. 评论服务数据库 (fishhub_comment)
-- ========================================================
CREATE DATABASE IF NOT EXISTS `fishhub_comment` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fishhub_comment`;

CREATE TABLE IF NOT EXISTS `t_comment` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `note_id` bigint UNSIGNED NOT NULL COMMENT '关联的笔记ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '发布者用户ID',
  `content_uuid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '评论内容UUID',
  `is_content_empty` bit(1) NOT NULL DEFAULT b'0' COMMENT '内容是否为空(0：不为空 1：为空)',
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '评论附加图片URL',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '级别(1：一级评论 2：二级评论)',
  `like_total` bigint NULL DEFAULT 0 COMMENT '评论被点赞次数',
  `parent_id` bigint UNSIGNED NULL DEFAULT 0 COMMENT '父ID (若是对笔记的评论，则此字段存储笔记ID; 若是二级评论，则此字段存储一级评论的ID)',
  `reply_comment_id` bigint UNSIGNED NULL DEFAULT 0 COMMENT '回复哪个的评论 (0表示是对笔记的评论，若是对他人评论的回复，则存储回复评论的ID)',
  `reply_user_id` bigint UNSIGNED NULL DEFAULT 0 COMMENT '回复的哪个用户, 存储用户ID',
  `is_top` tinyint NOT NULL DEFAULT 0 COMMENT '是否置顶(0：不置顶 1：置顶)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `child_comment_total` bigint NULL DEFAULT 0 COMMENT '二级评论总数（只有一级评论才需要统计）',
  `heat` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '评论热度',
  `first_reply_comment_id` bigint UNSIGNED NULL DEFAULT 0 COMMENT '最早回复的评论ID (只有一级评论需要)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_reply_comment_id`(`reply_comment_id` ASC) USING BTREE,
  INDEX `idx_comment_note_level_heat_id`(`note_id` ASC, `level` ASC, `heat` DESC, `id` DESC) USING BTREE,
  INDEX `idx_comment_parent_level_id`(`parent_id` ASC, `level` ASC, `id` ASC) USING BTREE,
  INDEX `idx_comment_parent_level_create_time_id`(`parent_id` ASC, `level` ASC, `create_time` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38003 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评论表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `t_comment_like` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `comment_id` bigint NOT NULL COMMENT '评论ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id_comment_id`(`user_id` ASC, `comment_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 51 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评论点赞表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `t_mq_consume_record` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `consumer_group` varchar(128) NOT NULL,
  `message_key` char(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consumer_message` (`consumer_group`, `message_key`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RocketMQ 消费幂等记录';

CREATE TABLE IF NOT EXISTS `t_tx_journal` (
  `tx_id` varchar(64) NOT NULL COMMENT '事务消息 txId（回查判定键）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`tx_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '事务消息本地事务回查日志';


-- ========================================================
-- 4. 计数服务数据库 (fishhub_count)
-- ========================================================
CREATE DATABASE IF NOT EXISTS `fishhub_count` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fishhub_count`;

CREATE TABLE IF NOT EXISTS `t_note_count` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `note_id` bigint UNSIGNED NOT NULL COMMENT '笔记ID',
  `like_total` bigint NULL DEFAULT 0 COMMENT '获得点赞总数',
  `collect_total` bigint NULL DEFAULT 0 COMMENT '获得收藏总数',
  `comment_total` bigint NULL DEFAULT 0 COMMENT '被评论总数',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_note_id`(`note_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 82 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记计数表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_user_count` (
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

CREATE TABLE IF NOT EXISTS `t_tx_journal` (
  `tx_id` varchar(64) NOT NULL COMMENT '事务消息 txId（回查判定键）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`tx_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '事务消息本地事务回查日志';

CREATE TABLE IF NOT EXISTS `t_mq_consume_record` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `consumer_group` varchar(128) NOT NULL,
  `message_key` char(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consumer_message` (`consumer_group`, `message_key`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RocketMQ 消费幂等记录';


-- ========================================================
-- 5. 分布式发号器数据库 (Leaf ID Generator)
-- ========================================================
CREATE DATABASE IF NOT EXISTS `leaf` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `leaf`;

CREATE TABLE IF NOT EXISTS `leaf_alloc` (
  `biz_tag` varchar(128) NOT NULL DEFAULT '',
  `max_id` bigint(20) NOT NULL DEFAULT '1',
  `step` int(11) NOT NULL,
  `description` varchar(256) DEFAULT NULL,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`biz_tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Leaf 号段发号分配表';

INSERT INTO `leaf_alloc` (`biz_tag`, `max_id`, `step`, `description`, `update_time`) VALUES
('leaf-segment-fishhub-id', 10100, 2000, 'fishhub ID', now()),
('leaf-segment-user-id', 100, 2000, '用户 ID', now()),
('leaf-segment-comment-id', 1, 2000, '评论 ID', now())
ON DUPLICATE KEY UPDATE `step`=VALUES(`step`), `description`=VALUES(`description`);

SET FOREIGN_KEY_CHECKS = 1;


-- ========================================================
-- 6. 对账与一致性自检查询 (Reconciliation Checks)
-- ========================================================

-- 1) 评论数对账：t_note_count.comment_total 应等于 fishhub_comment 中一级评论数
SELECT 'note_comment_total_mismatch' AS check_name, nc.note_id, nc.comment_total AS db_total,
       (SELECT COUNT(*) FROM fishhub_comment.t_comment c WHERE c.note_id = nc.note_id AND c.`level` = 1) AS actual_total
FROM fishhub_count.t_note_count nc
HAVING db_total != actual_total;

-- 2) 笔记点赞数对账
SELECT 'note_like_total_mismatch' AS check_name, nc.note_id, nc.like_total AS db_total,
       (SELECT COUNT(*) FROM fishhub_note.t_note_like l WHERE l.note_id = nc.note_id AND l.status = 1) AS actual_total
FROM fishhub_count.t_note_count nc
HAVING db_total != actual_total;

-- 3) 笔记收藏数对账
SELECT 'note_collect_total_mismatch' AS check_name, nc.note_id, nc.collect_total AS db_total,
       (SELECT COUNT(*) FROM fishhub_note.t_note_collection c WHERE c.note_id = nc.note_id AND c.status = 1) AS actual_total
FROM fishhub_count.t_note_count nc
HAVING db_total != actual_total;

-- 4) 粉丝/关注数对账（已修正为向 fishhub_user.t_following 对账）
SELECT 'user_fans_total_mismatch' AS check_name, uc.user_id, uc.fans_total AS db_total,
       (SELECT COUNT(*) FROM fishhub_user.t_following f WHERE f.following_user_id = uc.user_id) AS actual_total
FROM fishhub_count.t_user_count uc
HAVING db_total != actual_total;

-- 5) 评论点赞数对账（like_total 归属 t_comment，由评论服务自持）
SELECT 'comment_like_total_mismatch' AS check_name, c.id AS comment_id, c.like_total AS db_total,
       (SELECT COUNT(*) FROM fishhub_comment.t_comment_like l WHERE l.comment_id = c.id) AS actual_total
FROM fishhub_comment.t_comment c
HAVING db_total != actual_total;

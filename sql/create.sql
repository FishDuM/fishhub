SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `fishhub_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fishhub_user`;

CREATE TABLE IF NOT EXISTS `t_user`  (
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

CREATE TABLE IF NOT EXISTS `t_role`  (
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

CREATE TABLE IF NOT EXISTS `t_permission`  (
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

CREATE TABLE IF NOT EXISTS `t_role_permission_rel`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint UNSIGNED NOT NULL COMMENT '角色ID',
  `permission_id` bigint UNSIGNED NOT NULL COMMENT '权限ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户权限表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_user_role_rel`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `role_id` bigint UNSIGNED NOT NULL COMMENT '角色ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色表' ROW_FORMAT = DYNAMIC;

-- 消费幂等记录（各服务自持一份）
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


CREATE DATABASE IF NOT EXISTS `fishhub_note` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fishhub_note`;

CREATE TABLE IF NOT EXISTS `t_note`  (
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

CREATE TABLE IF NOT EXISTS `t_topic`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '话题名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 39 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '话题表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_channel`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '频道名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0：未删除 1：已删除)',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '频道表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_channel_topic_rel`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `channel_id` bigint UNSIGNED NOT NULL COMMENT '频道ID',
  `topic_id` bigint UNSIGNED NOT NULL COMMENT '话题ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '频道-话题关联表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_note_like`  (
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

CREATE TABLE IF NOT EXISTS `t_note_collection`  (
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

-- 消费幂等记录（各服务自持一份）
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


CREATE DATABASE IF NOT EXISTS `fishhub_comment` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fishhub_comment`;

CREATE TABLE IF NOT EXISTS `t_comment`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'id',
  `note_id` bigint UNSIGNED NOT NULL COMMENT '关联的笔记ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '发布者用户ID',
  `content_uuid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '评论内容UUID',
  `is_content_empty` bit(1) NOT NULL DEFAULT b'0' COMMENT '内容是否为空(0：不为空 1：为空)',
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '评论附加图片URL',
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
  `heat` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '评论热度',
  `first_reply_comment_id` bigint UNSIGNED NULL DEFAULT 0 COMMENT '最早回复的评论ID (只有一级评论需要)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_reply_comment_id`(`reply_comment_id` ASC) USING BTREE,
  INDEX `idx_comment_note_level_heat_id`(`note_id` ASC, `level` ASC, `heat` DESC, `id` DESC) USING BTREE,
  INDEX `idx_comment_parent_level_id`(`parent_id` ASC, `level` ASC, `id` ASC) USING BTREE,
  INDEX `idx_comment_parent_level_create_time_id`(`parent_id` ASC, `level` ASC, `create_time` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38003 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评论表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `t_comment_like`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `comment_id` bigint NOT NULL COMMENT '评论ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id_comment_id`(`user_id` ASC, `comment_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 51 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评论点赞表' ROW_FORMAT = Dynamic;

-- 消费幂等记录（各服务自持一份）
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


CREATE DATABASE IF NOT EXISTS `fishhub_relation` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fishhub_relation`;

CREATE TABLE IF NOT EXISTS `t_following`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `following_user_id` bigint UNSIGNED NOT NULL COMMENT '关注用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id_following_user_id`(`user_id` ASC, `following_user_id` ASC) USING BTREE,
  INDEX `idx_following_user_id_id`(`user_id` ASC, `id` DESC) USING BTREE,
  INDEX `idx_following_target_id`(`following_user_id` ASC, `id` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 91309 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户关注列表（粉丝由 following_user_id 反向查询）' ROW_FORMAT = DYNAMIC;

-- 消费幂等记录（各服务自持一份）
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


CREATE DATABASE IF NOT EXISTS `fishhub_count` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fishhub_count`;

CREATE TABLE IF NOT EXISTS `t_note_count`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `note_id` bigint UNSIGNED NOT NULL COMMENT '笔记ID',
  `like_total` bigint NULL DEFAULT 0 COMMENT '获得点赞总数',
  `collect_total` bigint NULL DEFAULT 0 COMMENT '获得收藏总数',
  `comment_total` bigint NULL DEFAULT 0 COMMENT '被评论总数',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_note_id`(`note_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 82 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记计数表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_user_count`  (
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

-- 消费幂等记录（各服务自持一份）
CREATE TABLE IF NOT EXISTS `t_mq_consume_record` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `consumer_group` varchar(128) NOT NULL,
  `message_key` char(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consumer_message` (`consumer_group`, `message_key`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RocketMQ 消费幂等记录';



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
-- ----------------------------

-- ----------------------------
-- Table structure for t_tx_journal
-- 事务消息本地事务回查日志：tx_id 行随业务事务提交，broker 回查以存在性判定提交事实；每日滚动清理（默认保留 24h）。
-- ----------------------------
DROP TABLE IF EXISTS `t_tx_journal`;
CREATE TABLE `t_tx_journal` (
  `tx_id` varchar(64) NOT NULL COMMENT '事务消息 txId（回查判定键）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`tx_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '事务消息本地事务回查日志';

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
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '评论附加图片URL',
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
  `heat` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '评论热度',
  `first_reply_comment_id` bigint UNSIGNED NULL DEFAULT 0 COMMENT '最早回复的评论ID (只有一级评论需要)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_reply_comment_id`(`reply_comment_id` ASC) USING BTREE,
  INDEX `idx_comment_note_level_heat_id`(`note_id` ASC, `level` ASC, `heat` DESC, `id` DESC) USING BTREE,
  INDEX `idx_comment_parent_level_id`(`parent_id` ASC, `level` ASC, `id` ASC) USING BTREE,
  INDEX `idx_comment_parent_level_create_time_id`(`parent_id` ASC, `level` ASC, `create_time` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38003 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评论表' ROW_FORMAT = Dynamic;

-- ----------------------------
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
-- ----------------------------

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
  UNIQUE INDEX `uk_user_id_following_user_id`(`user_id` ASC, `following_user_id` ASC) USING BTREE,
  INDEX `idx_following_user_id_id`(`user_id` ASC, `id` DESC) USING BTREE,
  INDEX `idx_following_target_id`(`following_user_id` ASC, `id` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 91309 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户关注列表（粉丝由 following_user_id 反向查询）' ROW_FORMAT = DYNAMIC;

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
  `revision` bigint UNSIGNED NOT NULL DEFAULT 1 COMMENT '笔记聚合版本（编辑乐观锁与缓存版本）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_creator_id`(`creator_id` ASC) USING BTREE,
  INDEX `idx_topic_id`(`topic_id` ASC) USING BTREE,
  INDEX `idx_channel_id`(`channel_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_discover_visible_status_id`(`visible` ASC, `status` ASC, `id` DESC) USING BTREE,
  INDEX `idx_discover_channel_visible_status_id`(`channel_id` ASC, `visible` ASC, `status` ASC, `id` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
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
  INDEX `idx_note_id`(`note_id` ASC) USING BTREE,
  INDEX `idx_user_status_create_id`(`user_id` ASC, `status` ASC, `create_time` ASC, `id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记收藏表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
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
  UNIQUE INDEX `uk_user_id_note_id`(`user_id` ASC, `note_id` ASC) USING BTREE,
  INDEX `idx_user_status_create_id`(`user_id` ASC, `status` ASC, `create_time` ASC, `id` ASC) USING BTREE,
  INDEX `idx_note_id_status`(`note_id` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记点赞表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
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

-- ----------------------------
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
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;


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

SET FOREIGN_KEY_CHECKS = 0;

-- ============ fishhub_user ============
USE `fishhub_user`;
TRUNCATE TABLE `t_user`;
INSERT INTO `t_user` SELECT * FROM `fishhub`.`t_user`;

USE `fishhub_user`;
TRUNCATE TABLE `t_role`;
INSERT INTO `t_role` SELECT * FROM `fishhub`.`t_role`;

USE `fishhub_user`;
TRUNCATE TABLE `t_permission`;
INSERT INTO `t_permission` SELECT * FROM `fishhub`.`t_permission`;

USE `fishhub_user`;
TRUNCATE TABLE `t_role_permission_rel`;
INSERT INTO `t_role_permission_rel` SELECT * FROM `fishhub`.`t_role_permission_rel`;

USE `fishhub_user`;
TRUNCATE TABLE `t_user_role_rel`;
INSERT INTO `t_user_role_rel` SELECT * FROM `fishhub`.`t_user_role_rel`;

USE `fishhub_user`;
TRUNCATE TABLE `t_mq_consume_record`;
INSERT INTO `t_mq_consume_record` SELECT * FROM `fishhub`.`t_mq_consume_record`;

-- ============ fishhub_note ============
USE `fishhub_note`;
TRUNCATE TABLE `t_note`;
INSERT INTO `t_note` SELECT * FROM `fishhub`.`t_note`;

USE `fishhub_note`;
TRUNCATE TABLE `t_topic`;
INSERT INTO `t_topic` SELECT * FROM `fishhub`.`t_topic`;

USE `fishhub_note`;
TRUNCATE TABLE `t_channel`;
INSERT INTO `t_channel` SELECT * FROM `fishhub`.`t_channel`;

USE `fishhub_note`;
TRUNCATE TABLE `t_channel_topic_rel`;
INSERT INTO `t_channel_topic_rel` SELECT * FROM `fishhub`.`t_channel_topic_rel`;

USE `fishhub_note`;
TRUNCATE TABLE `t_note_like`;
INSERT INTO `t_note_like` SELECT * FROM `fishhub`.`t_note_like`;

USE `fishhub_note`;
TRUNCATE TABLE `t_note_collection`;
INSERT INTO `t_note_collection` SELECT * FROM `fishhub`.`t_note_collection`;

USE `fishhub_note`;
TRUNCATE TABLE `t_mq_consume_record`;
INSERT INTO `t_mq_consume_record` SELECT * FROM `fishhub`.`t_mq_consume_record`;

-- ============ fishhub_comment ============
USE `fishhub_comment`;
TRUNCATE TABLE `t_comment`;
INSERT INTO `t_comment` SELECT * FROM `fishhub`.`t_comment`;

USE `fishhub_comment`;
TRUNCATE TABLE `t_comment_like`;
INSERT INTO `t_comment_like` SELECT * FROM `fishhub`.`t_comment_like`;

USE `fishhub_comment`;
TRUNCATE TABLE `t_mq_consume_record`;
INSERT INTO `t_mq_consume_record` SELECT * FROM `fishhub`.`t_mq_consume_record`;

-- ============ fishhub_relation ============
USE `fishhub_relation`;
TRUNCATE TABLE `t_following`;
INSERT INTO `t_following` SELECT * FROM `fishhub`.`t_following`;

USE `fishhub_relation`;
TRUNCATE TABLE `t_mq_consume_record`;
INSERT INTO `t_mq_consume_record` SELECT * FROM `fishhub`.`t_mq_consume_record`;

-- ============ fishhub_count ============
USE `fishhub_count`;
TRUNCATE TABLE `t_note_count`;
INSERT INTO `t_note_count` SELECT * FROM `fishhub`.`t_note_count`;

USE `fishhub_count`;
TRUNCATE TABLE `t_user_count`;
INSERT INTO `t_user_count` SELECT * FROM `fishhub`.`t_user_count`;

USE `fishhub_count`;
TRUNCATE TABLE `t_tx_journal`;
INSERT INTO `t_tx_journal` SELECT * FROM `fishhub`.`t_tx_journal`;

USE `fishhub_count`;
TRUNCATE TABLE `t_mq_consume_record`;
INSERT INTO `t_mq_consume_record` SELECT * FROM `fishhub`.`t_mq_consume_record`;

SET FOREIGN_KEY_CHECKS = 1;


SET NAMES utf8mb4;

-- 1) 评论数对账：t_note_count.comment_total 应等于 fishhub_comment 中一级评论数
--    （注释：comment_total 语义为一级评论数；子评论数记在 t_comment.child_comment_total）
SELECT 'note_comment_total_mismatch' AS check_name, nc.note_id, nc.comment_total AS db_total,
       (SELECT COUNT(*) FROM fishhub_comment.t_comment c WHERE c.note_id = nc.note_id AND c.`level` = 1) AS actual_total
FROM fishhub_count.t_note_count nc
HAVING db_total != actual_total;

-- 2) 笔记点赞数对账
SELECT 'note_like_total_mismatch' AS check_name, nc.note_id, nc.like_total AS db_total,
       (SELECT COUNT(*) FROM fishhub_note.t_note_like l WHERE l.note_id = nc.note_id) AS actual_total
FROM fishhub_count.t_note_count nc
HAVING db_total != actual_total;

-- 3) 笔记收藏数对账
SELECT 'note_collect_total_mismatch' AS check_name, nc.note_id, nc.collect_total AS db_total,
       (SELECT COUNT(*) FROM fishhub_note.t_note_collection c WHERE c.note_id = nc.note_id) AS actual_total
FROM fishhub_count.t_note_count nc
HAVING db_total != actual_total;

-- 4) 粉丝/关注数对账（t_user_count.fans_total 与 following 反向统计）
SELECT 'user_fans_total_mismatch' AS check_name, uc.user_id, uc.fans_total AS db_total,
       (SELECT COUNT(*) FROM fishhub_relation.t_following f WHERE f.following_user_id = uc.user_id) AS actual_total
FROM fishhub_count.t_user_count uc
HAVING db_total != actual_total;

-- 5) 评论点赞数对账（like_total 归属 t_comment，本服务自持）
SELECT 'comment_like_total_mismatch' AS check_name, c.id AS comment_id, c.like_total AS db_total,
       (SELECT COUNT(*) FROM fishhub_comment.t_comment_like l WHERE l.comment_id = c.id) AS actual_total
FROM fishhub_comment.t_comment c
HAVING db_total != actual_total;

-- 说明：子评论总数 child_comment_total 与 first_reply_comment_id 在评论服务事务内维护，
-- 若怀疑漂移，可对比 t_comment.child_comment_total 与 level=2 的父评论计数（量小可全量核对）。

-- 存量库升级：评论图片 URL 列扩容（幂等；全新安装自动空跑，已在上方建表为 varchar(255)）
ALTER TABLE `fishhub_comment`.`t_comment` MODIFY COLUMN `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '评论附加图片URL';

USE `fishhub_user`;
INSERT INTO t_user (fishhub_id, password, nickname, phone, status) VALUES
 ('smoke001', '$2a$10$mw1jq3XPBKtAEancOOJsLuSAPhxpwbnPhf6m/gb5gWvJhCuYl.CAC', 'SmokeUser1', '13811110001', 0),
 ('smoke002', '$2a$10$mw1jq3XPBKtAEancOOJsLuSAPhxpwbnPhf6m/gb5gWvJhCuYl.CAC', 'SmokeUser2', '13811110002', 0)
 ON DUPLICATE KEY UPDATE password=VALUES(password), nickname=VALUES(nickname);

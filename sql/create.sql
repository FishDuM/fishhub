SET NAMES utf8mb4;

-- ====================================================================
-- 1. 用户服务库: fishhub_user
-- ====================================================================
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

-- 消费幂等记录
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


-- ====================================================================
-- 2. 笔记服务库: fishhub_note
-- ====================================================================
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

-- 消费幂等记录
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


-- ====================================================================
-- 3. 评论服务库: fishhub_comment
-- ====================================================================
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

-- 消费幂等记录
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


-- ====================================================================
-- 4. 计数服务库: fishhub_count
-- ====================================================================
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

-- 消费幂等记录
CREATE TABLE IF NOT EXISTS `t_mq_consume_record` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `consumer_group` varchar(128) NOT NULL,
  `message_key` char(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consumer_message` (`consumer_group`, `message_key`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RocketMQ 消费幂等记录';


-- ====================================================================
-- 5. 关系服务库 (预留多库隔离使用): fishhub_relation
-- ====================================================================
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

-- 消费幂等记录
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

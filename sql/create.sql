SET NAMES utf8mb4;

-- ====================================================================
-- 飞鱼社区 (FishHub) 统一主库: fishhub
-- 架构原则：元数据与大文本分离（MySQL 存结构化元数据与关系，Cassandra 存大文本）
-- ====================================================================
CREATE DATABASE IF NOT EXISTS `fishhub` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `fishhub`;

-- --------------------------------------------------------------------
-- 1. 用户体系 (User)
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_user` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `fishhub_id` varchar(32) NOT NULL COMMENT '飞鱼社区号(唯一凭证)',
  `phone` varchar(11) NOT NULL COMMENT '手机号',
  `password` varchar(64) DEFAULT NULL COMMENT '密码',
  `nickname` varchar(24) NOT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `background_img` varchar(255) DEFAULT NULL COMMENT '背景图',
  `introduction` varchar(100) DEFAULT NULL COMMENT '个人简介',
  `sex` tinyint DEFAULT 0 COMMENT '性别(0:女 1:男)',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态(0:启用 1:禁用)',
  
  -- 内聚计数字段 (替代独立 t_user_count 表)
  `fans_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '粉丝总数',
  `following_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '关注总数',
  `note_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '发布笔记数',
  `like_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '获赞总数',
  `collect_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '获收藏总数',
  
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_phone` (`phone`) USING BTREE,
  UNIQUE KEY `uk_fishhub_id` (`fishhub_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2101 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_following` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '关注人ID',
  `following_user_id` bigint UNSIGNED NOT NULL COMMENT '被关注人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_following` (`user_id`, `following_user_id`) USING BTREE,
  INDEX `idx_following_target` (`following_user_id`, `id` DESC) USING BTREE COMMENT '反向查粉丝列表'
) ENGINE = InnoDB AUTO_INCREMENT = 91309 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户关注列表' ROW_FORMAT = DYNAMIC;


-- --------------------------------------------------------------------
-- 2. 笔记与分类体系 (Note & Category)
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_category` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(32) NOT NULL COMMENT '分类/话题名称',
  `type` tinyint NOT NULL DEFAULT 1 COMMENT '类型(1:频道/板块 2:话题标签)',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序权重',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态(0:启用 1:禁用)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type_status` (`type`, `status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 50 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '内容分类与话题表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_note` (
  `id` bigint UNSIGNED NOT NULL COMMENT '主键ID',
  `creator_id` bigint UNSIGNED NOT NULL COMMENT '发布者ID',
  `title` varchar(64) NOT NULL COMMENT '标题',
  `content_uuid` varchar(36) NOT NULL DEFAULT '' COMMENT 'Cassandra 笔记正文关联 UUID',
  `type` tinyint NOT NULL DEFAULT 0 COMMENT '类型(0:图文 1:视频)',
  `img_uris` varchar(1024) DEFAULT NULL COMMENT '笔记图片链接列表(逗号分隔)',
  `video_uri` varchar(255) DEFAULT NULL COMMENT '视频链接',
  `channel_id` bigint UNSIGNED DEFAULT NULL COMMENT '所属频道ID(关联 t_category)',
  `topic_id` bigint UNSIGNED DEFAULT NULL COMMENT '所属话题ID(关联 t_category)',
  `topic_name` varchar(32) DEFAULT NULL COMMENT '话题名称',
  `is_top` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否置顶(0:未置顶 1:置顶)',
  `visible` tinyint NOT NULL DEFAULT 0 COMMENT '可见范围(0:所有人可见 1:仅对自己可见)',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(0:待审核 1:正常展示 2:被删除 3:被下架)',
  
  -- 内聚计数字段 (替代独立 t_note_count 表)
  `like_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞总数',
  `collect_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '收藏总数',
  `comment_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论总数',
  
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_creator` (`creator_id`, `status`) USING BTREE,
  INDEX `idx_channel` (`channel_id`, `visible`, `status`, `id` DESC) USING BTREE,
  INDEX `idx_topic` (`topic_id`, `visible`, `status`, `id` DESC) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记元数据表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_note_like` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `note_id` bigint UNSIGNED NOT NULL COMMENT '笔记ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_note` (`user_id`, `note_id`) USING BTREE,
  INDEX `idx_note_id` (`note_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记点赞表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `t_note_collection` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `note_id` bigint UNSIGNED NOT NULL COMMENT '笔记ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_note` (`user_id`, `note_id`) USING BTREE,
  INDEX `idx_note_id` (`note_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记收藏表' ROW_FORMAT = DYNAMIC;


-- --------------------------------------------------------------------
-- 3. 评论体系 (Comment)
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_comment` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `note_id` bigint UNSIGNED NOT NULL COMMENT '关联笔记ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '发布者用户ID',
  `content_uuid` varchar(36) NOT NULL DEFAULT '' COMMENT 'Cassandra 评论正文关联 UUID',
  `is_content_empty` bit(1) NOT NULL DEFAULT b'0' COMMENT '正文是否为空',
  `image_url` varchar(255) NOT NULL DEFAULT '' COMMENT '评论附加图片URL',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '层级(1:一级评论 2:二级评论)',
  `parent_id` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '父ID(一级评论为0，二级评论存储一级评论ID)',
  `reply_user_id` bigint UNSIGNED DEFAULT 0 COMMENT '被回复者用户ID',
  `reply_comment_id` bigint UNSIGNED DEFAULT 0 COMMENT '被回复的具体评论ID',
  `like_total` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞总数',
  `child_comment_total` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '二级子评论总数',
  `first_reply_comment_id` bigint UNSIGNED NOT NULL DEFAULT 0 COMMENT '首条二级回复评论ID(加速首屏)',
  `heat` double NOT NULL DEFAULT 0 COMMENT '热度值',
  `is_top` tinyint NOT NULL DEFAULT 0 COMMENT '是否置顶(0:不置顶 1:置顶)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_note_level` (`note_id`, `level`, `id` DESC) USING BTREE COMMENT '查询笔记根评论列表',
  INDEX `idx_parent_id` (`parent_id`, `create_time` ASC) USING BTREE COMMENT '查询楼中楼二级回复列表'
) ENGINE = InnoDB AUTO_INCREMENT = 38003 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评论元数据表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `t_comment_like` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '用户ID',
  `comment_id` bigint UNSIGNED NOT NULL COMMENT '评论ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_comment` (`user_id`, `comment_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 51 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '评论点赞表' ROW_FORMAT = Dynamic;


-- --------------------------------------------------------------------
-- 4. 基础设施表 (全局 1 套)
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_mq_consume_record` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `consumer_group` varchar(128) NOT NULL,
  `message_key` char(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consumer_message` (`consumer_group`, `message_key`),
  KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'RocketMQ 消费幂等记录';

CREATE TABLE IF NOT EXISTS `t_tx_journal` (
  `tx_id` varchar(64) NOT NULL COMMENT '事务消息 txId（回查判定键）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`tx_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '事务消息本地事务回查日志';


-- ====================================================================
-- 5. 初始基础数据 (Initial Seed Data)
-- ====================================================================

-- 5.1 频道与话题分类数据
INSERT IGNORE INTO `t_category` (`id`, `name`, `type`, `sort_order`, `status`) VALUES
-- 核心频道 (type=1)
(1, '推荐', 1, 100, 0),
(2, '穿搭', 1, 90, 0),
(3, '美食', 1, 80, 0),
(4, '旅行', 1, 70, 0),
(5, '科技', 1, 60, 0),
(6, '影视', 1, 50, 0),
(7, '运动', 1, 40, 0),
(8, '游戏', 1, 30, 0),
-- 热门话题标签 (type=2)
(20, '日常分享', 2, 100, 0),
(21, '程序员日常', 2, 90, 0),
(22, '周末去哪儿', 2, 80, 0),
(23, '深夜食堂', 2, 70, 0),
(24, '硬核科技', 2, 60, 0),
(25, '摄影记录', 2, 50, 0);

-- 5.2 初始测试用户 (统一初始密码: 123456, BCrypt 加密)
INSERT IGNORE INTO `t_user` (`id`, `fishhub_id`, `phone`, `password`, `nickname`, `avatar`, `background_img`, `introduction`, `sex`, `birthday`, `status`, `fans_count`, `following_count`, `note_count`, `like_count`, `collect_count`) VALUES
(1001, 'fishhub_admin', '13800000000', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '飞鱼官方助手', 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150', 'https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=800', '欢迎来到飞鱼社区！分享你的美好生活与硬核技术', 1, '2000-01-01', 0, 3, 0, 1, 128, 64),
(1002, 'tech_geek',     '13800000001', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '极客先锋',     'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150', 'https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=800', '专注于分布式高并发架构与 JDK21 前沿探索', 1, '1998-06-18', 0, 2, 1, 1, 96, 48),
(1003, 'foodie_cat',    '13800000002', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '深夜食堂喵',   'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=150', 'https://images.unsplash.com/photo-1498837167922-ddd27525d352?w=800', '吃遍世界各地街头巷尾的烟火气美食', 0, '1999-10-24', 0, 1, 2, 1, 85, 32),
(1004, 'traveler_bob',  '13800000003', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '山野背包客',   'https://images.unsplash.com/photo-1527980965255-d3b416303d12?w=150', 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800', '路虽远，行则将至；去探索未知的山川与旷野', 1, '1997-03-15', 0, 0, 3, 1, 45, 20);

-- 5.3 初始关注关系
INSERT IGNORE INTO `t_following` (`user_id`, `following_user_id`) VALUES
(1002, 1001),
(1003, 1001),
(1003, 1002),
(1004, 1001),
(1004, 1002),
(1004, 1003);

-- 5.4 初始推荐笔记
INSERT IGNORE INTO `t_note` (`id`, `creator_id`, `title`, `content_uuid`, `type`, `img_uris`, `video_uri`, `channel_id`, `topic_id`, `topic_name`, `is_top`, `visible`, `status`, `like_count`, `collect_count`, `comment_count`) VALUES
(10001, 1001, '欢迎来到飞鱼社区！一起探索精彩的多元生活', '', 0, 'https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=600', NULL, 1, 20, '日常分享', b'1', 0, 1, 128, 64, 2),
(10002, 1002, '基于 Spring Cloud Alibaba + JDK 21 现代微服务架构实战', '', 0, 'https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=600', NULL, 5, 21, '程序员日常', b'0', 0, 1, 96, 48, 1),
(10003, 1003, '藏在胡同深处的百年地道老店！这口爆肚真的太绝了', '', 0, 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=600', NULL, 3, 23, '深夜食堂', b'0', 0, 1, 85, 32, 0),
(10004, 1004, '川西自驾大环线超详细攻略，新手必看避坑指南', '', 0, 'https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?w=600', NULL, 4, 22, '周末去哪儿', b'0', 0, 1, 45, 20, 0);

-- 5.5 初始笔记点赞与收藏记录
INSERT IGNORE INTO `t_note_like` (`user_id`, `note_id`) VALUES
(1002, 10001),
(1003, 10001),
(1004, 10001),
(1003, 10002),
(1004, 10002);

INSERT IGNORE INTO `t_note_collection` (`user_id`, `note_id`) VALUES
(1002, 10001),
(1003, 10001),
(1004, 10002);

-- 5.6 初始评论与二级回复树
INSERT IGNORE INTO `t_comment` (`id`, `note_id`, `user_id`, `content_uuid`, `is_content_empty`, `image_url`, `level`, `parent_id`, `reply_user_id`, `reply_comment_id`, `like_total`, `child_comment_total`, `first_reply_comment_id`, `heat`, `is_top`, `is_deleted`) VALUES
(20001, 10001, 1002, '', b'1', '', 1, 0, 0, 0, 5, 1, 20002, 10.5, 0, b'0'),
(20002, 10001, 1001, '', b'1', '', 2, 20001, 1002, 20001, 2, 0, 0, 5.0, 0, b'0'),
(20003, 10002, 1003, '', b'1', '', 1, 0, 0, 0, 8, 0, 0, 12.0, 0, b'0');

INSERT IGNORE INTO `t_comment_like` (`user_id`, `comment_id`) VALUES
(1003, 20001),
(1004, 20001),
(1001, 20003);

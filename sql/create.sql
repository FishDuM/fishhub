create database if not exists `fishhub`;

use `fishhub`;

create table if not exists `t_user` (
                                        `id` bigint(20) unsigned not null auto_increment comment '主键',
                                        `username` varchar(64) not null comment '用户名',
                                        `create_time` datetime not null default current_timestamp comment '创建时间',
                                        `update_time` datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                                        primary key (`id`) using btree)engine = innodb comment = '用户表';
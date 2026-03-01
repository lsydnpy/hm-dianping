package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Data

// @EqualsAndHashCode 注解用于自动生成 equals() 和 hashCode() 方法
// callSuper = false 表示在比较对象是否相等时，不调用父类的 equals() 方法
// 因为 BlogComments 只实现了 Serializable 接口，没有实际的业务父类
// 所以设置为 false，仅比较当前类的字段
@EqualsAndHashCode(callSuper = false)

// @Accessors(chain = true) 启用链式调用，允许连续设置多个属性
// 例如: blogComments.setUserId(1L).setBlogId(2L).setContent("评论内容")
@Accessors(chain = true)

// @TableName 注解用于指定实体类对应的数据库表名
// 将 BlogComments 实体类映射到数据库中的 tb_blog_comments 表
// 如果不使用该注解，MyBatis-Plus 会默认使用类名的驼峰转下划线形式作为表名
@TableName("tb_blog_comments")
public class BlogComments implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 探店id
     */
    private Long blogId;

    /**
     * 关联的1级评论id，如果是一级评论，则值为0
     */
    private Long parentId;

    /**
     * 回复的评论id
     */
    private Long answerId;

    /**
     * 回复的内容
     */
    private String content;

    /**
     * 点赞数
     */
    private Integer liked;

    /**
     * 状态，0：正常，1：被举报，2：禁止查看
     */
    private Boolean status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}

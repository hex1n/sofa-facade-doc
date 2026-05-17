package com.company.loan.facade.dto;

import java.util.List;

/**
 * 分页结果
 */
public class PageResult<T> {
    /**
     * 数据列表
     */
    private List<T> items;

    /**
     * 总数量
     */
    private Long total;
}

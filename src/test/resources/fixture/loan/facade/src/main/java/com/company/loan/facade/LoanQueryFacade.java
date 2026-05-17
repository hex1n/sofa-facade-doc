package com.company.loan.facade;

import com.company.loan.facade.dto.QueryRequest;

/**
 * 贷款查询接口
 */
public interface LoanQueryFacade {
    /**
     * 查询订单状态
     *
     * @param request 查询请求，必填
     * @return 状态文本
     */
    String queryStatus(QueryRequest request);
}


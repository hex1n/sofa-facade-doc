package com.company.loan.facade;

import com.company.loan.facade.dto.*;
import com.company.partner.ExternalApplyRequest;

/**
 * 贷款申请接口
 */
public interface LoanApplyFacade {
    /**
     * 提交贷款申请
     *
     * @param request 申请请求，必填
     * @return 申请结果
     */
    ApplyResponse submitApply(ApplyRequest request) throws IllegalArgumentException;

    /**
     * 按订单号提交贷款申请
     *
     * @param orderNo 订单号，必填
     * @return 申请结果
     */
    ApplyResponse submitApply(String orderNo);

    /**
     * 提交贷款申请并记录操作员
     *
     * @param orderNo 订单号，必填
     * @param request 申请请求，必填
     * @return 申请结果
     */
    ApplyResponse submitApply(String orderNo, ApplyRequest request);

    /**
     * 查询申请分页
     *
     * @param request 查询请求，必填
     * @return 申请分页
     */
    PageResult<ApplyResponse> listApplications(ApplyRequest request);

    /**
     * 查询审计信息
     *
     * @param orderNo 订单号，必填
     * @return 审计信息
     */
    AuditInfo audit(String orderNo);

    /**
     * 提交外部联合申请
     *
     * @param request 外部申请，必填
     * @return 外部申请受理号
     */
    String submitExternalApply(ExternalApplyRequest request);

    class AuditInfo {
        /**
         * 操作员
         */
        private String operator;
    }
}

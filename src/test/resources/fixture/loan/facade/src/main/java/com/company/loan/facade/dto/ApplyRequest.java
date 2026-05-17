package com.company.loan.facade.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * 申请请求
 */
public class ApplyRequest {
    private static final long serialVersionUID = 1L;

    /**
     * 订单号，必填
     */
    @JsonProperty(value = "order_no", required = true)
    private String orderNo;

    // 申请金额，必填
    @javax.validation.constraints.NotNull
    private BigDecimal amount;

    private ApplyStatus status; // 申请状态，选填

    @JsonIgnore
    private String internalTraceId;

    private transient String localCacheKey;
}

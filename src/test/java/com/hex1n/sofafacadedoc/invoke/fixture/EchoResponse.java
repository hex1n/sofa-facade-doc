package com.hex1n.sofafacadedoc.invoke.fixture;

import java.math.BigDecimal;

public class EchoResponse {
    private boolean success;
    private String orderNo;
    private BigDecimal amount;
    private EchoStatus status;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public EchoStatus getStatus() {
        return status;
    }

    public void setStatus(EchoStatus status) {
        this.status = status;
    }
}

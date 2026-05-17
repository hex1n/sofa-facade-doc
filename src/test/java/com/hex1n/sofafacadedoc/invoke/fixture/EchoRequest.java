package com.hex1n.sofafacadedoc.invoke.fixture;

import java.math.BigDecimal;

public class EchoRequest {
    private String orderNo;
    private BigDecimal amount;
    private EchoStatus status;

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

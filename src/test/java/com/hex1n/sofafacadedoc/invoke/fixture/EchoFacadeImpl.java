package com.hex1n.sofafacadedoc.invoke.fixture;

public class EchoFacadeImpl implements EchoFacade {
    @Override
    public EchoResponse submit(EchoRequest request) {
        if ("FAIL".equals(request.getOrderNo())) {
            throw new IllegalStateException("business rejected " + request.getOrderNo());
        }
        EchoResponse response = new EchoResponse();
        response.setSuccess(true);
        response.setOrderNo(request.getOrderNo());
        response.setAmount(request.getAmount());
        response.setStatus(request.getStatus());
        return response;
    }
}

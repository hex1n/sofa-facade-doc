package com.company.loan.service;

import com.alipay.sofa.runtime.api.annotation.SofaService;
import com.alipay.sofa.runtime.api.annotation.SofaServiceBinding;
import com.company.loan.facade.LoanApplyFacade;
import com.company.loan.facade.dto.ApplyRequest;
import com.company.loan.facade.dto.ApplyResponse;

@SofaService(interfaceType = LoanApplyFacade.class, uniqueId = "test", bindings = @SofaServiceBinding(bindingType = "bolt"))
public class LoanApplyFacadeImpl implements LoanApplyFacade {
    public ApplyResponse submitApply(ApplyRequest request) {
        return null;
    }
}


package com.lumintra.backend.commerce.domain.service;

import com.lumintra.backend.commerce.domain.model.CarrierDispatchCommand;
import com.lumintra.backend.commerce.domain.model.CarrierDispatchResult;

public interface CarrierDispatchPort {

    CarrierDispatchResult createDeliveryOrder(CarrierDispatchCommand command);
}

package com.ipo.manager.controller;

import com.ipo.manager.domain.BrokerFee;
import com.ipo.manager.service.BrokerFeeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/broker-fees")
public class BrokerFeeApiController {

    private final BrokerFeeService brokerFeeService;

    public BrokerFeeApiController(BrokerFeeService brokerFeeService) {
        this.brokerFeeService = brokerFeeService;
    }

    @GetMapping
    public List<BrokerFee> list() {
        return brokerFeeService.getAll();
    }

    @PutMapping("/{name}")
    public BrokerFee update(@PathVariable String name, @RequestBody Map<String, Long> body) {
        return brokerFeeService.updateByName(name, body.get("feeAmount"));
    }
}

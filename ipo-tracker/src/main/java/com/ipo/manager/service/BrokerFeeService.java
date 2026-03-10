package com.ipo.manager.service;

import com.ipo.manager.domain.BrokerFee;
import com.ipo.manager.repository.BrokerFeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class BrokerFeeService {

    private final BrokerFeeRepository repository;

    public BrokerFeeService(BrokerFeeRepository repository) {
        this.repository = repository;
    }

    public List<BrokerFee> getAll() {
        return repository.findAll();
    }

    public BrokerFee updateByName(String name, Long feeAmount) {
        BrokerFee fee = repository.findByBrokerName(name)
                .orElseThrow(() -> new RuntimeException("Broker not found: " + name));
        fee.setFeeAmount(feeAmount);
        return repository.save(fee);
    }
}

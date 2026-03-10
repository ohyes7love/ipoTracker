package com.ipo.manager.repository;

import com.ipo.manager.domain.BrokerFee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BrokerFeeRepository extends JpaRepository<BrokerFee, Long> {
    Optional<BrokerFee> findByBrokerName(String brokerName);
}

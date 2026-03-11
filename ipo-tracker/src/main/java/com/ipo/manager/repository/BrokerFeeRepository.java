package com.ipo.manager.repository;

import com.ipo.manager.domain.BrokerFee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BrokerFeeRepository {
    List<BrokerFee> findAll();
    BrokerFee findByBrokerName(@Param("brokerName") String brokerName);
    void update(BrokerFee fee);
}

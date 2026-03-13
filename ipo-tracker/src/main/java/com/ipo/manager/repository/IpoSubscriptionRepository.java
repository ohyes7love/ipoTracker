package com.ipo.manager.repository;

import com.ipo.manager.domain.IpoSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IpoSubscriptionRepository {
    List<IpoSubscription> findAll();
    List<IpoSubscription> findByYear(@Param("year") int year);
    List<IpoSubscription> findByYearWithSoldDate(@Param("year") int year);
    IpoSubscription findById(@Param("id") Long id);
    void insert(IpoSubscription entity);
    void update(IpoSubscription entity);
    void deleteById(@Param("id") Long id);
}

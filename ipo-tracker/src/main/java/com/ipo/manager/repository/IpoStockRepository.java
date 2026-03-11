package com.ipo.manager.repository;

import com.ipo.manager.domain.IpoStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IpoStockRepository {
    List<IpoStock> findAll();
    IpoStock findByStockName(@Param("stockName") String stockName);
    void insert(IpoStock stock);
    void update(IpoStock stock);
}

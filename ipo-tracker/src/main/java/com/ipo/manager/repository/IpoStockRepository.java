package com.ipo.manager.repository;

import com.ipo.manager.domain.IpoStock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IpoStockRepository extends JpaRepository<IpoStock, Long> {
    Optional<IpoStock> findByStockName(String stockName);
}

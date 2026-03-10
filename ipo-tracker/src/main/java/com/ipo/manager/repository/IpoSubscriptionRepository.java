package com.ipo.manager.repository;

import com.ipo.manager.domain.IpoSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IpoSubscriptionRepository extends JpaRepository<IpoSubscription, Long> {
    List<IpoSubscription> findByYearOrderByIdDesc(int year);
    List<IpoSubscription> findBySoldDateIsNotNullAndYear(int year);
}

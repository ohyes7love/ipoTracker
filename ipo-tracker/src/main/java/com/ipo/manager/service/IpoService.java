package com.ipo.manager.service;

import com.ipo.manager.domain.IpoStock;
import com.ipo.manager.domain.IpoSubscription;
import com.ipo.manager.dto.IpoDto;
import com.ipo.manager.dto.MonthlySummaryDto;
import com.ipo.manager.repository.IpoStockRepository;
import com.ipo.manager.repository.IpoSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class IpoService {

    private final IpoSubscriptionRepository repository;
    private final IpoStockRepository stockRepository;

    public IpoService(IpoSubscriptionRepository repository, IpoStockRepository stockRepository) {
        this.repository = repository;
        this.stockRepository = stockRepository;
    }

    public List<IpoDto> getByYear(int year) {
        return repository.findByYearOrderByIdDesc(year)
                .stream().map(IpoDto::from).collect(Collectors.toList());
    }

    public IpoDto create(IpoDto dto) {
        IpoSubscription entity = dto.toEntity();
        ensureIpoStock(dto);
        return IpoDto.from(repository.save(entity));
    }

    public IpoDto update(Long id, IpoDto dto) {
        IpoSubscription entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found: " + id));
        entity.setStockName(dto.getStockName());
        entity.setBroker(dto.getBroker());
        entity.setOfferingPrice(dto.getOfferingPrice());
        entity.setSubscriptionQty(dto.getSubscriptionQty());
        entity.setAllocatedQty(dto.getAllocatedQty());
        entity.setSoldQty(dto.getSoldQty() != null ? dto.getSoldQty() : 0);
        entity.setSoldDate(dto.getSoldDate());
        entity.setSoldPrice(dto.getSoldPrice());
        entity.setTaxAndFee(dto.getTaxAndFee() != null ? dto.getTaxAndFee() : 0L);
        entity.setSubscriptionFee(dto.getSubscriptionFee() != null ? dto.getSubscriptionFee() : 0L);
        entity.setYear(dto.getYear() != null ? dto.getYear() : entity.getYear());
        ensureIpoStock(dto);
        return IpoDto.from(repository.save(entity));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<MonthlySummaryDto> getMonthlySummary(int year) {
        List<IpoSubscription> soldList = repository.findBySoldDateIsNotNullAndYear(year);
        Map<Integer, Long> monthlyMap = soldList.stream()
                .filter(e -> e.getProfit() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getSoldDate().getMonthValue(),
                        Collectors.summingLong(IpoSubscription::getProfit)
                ));
        List<MonthlySummaryDto> result = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            result.add(new MonthlySummaryDto(m, monthlyMap.getOrDefault(m, 0L)));
        }
        return result;
    }

    // IpoStock methods
    public List<IpoStock> getAllStocks() {
        return stockRepository.findAll();
    }

    public IpoStock createStock(IpoStock stock) {
        return stockRepository.save(stock);
    }

    private void ensureIpoStock(IpoDto dto) {
        if (dto.getStockName() == null || dto.getStockName().isBlank()) return;
        stockRepository.findByStockName(dto.getStockName()).ifPresentOrElse(
                existing -> {
                    if (dto.getOfferingPrice() != null) existing.setOfferingPrice(dto.getOfferingPrice());
                    stockRepository.save(existing);
                },
                () -> {
                    IpoStock stock = new IpoStock();
                    stock.setStockName(dto.getStockName());
                    stock.setOfferingPrice(dto.getOfferingPrice());
                    stockRepository.save(stock);
                }
        );
    }
}

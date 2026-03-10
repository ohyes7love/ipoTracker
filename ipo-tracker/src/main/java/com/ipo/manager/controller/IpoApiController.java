package com.ipo.manager.controller;

import com.ipo.manager.domain.IpoStock;
import com.ipo.manager.dto.IpoDto;
import com.ipo.manager.dto.MonthlySummaryDto;
import com.ipo.manager.service.IpoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ipo")
public class IpoApiController {

    private final IpoService ipoService;

    public IpoApiController(IpoService ipoService) {
        this.ipoService = ipoService;
    }

    @GetMapping
    public List<IpoDto> list(@RequestParam int year) {
        return ipoService.getByYear(year);
    }

    @PostMapping
    public IpoDto create(@RequestBody IpoDto dto) {
        return ipoService.create(dto);
    }

    @PutMapping("/{id}")
    public IpoDto update(@PathVariable Long id, @RequestBody IpoDto dto) {
        return ipoService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ipoService.delete(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/monthly")
    public List<MonthlySummaryDto> monthly(@RequestParam int year) {
        return ipoService.getMonthlySummary(year);
    }

    @GetMapping("/stocks")
    public List<IpoStock> stocks() {
        return ipoService.getAllStocks();
    }

    @PostMapping("/stocks")
    public IpoStock createStock(@RequestBody IpoStock stock) {
        return ipoService.createStock(stock);
    }
}

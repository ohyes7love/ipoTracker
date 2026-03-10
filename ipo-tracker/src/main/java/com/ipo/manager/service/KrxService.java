package com.ipo.manager.service;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KrxService {

    private final RestTemplate restTemplate;
    private List<Map<String, String>> cachedStocks = null;
    private LocalDateTime cacheTime = null;

    public KrxService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public List<Map<String, String>> getAllStocks() {
        if (cachedStocks != null && cacheTime != null
                && cacheTime.isAfter(LocalDateTime.now().minusHours(24))) {
            return cachedStocks;
        }
        List<Map<String, String>> result = new ArrayList<>();
        result.addAll(fetchStocks("STK")); // KOSPI
        result.addAll(fetchStocks("KSQ")); // KOSDAQ
        result.sort(Comparator.comparing(m -> m.get("stockName")));
        cachedStocks = result;
        cacheTime = LocalDateTime.now();
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> fetchStocks(String mktId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Referer", "https://data.krx.co.kr/contents/MDC/MDI/mdiBoardDetail/MDCMDI02010.cmd");
        headers.set("User-Agent", "Mozilla/5.0");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("bld", "dbms/MDC/STAT/standard/MDCSTAT01901");
        body.add("locale", "ko_KR");
        body.add("mktId", mktId);
        body.add("share", "1");
        body.add("money", "1");
        body.add("csvxls_isNo", "false");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd",
                request, Map.class);

        if (response.getBody() == null) return List.of();

        List<Map<String, Object>> block = (List<Map<String, Object>>) response.getBody().get("OutBlock_1");
        if (block == null) return List.of();

        return block.stream()
                .map(item -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("stockName", (String) item.get("ISU_ABBRV"));
                    return m;
                })
                .filter(m -> m.get("stockName") != null)
                .collect(Collectors.toList());
    }
}

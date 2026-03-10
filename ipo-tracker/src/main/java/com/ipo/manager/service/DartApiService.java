package com.ipo.manager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DartApiService {

    @Value("${dart.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public DartApiService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getRecentIpoList() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(3);
        String url = "https://opendart.fss.or.kr/api/list.json"
                + "?crtfc_key=" + apiKey
                + "&bgn_de=" + start.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&end_de=" + end.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&pblntf_ty=C&page_count=100";

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null || !"000".equals(response.get("status"))) {
            String msg = response != null ? (String) response.get("message") : "응답 없음";
            throw new RuntimeException("DART API 오류: " + msg);
        }

        List<Map<String, Object>> list = (List<Map<String, Object>>) response.get("list");
        if (list == null) return List.of();

        return list.stream()
                .filter(item -> {
                    String reportNm = (String) item.get("report_nm");
                    return reportNm != null && reportNm.contains("증권신고서") && reportNm.contains("지분증권");
                })
                .map(item -> {
                    Map<String, String> r = new LinkedHashMap<>();
                    r.put("corpName", (String) item.get("corp_name"));
                    r.put("receiptDate", (String) item.get("rcept_dt"));
                    return r;
                })
                .collect(Collectors.toList());
    }
}

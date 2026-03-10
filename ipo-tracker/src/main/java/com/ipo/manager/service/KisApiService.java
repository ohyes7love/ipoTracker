package com.ipo.manager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class KisApiService {

    private static final Logger log = LoggerFactory.getLogger(KisApiService.class);

    @Value("${kis.api.appkey:}")
    private String appKey;

    @Value("${kis.api.appsecret:}")
    private String appSecret;

    @Value("${kis.api.base-url:https://openapi.koreainvestment.com:9443}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String accessToken;
    private LocalDateTime tokenExpiry;

    public boolean isConfigured() {
        return appKey != null && !appKey.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    private synchronized String getAccessToken() {
        if (accessToken != null && tokenExpiry != null && LocalDateTime.now().isBefore(tokenExpiry)) {
            return accessToken;
        }

        String url = baseUrl + "/oauth2/tokenP";
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", appKey);
        body.put("appsecret", appSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            accessToken = json.get("access_token").asText();
            tokenExpiry = LocalDateTime.now().plusHours(23);
            log.info("KIS API token acquired, expires at {}", tokenExpiry);
            return accessToken;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get KIS access token", e);
        }
    }

    private HttpHeaders createHeaders(String trId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + getAccessToken());
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", trId);
        return headers;
    }

    /**
     * 현재가 조회
     * @return { "price": 72300, "change": 300, "changeRate": "0.42", "volume": 12345678 }
     */
    public Map<String, Object> getCurrentPrice(String stockCode) {
        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price"
                + "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=" + stockCode;

        HttpEntity<Void> request = new HttpEntity<>(createHeaders("FHKST01010100"));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            if (!"0".equals(json.get("rt_cd").asText())) {
                throw new RuntimeException("KIS API error: " + json.get("msg1").asText());
            }
            JsonNode output = json.get("output");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("price", Long.parseLong(output.get("stck_prpr").asText()));
            result.put("change", Long.parseLong(output.get("prdy_vrss").asText()));
            result.put("changeRate", output.get("prdy_ctrt").asText());
            result.put("volume", Long.parseLong(output.get("acml_vol").asText()));
            result.put("high", Long.parseLong(output.get("stck_hgpr").asText()));
            result.put("low", Long.parseLong(output.get("stck_lwpr").asText()));
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse KIS price response", e);
        }
    }

    /**
     * 특정일 종가 조회
     * @return { "date": "20251229", "close": 60000, "open": 55000, "high": 62000, "low": 54000 }
     */
    public Map<String, Object> getPriceOnDate(String stockCode, LocalDate date) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        String dateStr = date.format(fmt);

        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
                + "?FID_COND_MRKT_DIV_CODE=J"
                + "&FID_INPUT_ISCD=" + stockCode
                + "&FID_INPUT_DATE_1=" + dateStr
                + "&FID_INPUT_DATE_2=" + dateStr
                + "&FID_PERIOD_DIV_CODE=D"
                + "&FID_ORG_ADJ_PRC=0";

        HttpEntity<Void> request = new HttpEntity<>(createHeaders("FHKST03010100"));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            if (!"0".equals(json.get("rt_cd").asText())) {
                throw new RuntimeException("KIS API error: " + json.get("msg1").asText());
            }
            JsonNode output2 = json.get("output2");
            if (output2 == null || !output2.isArray() || output2.isEmpty()) {
                throw new RuntimeException("해당 날짜의 시세 데이터가 없습니다: " + dateStr);
            }
            JsonNode record = output2.get(0);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("date", record.get("stck_bsop_date").asText());
            result.put("close", Long.parseLong(record.get("stck_clpr").asText()));
            result.put("open", Long.parseLong(record.get("stck_oprc").asText()));
            result.put("high", Long.parseLong(record.get("stck_hgpr").asText()));
            result.put("low", Long.parseLong(record.get("stck_lwpr").asText()));
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse KIS daily price response", e);
        }
    }
}

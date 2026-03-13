package com.ipo.manager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Service
public class DartApiService {

    private static final Logger log = LoggerFactory.getLogger(DartApiService.class);

    @Value("${dart.api.key:}")
    private String apiKey;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Apache HttpClient 5: 연결 풀링 + TLS 자동 처리
    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build();

    // ipo1.json 동시 호출 3개로 제한 (Connection reset 방지)
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ── 공시 목록 캐시 ─────────────────────────────────────────────────────────
    private List<Map<String, String>> ipoFilingsCache = null;
    private LocalDate cacheDate = null;

    // ── IPO 일정 캐시: rceptNo → schedule (ConcurrentHashMap, null 불가 → FAILED_SENTINEL 사용)
    private static final Map<String, String> FAILED_SENTINEL = Map.of();
    private final Map<String, Map<String, String>> ipoScheduleCache = new ConcurrentHashMap<>();
    private LocalDate scheduleCacheDate = null;

    @SuppressWarnings("unchecked")
    private Map<String, Object> dartGet(String url) throws Exception {
        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "application/json, text/plain, */*");
        request.setHeader("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8");
        request.setHeader("Referer", "https://opendart.fss.or.kr/");

        return HTTP_CLIENT.execute(request, response -> {
            log.debug("[DART] HTTP {} {}", response.getCode(), url);
            return OBJECT_MAPPER.readValue(response.getEntity().getContent(), Map.class);
        });
    }

    /** 자동완성: 회사명 검색 */
    public List<String> searchCorpName(String query) {
        if (!isConfigured() || query == null || query.isBlank()) return List.of();
        try {
            return getIpoFilingsCache().stream()
                    .map(m -> m.get("corpName"))
                    .filter(name -> name != null && name.contains(query))
                    .distinct().limit(10)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 캘린더용: 특정 연월의 IPO 청약/상장 일정 */
    public List<Map<String, String>> getIpoSchedulesByMonth(int year, int month) {
        if (!isConfigured()) return List.of();
        try {
            String yearMonth = String.format("%04d-%02d", year, month);
            return getAllIpoSchedules().stream()
                    .filter(s -> {
                        String start   = s.get("subscriptionStartDate");
                        String end     = s.get("subscriptionEndDate");
                        String listing = s.get("listingDate");
                        return (start   != null && start.startsWith(yearMonth))
                            || (end     != null && end.startsWith(yearMonth))
                            || (listing != null && listing.startsWith(yearMonth));
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[DART] getIpoSchedulesByMonth error", e);
            return List.of();
        }
    }

    /** ipo1.json 제한 병렬(3개) 호출로 일정 캐시 구성 */
    private List<Map<String, String>> getAllIpoSchedules() {
        if (!LocalDate.now().equals(scheduleCacheDate)) {
            ipoScheduleCache.clear();
            scheduleCacheDate = LocalDate.now();
        }

        List<Map<String, String>> filings = getIpoFilingsCache();
        List<Map<String, String>> missing = filings.stream()
                .filter(f -> f.get("rceptNo") != null && !ipoScheduleCache.containsKey(f.get("rceptNo")))
                .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            List<Future<?>> futures = missing.stream()
                    .map(f -> executor.submit(() -> fetchIpoSchedule(f)))
                    .collect(Collectors.toList());
            for (Future<?> future : futures) {
                try { future.get(); } catch (Exception ignored) {}
            }
        }

        return ipoScheduleCache.values().stream()
                .filter(s -> s != FAILED_SENTINEL)
                .collect(Collectors.toList());
    }

    private void fetchIpoSchedule(Map<String, String> filing) {
        String rceptNo = filing.get("rceptNo");
        try {
            String url = "https://opendart.fss.or.kr/api/ipo1.json"
                    + "?crtfc_key=" + apiKey
                    + "&rcept_no=" + rceptNo;
            Map<String, Object> resp = dartGet(url);

            if (resp == null) {
                ipoScheduleCache.put(rceptNo, FAILED_SENTINEL);
                return;
            }

            String status = (String) resp.get("status");
            log.info("[DART] ipo1 rcept_no={} status={} subscr_sttd={} lstg_dt={}",
                    rceptNo, status, resp.get("subscr_sttd"), resp.get("lstg_dt"));

            if (!"000".equals(status)) {
                ipoScheduleCache.put(rceptNo, FAILED_SENTINEL);
                return;
            }

            Map<String, String> schedule = new LinkedHashMap<>();
            schedule.put("corpName", filing.get("corpName"));
            putDate(schedule, "subscriptionStartDate", (String) resp.get("subscr_sttd"));
            putDate(schedule, "subscriptionEndDate",   (String) resp.get("subscr_end_d"));
            putDate(schedule, "listingDate",           (String) resp.get("lstg_dt"));
            ipoScheduleCache.put(rceptNo, schedule);

        } catch (Exception e) {
            log.warn("[DART] ipo1.json error rcept_no={}: {}", rceptNo, e.getMessage());
            ipoScheduleCache.put(rceptNo, FAILED_SENTINEL);
        }
    }

    private void putDate(Map<String, String> map, String key, String yyyymmdd) {
        if (yyyymmdd != null && yyyymmdd.length() == 8) {
            map.put(key, yyyymmdd.substring(0, 4) + "-"
                       + yyyymmdd.substring(4, 6) + "-"
                       + yyyymmdd.substring(6, 8));
        }
    }

    /** 디버그용: 필터 전 원본 report_nm 목록 반환 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> debugRawFilings() {
        try {
            LocalDate end   = LocalDate.now();
            LocalDate start = end.minusDays(88);
            String url = "https://opendart.fss.or.kr/api/list.json"
                    + "?crtfc_key=" + apiKey
                    + "&bgn_de=" + start.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "&end_de="   + end.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "&pblntf_ty=C&pblntf_detail_ty=C001&page_count=20&page_no=1";
            Map<String, Object> raw = dartGet(url);
            if (raw == null) return Map.of("error", "null response");
            List<Map<String, Object>> list = (List<Map<String, Object>>) raw.get("list");
            List<Map<String, String>> samples = list == null ? List.of() :
                    list.stream().map(i -> {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put("corp_name", String.valueOf(i.get("corp_name")));
                        m.put("report_nm", String.valueOf(i.get("report_nm")));
                        m.put("rcept_dt",  String.valueOf(i.get("rcept_dt")));
                        m.put("rcept_no",  String.valueOf(i.get("rcept_no")));
                        return m;
                    }).collect(Collectors.toList());
            return Map.of(
                    "status", raw.getOrDefault("status", "?"),
                    "message", raw.getOrDefault("message", ""),
                    "total_count", raw.getOrDefault("total_count", "?"),
                    "samples", samples
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> getIpoFilingsCache() {
        if (ipoFilingsCache != null && LocalDate.now().equals(cacheDate)) return ipoFilingsCache;

        LocalDate end   = LocalDate.now();
        LocalDate start = end.minusDays(88); // DART 제한: corp_code 없이 최대 3개월(90일 미만)
        // pblntf_ty=C(발행공시) + pblntf_detail_ty=C001(증권신고서 지분증권)
        String baseUrl = "https://opendart.fss.or.kr/api/list.json"
                + "?crtfc_key=" + apiKey
                + "&bgn_de=" + start.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&end_de="   + end.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&pblntf_ty=C&pblntf_detail_ty=C001&page_count=100";

        List<Map<String, Object>> allItems = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            try {
                String url = baseUrl + "&page_no=" + pageNo;
                log.info("[DART] list page={}", pageNo);
                Map<String, Object> response = dartGet(url);
                if (response == null) break;
                String status = (String) response.get("status");
                if (!"000".equals(status)) {
                    log.info("[DART] list status={} msg={}", status, response.get("message"));
                    break;
                }
                List<Map<String, Object>> list = (List<Map<String, Object>>) response.get("list");
                if (list == null || list.isEmpty()) break;
                allItems.addAll(list);

                int totalCount = Integer.parseInt(response.getOrDefault("total_count", "0").toString());
                log.info("[DART] list page={} fetched={}/{}", pageNo, allItems.size(), totalCount);
                if (allItems.size() >= totalCount) break;
                pageNo++;
            } catch (Exception e) {
                log.warn("[DART] list fetch error page={}: {}", pageNo, e.getMessage());
                break;
            }
        }

        // API에서 이미 C001(증권신고서 지분증권)만 필터된 결과 → 추가 필터 불필요
        ipoFilingsCache = allItems.stream()
                .map(item -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("corpName",    (String) item.get("corp_name"));
                    m.put("receiptDate", (String) item.get("rcept_dt"));
                    m.put("rceptNo",     (String) item.get("rcept_no"));
                    return m;
                })
                .filter(m -> m.get("corpName") != null && m.get("rceptNo") != null)
                .collect(Collectors.toList());

        log.info("[DART] filings cached (증권신고서): {}", ipoFilingsCache.size());
        cacheDate = LocalDate.now();
        return ipoFilingsCache;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getRecentIpoList() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(3);
        String url = "https://opendart.fss.or.kr/api/list.json"
                + "?crtfc_key=" + apiKey
                + "&bgn_de=" + start.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&end_de=" + end.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&pblntf_detail_ty=C001&page_count=100";

        try {
            Map<String, Object> response = dartGet(url);
            if (response == null || !"000".equals(response.get("status"))) {
                String msg = response != null ? (String) response.get("message") : "응답 없음";
                throw new RuntimeException("DART API 오류: " + msg);
            }

            List<Map<String, Object>> list = (List<Map<String, Object>>) response.get("list");
            if (list == null) return List.of();

            return list.stream()
                    .map(item -> {
                        Map<String, String> r = new LinkedHashMap<>();
                        r.put("corpName",    (String) item.get("corp_name"));
                        r.put("receiptDate", (String) item.get("rcept_dt"));
                        return r;
                    })
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("DART API 호출 실패: " + e.getMessage(), e);
        }
    }
}

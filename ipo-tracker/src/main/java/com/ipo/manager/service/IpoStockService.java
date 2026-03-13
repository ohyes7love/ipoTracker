package com.ipo.manager.service;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * kokstock.com 공모주 일정 스크래퍼
 * 청약일정: /stock/ipo.asp
 * 상장일정: /stock/ipo_listing.asp
 * 상세:     /Ajax/popStockIPO.asp?I_IDX=xxx
 */
@Service
public class IpoStockService {

    private static final Logger log = LoggerFactory.getLogger(IpoStockService.class);

    private static final String BASE_URL    = "https://www.kokstock.com";
    private static final String IPO_URL     = BASE_URL + "/stock/ipo.asp";
    private static final String LISTING_URL = BASE_URL + "/stock/ipo_listing.asp";
    private static final String DETAIL_URL  = BASE_URL + "/Ajax/popStockIPO.asp?I_IDX=";

    private static final Pattern IDX_PATTERN     = Pattern.compile("popStockIPO\\((\\d+)");
    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)charset=([\\w-]+)");

    // 쿠키 스토어 공유: 메인 페이지 세션 쿠키를 AJAX 요청에서도 재사용
    private static final BasicCookieStore COOKIE_STORE = new BasicCookieStore();

    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setDefaultCookieStore(COOKIE_STORE)
            .build();

    // 캐시: 하루 1번만 fetch
    private List<Map<String, String>> cache = null;
    private LocalDate cacheDate = null;

    public List<Map<String, String>> getSchedulesByMonth(int year, int month) {
        List<Map<String, String>> all = getAllSchedules();
        String yearMonth = String.format("%04d-%02d", year, month);
        return all.stream()
                .filter(s -> {
                    String sub  = s.get("subscriptionStartDate");
                    String sub2 = s.get("subscriptionEndDate");
                    String lst  = s.get("listingDate");
                    return (sub  != null && sub.startsWith(yearMonth))
                        || (sub2 != null && sub2.startsWith(yearMonth))
                        || (lst  != null && lst.startsWith(yearMonth));
                })
                .toList();
    }

    public List<String> searchCorpName(String query) {
        return getAllSchedules().stream()
                .map(m -> m.get("corpName"))
                .filter(n -> n != null && n.contains(query))
                .distinct().limit(10).toList();
    }

    public List<Map<String, String>> searchSchedule(String query) {
        return getAllSchedules().stream()
                .filter(m -> m.get("corpName") != null && m.get("corpName").contains(query))
                .limit(10).toList();
    }

    /** kokstock 팝업 상세 정보: 세션 쿠키 확보 후 AJAX 호출 */
    public Map<String, Object> getIpoDetail(String idx) {
        // 메인 페이지를 먼저 fetch해서 세션 쿠키 확보
        getAllSchedules();

        String url = DETAIL_URL + idx;
        try {
            HttpGet get = new HttpGet(url);
            get.setHeader("Accept", "text/html,application/xhtml+xml,*/*;q=0.8");
            get.setHeader("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8");
            get.setHeader("Referer", IPO_URL);
            get.setHeader("X-Requested-With", "XMLHttpRequest");

            String[] detectedCharset = {"EUC-KR"};
            byte[] bytes = HTTP_CLIENT.execute(get, response -> {
                // Content-Type 헤더에서 charset 추출
                var ct = response.getFirstHeader("Content-Type");
                if (ct != null) {
                    Matcher m = CHARSET_PATTERN.matcher(ct.getValue());
                    if (m.find()) detectedCharset[0] = m.group(1);
                }
                log.info("[KOKSTOCK] detail HTTP {} charset={} idx={}", response.getCode(), detectedCharset[0], idx);
                return response.getEntity().getContent().readAllBytes();
            });

            if (bytes.length == 0) {
                log.warn("[KOKSTOCK] detail empty response idx={}", idx);
                return Map.of("error", "empty response");
            }

            String html = new String(bytes, detectedCharset[0]);
            log.debug("[KOKSTOCK] detail HTML[0:300]: {}", html.substring(0, Math.min(300, html.length())));
            Document doc = Jsoup.parse(html, url);

            return parseDetail(doc);

        } catch (Exception e) {
            log.warn("[KOKSTOCK] detail error idx={}: {}", idx, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    private Map<String, Object> parseDetail(Document doc) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 종목명: h4, h3, 또는 첫 번째 굵은 텍스트
        Element title = doc.selectFirst("h4, h3, .stock-name, .pop-title, strong");
        result.put("corpName", title != null ? title.text().trim() : "");

        // 시장 구분 배지 (코스닥/코스피)
        Element marketBadge = doc.selectFirst("span.badge");
        result.put("market", marketBadge != null ? marketBadge.text().trim() : "");

        // 메인 정보 테이블: 4열 구조 (label1, val1, label2, val2) 또는 th/td 혼합
        Map<String, String> info = new LinkedHashMap<>();
        Elements tables = doc.select("table");
        if (!tables.isEmpty()) {
            Element mainTable = tables.first();
            for (Element row : mainTable.select("tr")) {
                // th 태그가 있는 경우
                Elements thCells = row.select("th");
                if (!thCells.isEmpty()) {
                    Elements allCells = row.select("th, td");
                    for (int i = 0; i + 1 < allCells.size(); i++) {
                        if (allCells.get(i).tagName().equals("th")) {
                            String key = allCells.get(i).text().trim();
                            String val = allCells.get(i + 1).text().trim();
                            if (!key.isEmpty()) { info.put(key, val); i++; }
                        }
                    }
                } else {
                    // td만 있는 경우: 짝수 인덱스=라벨, 홀수 인덱스=값
                    Elements tds = row.select("td");
                    for (int i = 0; i + 1 < tds.size(); i += 2) {
                        String key = tds.get(i).text().trim();
                        String val = tds.get(i + 1).text().trim();
                        if (!key.isEmpty()) info.put(key, val);
                    }
                }
            }
        }
        result.put("info", info);

        // 비고 텍스트
        Element memo = doc.selectFirst("ul.ul-memo, .memo, ul");
        result.put("memo", memo != null ? memo.text().trim() : "");

        // 증권사 테이블 (두 번째 table)
        List<Map<String, String>> brokers = new ArrayList<>();
        if (tables.size() > 1) {
            for (int t = 1; t < tables.size(); t++) {
                Element tbl = tables.get(t);
                for (Element row : tbl.select("tr")) {
                    Elements tds = row.select("td");
                    if (tds.size() >= 3) {
                        Map<String, String> b = new LinkedHashMap<>();
                        b.put("name",       tds.get(0).text().trim());
                        b.put("allocation", tds.get(1).text().trim());
                        b.put("limit",      tds.get(2).text().trim());
                        if (!b.get("name").isEmpty()) brokers.add(b);
                    }
                }
                if (!brokers.isEmpty()) break;
            }
        }
        result.put("brokers", brokers);

        log.info("[KOKSTOCK] detail parsed: corpName={}, infoKeys={}, brokers={}",
                result.get("corpName"), info.keySet(), brokers.size());
        return result;
    }

    // ── 스케줄 캐시 ─────────────────────────────────────────

    /** 캐시 강제 갱신 (수동 동기화 버튼용) */
    public String refreshCache() {
        try {
            cache = fetch();
            cacheDate = LocalDate.now();
            return "ok:" + cache.size();
        } catch (Exception e) {
            log.warn("[KOKSTOCK] 갱신 실패: {}", e.getMessage());
            return "error:" + e.getMessage();
        }
    }

    private List<Map<String, String>> getAllSchedules() {
        if (cache != null && LocalDate.now().equals(cacheDate)) return cache;
        try {
            cache = fetch();
            cacheDate = LocalDate.now();
        } catch (Exception e) {
            log.warn("[KOKSTOCK] 스크래핑 실패: {}", e.getMessage());
            if (cache == null) cache = List.of();
        }
        return cache;
    }

    private List<Map<String, String>> fetch() throws Exception {
        Map<String, Map<String, String>> byName = new LinkedHashMap<>();

        // 현재 달 + 다음 달 모두 수집
        LocalDate today = LocalDate.now();
        LocalDate next  = today.plusMonths(1);

        fetchSubscription(byName, today.getYear(), today.getMonthValue());
        fetchSubscription(byName, next.getYear(),  next.getMonthValue());
        fetchListing(byName,      today.getYear(), today.getMonthValue());
        fetchListing(byName,      next.getYear(),  next.getMonthValue());

        List<Map<String, String>> result = new ArrayList<>(byName.values());
        log.info("[KOKSTOCK] total {} IPO schedules ({}월+{}월)", result.size(),
                today.getMonthValue(), next.getMonthValue());
        return result;
    }

    /** month-aware URL: ?page=1&pagesize=100&search_year=YYYY&search_month=MM */
    private String monthUrl(String base, int year, int month) {
        return base + "?page=1&pagesize=100&search_year=" + year
                + "&search_month=" + String.format("%02d", month);
    }

    private void fetchSubscription(Map<String, Map<String, String>> byName, int year, int month) throws Exception {
        String url = monthUrl(IPO_URL, year, month);
        log.info("[KOKSTOCK] fetching subscription {}", url);
        Document doc = fetchDoc(url);

        Elements rows = doc.select("tr.ipo-active-tr, tr.ipo-default-tr");
        log.info("[KOKSTOCK] subscription rows: {}", rows.size());

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 2) continue;

            String dateText = cells.get(0).text().trim();
            Element anchor = cells.get(1).selectFirst("a");
            String corpName = anchor != null ? anchor.ownText().trim() : cells.get(1).ownText().trim();
            if (corpName.isEmpty()) continue;

            Map<String, String> item = byName.computeIfAbsent(corpName, k -> {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("corpName", k);
                return m;
            });

            if (anchor != null) {
                Matcher m = IDX_PATTERN.matcher(anchor.attr("href"));
                if (m.find()) item.put("idx", m.group(1));
            }

            parseDateRange(dateText, item, "subscriptionStartDate", "subscriptionEndDate");
        }
    }

    private void fetchListing(Map<String, Map<String, String>> byName, int year, int month) throws Exception {
        String url = monthUrl(LISTING_URL, year, month);
        log.info("[KOKSTOCK] fetching listing {}", url);
        Document doc = fetchDoc(url);

        Elements rows = doc.select("tr.ipo-active-tr, tr.ipo-default-tr");
        log.info("[KOKSTOCK] listing rows: {}", rows.size());

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 2) continue;

            String dateText = cells.get(0).text().trim();
            Element anchor = cells.get(1).selectFirst("a");
            String corpName = anchor != null ? anchor.ownText().trim() : cells.get(1).ownText().trim();
            if (corpName.isEmpty()) continue;

            String listingDate = parseDate(dateText);
            if (listingDate == null) continue;

            Map<String, String> item = byName.computeIfAbsent(corpName, k -> {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("corpName", k);
                return m;
            });
            item.put("listingDate", listingDate);

            if (anchor != null && !item.containsKey("idx")) {
                Matcher m = IDX_PATTERN.matcher(anchor.attr("href"));
                if (m.find()) item.put("idx", m.group(1));
            }
        }
    }

    // ── HTTP 유틸 ────────────────────────────────────────────

    private Document fetchDoc(String url) throws Exception {
        HttpGet get = new HttpGet(url);
        get.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        get.setHeader("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8");
        get.setHeader("Referer", BASE_URL + "/");

        byte[] bytes = HTTP_CLIENT.execute(get, response -> {
            log.info("[KOKSTOCK] HTTP {} {}", response.getCode(), url);
            return response.getEntity().getContent().readAllBytes();
        });
        // Jsoup이 meta charset 태그를 자동 감지 (EUC-KR 포함)
        return Jsoup.parse(new ByteArrayInputStream(bytes), null, url);
    }

    // ── 날짜 파싱 ─────────────────────────────────────────────

    private void parseDateRange(String text, Map<String, String> item, String startKey, String endKey) {
        if (text == null || text.isBlank()) return;
        String[] parts = text.split("~");
        String start = parseDate(parts[0].trim());
        if (start != null) item.put(startKey, start);
        if (parts.length > 1) {
            String end = parseDate(parts[1].trim());
            if (end != null) item.put(endKey, end);
            else if (start != null) item.put(endKey, start);
        } else if (start != null) {
            item.put(endKey, start);
        }
    }

    private static final int CURRENT_YEAR = LocalDate.now().getYear();

    private String parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        text = text.trim().replaceAll("[^0-9/.]", "");
        try {
            if (text.matches("\\d{4}[./]\\d{2}[./]\\d{2}"))
                return text.replaceAll("[./]", "-").substring(0, 10);
            if (text.matches("\\d{2}[./]\\d{2}")) {
                String[] p = text.split("[./]");
                return CURRENT_YEAR + "-" + p[0] + "-" + p[1];
            }
            if (text.matches("\\d{1,2}[./]\\d{1,2}")) {
                String[] p = text.split("[./]");
                return String.format("%d-%02d-%02d", CURRENT_YEAR, Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            }
            if (text.matches("\\d{8}"))
                return text.substring(0,4) + "-" + text.substring(4,6) + "-" + text.substring(6,8);
        } catch (Exception ignored) {}
        return null;
    }
}

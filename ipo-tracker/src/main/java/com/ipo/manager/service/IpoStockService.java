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
 * IpoStockService - kokstock.com 공모주 일정 스크래퍼 (서버 사이드)
 *
 * <p>kokstock.com의 공모주 청약/상장 일정을 HTTP 스크래핑으로 수집합니다.
 * 한글 사이트 특성상 EUC-KR 인코딩을 사용하며, Jsoup의 meta charset 자동감지로 처리합니다.</p>
 *
 * <h3>스크래핑 대상 URL</h3>
 * <ul>
 *   <li>청약일정: /stock/ipo.asp?search_year=YYYY&search_month=MM</li>
 *   <li>상장일정: /stock/ipo_listing.asp?search_year=YYYY&search_month=MM</li>
 *   <li>종목상세: /Ajax/popStockIPO.asp?I_IDX={idx} (AJAX 팝업)</li>
 * </ul>
 *
 * <h3>캐싱 전략</h3>
 * <p>월별로 Map&lt;String, List&gt; 캐시를 유지합니다.
 * 캘린더에서 이전/다음 달 이동 시 해당 월 데이터가 없으면 그때 fetch합니다.
 * 동기화 버튼을 누르면 현재달+다음달 캐시를 invalidate하고 재로드합니다.</p>
 *
 * <h3>세션 쿠키 처리</h3>
 * <p>AJAX detail 호출 전 메인 페이지를 먼저 fetch해 세션 쿠키를 확보합니다.
 * BasicCookieStore를 공유하여 같은 HttpClient 인스턴스가 쿠키를 재사용합니다.</p>
 */
@Service
public class IpoStockService {

    private static final Logger log = LoggerFactory.getLogger(IpoStockService.class);

    private static final String BASE_URL    = "https://www.kokstock.com";
    private static final String IPO_URL     = BASE_URL + "/stock/ipo.asp";
    private static final String LISTING_URL = BASE_URL + "/stock/ipo_listing.asp";
    /** 종목 상세 팝업 AJAX URL (I_IDX 파라미터로 종목 구분) */
    private static final String DETAIL_URL  = BASE_URL + "/Ajax/popStockIPO.asp?I_IDX=";

    /** popStockIPO(611,'메쥬') 형식에서 idx 추출 */
    private static final Pattern IDX_PATTERN     = Pattern.compile("popStockIPO\\((\\d+)");
    /** Content-Type 헤더에서 charset 추출 */
    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)charset=([\\w-]+)");

    // 메인 페이지 세션 쿠키를 AJAX detail 요청에서도 재사용하기 위해 공유
    private static final BasicCookieStore COOKIE_STORE = new BasicCookieStore();

    /** 싱글턴 HttpClient: User-Agent 설정 및 쿠키 공유 */
    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setDefaultCookieStore(COOKIE_STORE)
            .build();

    /**
     * 월별 캐시: "2026-03" → 해당 월 공모주 일정 목록
     * <p>요청된 월이 캐시에 없으면 getSchedulesByMonth() 호출 시 fetch합니다.</p>
     */
    private final Map<String, List<Map<String, String>>> monthCache = new LinkedHashMap<>();

    public List<Map<String, String>> getSchedulesByMonth(int year, int month) {
        String ym = String.format("%04d-%02d", year, month);
        if (!monthCache.containsKey(ym)) {
            log.info("[KOKSTOCK] fetch 요청: {}", ym);
            try {
                Map<String, Map<String, String>> byName = new LinkedHashMap<>();
                fetchSubscription(byName, year, month);
                fetchListing(byName, year, month);
                monthCache.put(ym, new ArrayList<>(byName.values()));
                log.info("[KOKSTOCK] fetch 완료: {} → {}건", ym, monthCache.get(ym).size());
            } catch (Exception e) {
                log.warn("[KOKSTOCK] fetch 실패: {} {}", ym, e.getMessage());
                monthCache.put(ym, List.of());
            }
        }
        return monthCache.get(ym).stream()
                .filter(s -> {
                    String sub  = s.get("subscriptionStartDate");
                    String sub2 = s.get("subscriptionEndDate");
                    String lst  = s.get("listingDate");
                    return (sub  != null && sub.startsWith(ym))
                        || (sub2 != null && sub2.startsWith(ym))
                        || (lst  != null && lst.startsWith(ym));
                })
                .toList();
    }

    public List<String> searchCorpName(String query) {
        return getCurrentAndNextMonthSchedules().stream()
                .map(m -> m.get("corpName"))
                .filter(n -> n != null && n.contains(query))
                .distinct().limit(10).toList();
    }

    public List<Map<String, String>> searchSchedule(String query) {
        return getCurrentAndNextMonthSchedules().stream()
                .filter(m -> m.get("corpName") != null && m.get("corpName").contains(query))
                .limit(10).toList();
    }

    /** kokstock 팝업 상세 정보: 세션 쿠키 확보 후 AJAX 호출 */
    public Map<String, Object> getIpoDetail(String idx) {
        // 메인 페이지를 먼저 fetch해서 세션 쿠키 확보
        getMonthRaw(LocalDate.now().getYear(), LocalDate.now().getMonthValue());

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

    /** 캐시 강제 갱신: 현재달+다음달 재로드 */
    public String refreshCache() {
        LocalDate today = LocalDate.now();
        LocalDate next  = today.plusMonths(1);
        String ym1 = String.format("%04d-%02d", today.getYear(), today.getMonthValue());
        String ym2 = String.format("%04d-%02d", next.getYear(),  next.getMonthValue());
        monthCache.remove(ym1);
        monthCache.remove(ym2);
        // 다시 로드
        getSchedulesByMonth(today.getYear(), today.getMonthValue());
        getSchedulesByMonth(next.getYear(),  next.getMonthValue());
        int count = monthCache.getOrDefault(ym1, List.of()).size()
                  + monthCache.getOrDefault(ym2, List.of()).size();
        return "ok:" + count;
    }

    /**
     * 특정 월의 모든 공모주에 대해 증권사별 참여 건수를 집계합니다.
     * 각 종목의 detail을 fetch하므로 건수가 많으면 시간이 걸릴 수 있습니다.
     */
    public List<Map<String, Object>> getBrokerStats(int year, int month) {
        List<Map<String, String>> schedules = getSchedulesByMonth(year, month);

        // broker → { count, ipos }
        Map<String, Integer> countMap = new LinkedHashMap<>();
        Map<String, List<String>> iposMap = new LinkedHashMap<>();

        for (Map<String, String> s : schedules) {
            String idx = s.get("idx");
            String corp = s.get("corpName");
            if (idx == null || corp == null) continue;

            try {
                Map<String, Object> detail = getIpoDetail(idx);
                @SuppressWarnings("unchecked")
                List<Map<String, String>> brokers = (List<Map<String, String>>) detail.get("brokers");
                if (brokers == null) continue;
                for (Map<String, String> b : brokers) {
                    String name = b.get("name");
                    if (name == null || name.isBlank()) continue;
                    countMap.merge(name, 1, Integer::sum);
                    iposMap.computeIfAbsent(name, k -> new ArrayList<>()).add(corp);
                }
            } catch (Exception e) {
                log.warn("[KOKSTOCK] brokerStats detail error: {} {}", corp, e.getMessage());
            }
        }

        return countMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("brokerName", e.getKey());
                    row.put("count", e.getValue());
                    row.put("ipos", iposMap.getOrDefault(e.getKey(), List.of()));
                    return row;
                })
                .toList();
    }

    /** 검색용: 현재달 + 다음달 전체 목록 */
    private List<Map<String, String>> getCurrentAndNextMonthSchedules() {
        LocalDate today = LocalDate.now();
        LocalDate next  = today.plusMonths(1);
        List<Map<String, String>> result = new ArrayList<>();
        result.addAll(getMonthRaw(today.getYear(), today.getMonthValue()));
        result.addAll(getMonthRaw(next.getYear(),  next.getMonthValue()));
        return result;
    }

    private List<Map<String, String>> getMonthRaw(int year, int month) {
        String ym = String.format("%04d-%02d", year, month);
        if (!monthCache.containsKey(ym)) {
            getSchedulesByMonth(year, month); // 캐시 채움
        }
        return monthCache.getOrDefault(ym, List.of());
    }

    /** month-aware URL: ?page=1&pagesize=100&search_year=YYYY&search_month=MM */
    private String monthUrl(String base, int year, int month) {
        return base + "?page=1&pagesize=100&search_year=" + year
                + "&search_month=" + String.format("%02d", month);
    }

    /**
     * kokstock 청약일정 페이지 스크래핑
     * <p>tr.ipo-active-tr / tr.ipo-default-tr 행에서 종목명, 청약기간, idx를 추출합니다.
     * 종목명은 anchor.ownText()로 읽어 배지(코스닥/코스피) 텍스트를 제거합니다.</p>
     */
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

    /**
     * kokstock 상장일정 페이지 스크래핑
     * <p>청약일정에서 이미 등록된 종목이면 listingDate만 추가하고,
     * 청약일 없이 상장일만 있는 종목은 새로 추가합니다.</p>
     */
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

    /**
     * URL을 fetch하여 Jsoup Document로 반환합니다.
     * <p>응답 바이트를 먼저 읽은 뒤 Jsoup.parse(InputStream, null, url)로 파싱합니다.
     * charset=null 이면 Jsoup이 HTML &lt;meta charset&gt; 태그를 자동감지하므로
     * EUC-KR 사이트도 별도 처리 없이 올바르게 파싱됩니다.</p>
     */
    private Document fetchDoc(String url) throws Exception {
        HttpGet get = new HttpGet(url);
        get.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        get.setHeader("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8");
        get.setHeader("Referer", BASE_URL + "/");

        byte[] bytes = HTTP_CLIENT.execute(get, response -> {
            log.info("[KOKSTOCK] HTTP {} {}", response.getCode(), url);
            return response.getEntity().getContent().readAllBytes();
        });
        // charset=null → Jsoup이 <meta charset="euc-kr"> 를 읽어 자동 디코딩
        return Jsoup.parse(new ByteArrayInputStream(bytes), null, url);
    }

    // ── 날짜 파싱 ─────────────────────────────────────────────

    /**
     * "MM/DD ~ MM/DD" 또는 "YYYY/MM/DD" 형식의 날짜 범위 문자열을 파싱합니다.
     *
     * @param text     원본 날짜 문자열
     * @param item     결과를 저장할 Map
     * @param startKey 시작일 키 (예: "subscriptionStartDate")
     * @param endKey   종료일 키 (예: "subscriptionEndDate")
     */
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

    /**
     * 날짜 문자열을 "YYYY-MM-DD" 형식으로 파싱합니다.
     * <p>지원 형식:
     * <ul>
     *   <li>YYYY/MM/DD 또는 YYYY.MM.DD → YYYY-MM-DD</li>
     *   <li>MM/DD 또는 MM.DD           → 현재연도-MM-DD</li>
     *   <li>YYYYMMDD (8자리)           → YYYY-MM-DD</li>
     * </ul>
     * </p>
     */
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

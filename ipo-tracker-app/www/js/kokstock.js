/**
 * kokstock.js - kokstock.com 공모주 일정 스크래퍼 (클라이언트 사이드)
 *
 * Capacitor 앱(Android)에서 직접 kokstock.com을 스크래핑합니다.
 * 서버 없이 독립 실행되며 IndexedDB(db.js)와 함께 오프라인 동작합니다.
 *
 * ■ EUC-KR 처리 방법
 *   - CapacitorHttp.request({ responseType: 'arraybuffer' }) 로 raw bytes 수신
 *   - Android에서는 ArrayBuffer가 base64 문자열로 전달됨 → atob() 로 디코딩
 *   - TextDecoder('euc-kr') 로 한글 복원
 *   - 웹 브라우저 fallback: fetch() → arrayBuffer() → TextDecoder('euc-kr')
 *
 * ■ 공개 함수 목록
 *   - getKokstockSchedulesByMonth(year, month) : 해당 월 공모주 일정 반환
 *   - searchKokstockSchedule(query)            : 현재달+다음달에서 종목명 검색
 *   - getKokstockDetail(idx)                   : 종목 상세 (공모가/증권사/비고 등)
 *   - refreshKokstockCache()                   : 캐시 초기화 후 현재달+다음달 재로드
 *
 * ■ 캐싱 전략
 *   - _monthCache: { 'YYYY-MM': [...] } 형태로 월별 관리
 *   - 요청한 달이 캐시에 없으면 그때 fetch (lazy loading)
 */

const KOKSTOCK_BASE = 'https://www.kokstock.com';

// ── HTML fetch: EUC-KR 디코딩 ─────────────────────────────

/**
 * URL을 fetch하여 EUC-KR 디코딩된 HTML 문자열을 반환합니다.
 *
 * Capacitor 환경에서는 CapacitorHttp 플러그인을 통해
 * CORS 우회 + arraybuffer 응답을 받습니다.
 * 브라우저 환경에서는 일반 fetch()로 fallback합니다.
 */
async function _fetchHtml(url) {
    const headers = {
        'Accept': 'text/html,application/xhtml+xml,*/*;q=0.8',
        'Accept-Language': 'ko-KR,ko;q=0.9',
        'Referer': KOKSTOCK_BASE + '/'
    };

    // Capacitor 네이티브 HTTP → arraybuffer → TextDecoder('euc-kr')
    const Cap = window.Capacitor;
    if (Cap && Cap.Plugins && Cap.Plugins.CapacitorHttp) {
        const resp = await Cap.Plugins.CapacitorHttp.request({
            method: 'GET', url, headers, responseType: 'arraybuffer'
        });
        if (resp && resp.data != null) {
            try {
                let bytes;
                if (typeof resp.data === 'string') {
                    // Android: base64 인코딩된 문자열
                    const b = atob(resp.data);
                    bytes = new Uint8Array(b.length);
                    for (let i = 0; i < b.length; i++) bytes[i] = b.charCodeAt(i);
                } else {
                    bytes = new Uint8Array(resp.data);
                }
                return new TextDecoder('euc-kr').decode(bytes);
            } catch (e) {
                console.warn('[KOKSTOCK] EUC-KR decode 실패, resp.data 사용:', e.message);
                return String(resp.data);
            }
        }
    }

    // 웹 브라우저 fallback
    const res = await fetch(url, { headers });
    const buf = await res.arrayBuffer();
    try { return new TextDecoder('euc-kr').decode(buf); } catch (_) { return ''; }
}

// ── 날짜 파싱 ──────────────────────────────────────────────

/**
 * kokstock 날짜 문자열을 "YYYY-MM-DD" 형식으로 변환합니다.
 * 지원 형식: YYYY/MM/DD, MM/DD, M/D, YYYYMMDD(8자리)
 */
function _parseKokDate(text) {
    if (!text) return null;
    text = text.trim().replace(/[^0-9/.]/g, '');
    const y = new Date().getFullYear();
    if (/^\d{4}[./]\d{2}[./]\d{2}$/.test(text)) return text.replace(/[./]/g, '-').substring(0, 10);
    if (/^\d{2}[./]\d{2}$/.test(text)) { const [m,d] = text.split(/[./]/); return `${y}-${m}-${d}`; }
    if (/^\d{1,2}[./]\d{1,2}$/.test(text)) { const [m,d] = text.split(/[./]/); return `${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}`; }
    if (/^\d{8}$/.test(text)) return `${text.slice(0,4)}-${text.slice(4,6)}-${text.slice(6,8)}`;
    return null;
}

function _parseDateRange(text) {
    if (!text) return [null, null];
    const parts = text.split('~');
    const start = _parseKokDate(parts[0]);
    const end   = parts.length > 1 ? _parseKokDate(parts[1]) : start;
    return [start, end || start];
}

function _monthUrl(base, year, month) {
    return `${base}?page=1&pagesize=100&search_year=${year}&search_month=${String(month).padStart(2,'0')}`;
}

function _corpNameFromAnchor(anchor) {
    if (!anchor) return '';
    let name = '';
    for (const node of anchor.childNodes) {
        if (node.nodeType === Node.TEXT_NODE) name += node.textContent;
    }
    return name.trim();
}

// ── 월별 일정 fetch ────────────────────────────────────────

/**
 * 특정 연월의 청약일정 + 상장일정을 모두 fetch합니다.
 *
 * 청약일정 (/stock/ipo.asp) 과 상장일정 (/stock/ipo_listing.asp) 을
 * 종목명(corpName)을 키로 하여 byName 객체에 합칩니다.
 * 청약일이 있는 종목에 상장일을 추가하고,
 * 상장일만 있는 종목은 별도로 추가합니다.
 *
 * @param {number} year  연도
 * @param {number} month 월 (1~12)
 * @returns {Array} 공모주 일정 배열 [{ corpName, subscriptionStartDate, subscriptionEndDate, listingDate, idx }]
 */
async function _fetchMonth(year, month) {
    const byName = {};

    // 청약 일정
    try {
        const html = await _fetchHtml(_monthUrl(`${KOKSTOCK_BASE}/stock/ipo.asp`, year, month));
        const doc  = new DOMParser().parseFromString(html, 'text/html');
        for (const row of doc.querySelectorAll('tr.ipo-active-tr, tr.ipo-default-tr')) {
            const cells = row.querySelectorAll('td');
            if (cells.length < 2) continue;
            const anchor   = cells[1].querySelector('a');
            const corpName = _corpNameFromAnchor(anchor);
            if (!corpName) continue;
            const [sub, subEnd] = _parseDateRange(cells[0].textContent.trim());
            const idxM = (anchor?.getAttribute('href') || '').match(/popStockIPO\((\d+)/);
            byName[corpName] = { corpName, subscriptionStartDate: sub, subscriptionEndDate: subEnd, idx: idxM?.[1] || null };
        }
        console.log('[KOKSTOCK] 청약', year, month, '→', Object.keys(byName).length, '건');
    } catch (e) { console.warn('[KOKSTOCK] sub error', year, month, e.message); }

    // 상장 일정
    try {
        const html = await _fetchHtml(_monthUrl(`${KOKSTOCK_BASE}/stock/ipo_listing.asp`, year, month));
        const doc  = new DOMParser().parseFromString(html, 'text/html');
        for (const row of doc.querySelectorAll('tr.ipo-active-tr, tr.ipo-default-tr')) {
            const cells = row.querySelectorAll('td');
            if (cells.length < 2) continue;
            const anchor      = cells[1].querySelector('a');
            const corpName    = _corpNameFromAnchor(anchor);
            if (!corpName) continue;
            const listingDate = _parseKokDate(cells[0].textContent.trim());
            if (!listingDate) continue;
            const idxM = (anchor?.getAttribute('href') || '').match(/popStockIPO\((\d+)/);
            if (byName[corpName]) {
                byName[corpName].listingDate = listingDate;
            } else {
                byName[corpName] = { corpName, listingDate, idx: idxM?.[1] || null };
            }
        }
    } catch (e) { console.warn('[KOKSTOCK] listing error', year, month, e.message); }

    return Object.values(byName);
}

// ── 캐시: 월별로 개별 관리 ────────────────────────────────
// { '2026-03': [...], '2026-04': [...], ... }
// 캘린더에서 이전/다음 달 이동 시 해당 달 캐시가 없으면 lazy fetch합니다.
let _monthCache = {};

/**
 * 월별 캐시에서 데이터를 가져옵니다. 없으면 fetch 후 캐시에 저장합니다.
 */
async function _getMonthCached(year, month) {
    const ym = `${year}-${String(month).padStart(2,'0')}`;
    if (!_monthCache[ym]) {
        console.log('[KOKSTOCK] fetch 요청:', ym);
        _monthCache[ym] = await _fetchMonth(year, month);
        console.log('[KOKSTOCK] fetch 완료:', ym, _monthCache[ym].length, '건');
    }
    return _monthCache[ym];
}

async function getKokstockSchedulesByMonth(year, month) {
    const ym   = `${year}-${String(month).padStart(2,'0')}`;
    const data = await _getMonthCached(year, month);
    return data.filter(s =>
        (s.subscriptionStartDate && s.subscriptionStartDate.startsWith(ym)) ||
        (s.subscriptionEndDate   && s.subscriptionEndDate.startsWith(ym))   ||
        (s.listingDate           && s.listingDate.startsWith(ym))
    );
}

async function searchKokstockSchedule(query) {
    if (!query) return [];
    // 검색은 현재달 + 다음달 대상
    const today = new Date();
    const y = today.getFullYear(), m = today.getMonth() + 1;
    const ny = m === 12 ? y + 1 : y, nm = m === 12 ? 1 : m + 1;
    const [curr, next] = await Promise.all([_getMonthCached(y, m), _getMonthCached(ny, nm)]);
    const merged = {};
    [...curr, ...next].forEach(s => { merged[s.corpName] = s; });
    return Object.values(merged).filter(s => s.corpName && s.corpName.includes(query)).slice(0, 10);
}

// ── 상세 정보 ─────────────────────────────────────────────

/**
 * 종목 상세 정보를 가져옵니다 (kokstock AJAX 팝업 파싱).
 *
 * 반환 구조:
 * <pre>
 * {
 *   info: {
 *     '청약일정': '03/16 ~ 03/17',
 *     '환불일':   '03/20',
 *     '확정공모가': '12,000',
 *     '경쟁률':   '1,234.56 : 1',
 *     ...
 *   },
 *   market:  '코스닥',         // 시장 구분 배지
 *   brokers: [                // 참여 증권사 목록
 *     { name: '미래에셋', allocation: '20%', limit: '10주' },
 *     ...
 *   ],
 *   memo: '...'               // 비고
 * }
 * </pre>
 *
 * @param {string} idx kokstock 종목 고유 번호 (popStockIPO(idx,...) 에서 추출)
 */
async function getKokstockDetail(idx) {
    const url = `${KOKSTOCK_BASE}/Ajax/popStockIPO.asp?I_IDX=${idx}`;
    try {
        const html = await _fetchHtml(url);
        const doc  = new DOMParser().parseFromString(html, 'text/html');
        const info = {};
        const tables = doc.querySelectorAll('table');

        if (tables.length > 0) {
            tables[0].querySelectorAll('tr').forEach(row => {
                const ths = row.querySelectorAll('th');
                if (ths.length > 0) {
                    const allCells = row.querySelectorAll('th, td');
                    for (let i = 0; i + 1 < allCells.length; i++) {
                        if (allCells[i].tagName === 'TH') {
                            info[allCells[i].textContent.trim()] = allCells[i+1].textContent.trim();
                            i++;
                        }
                    }
                } else {
                    const tds = row.querySelectorAll('td');
                    for (let i = 0; i + 1 < tds.length; i += 2)
                        info[tds[i].textContent.trim()] = tds[i+1].textContent.trim();
                }
            });
        }

        const brokers = [];
        for (let t = 1; t < tables.length; t++) {
            tables[t].querySelectorAll('tr').forEach(row => {
                const tds = row.querySelectorAll('td');
                if (tds.length >= 3) brokers.push({
                    name: tds[0].textContent.trim(),
                    allocation: tds[1].textContent.trim(),
                    limit: tds[2].textContent.trim()
                });
            });
            if (brokers.length > 0) break;
        }

        // 비고 추출: info 객체에서 먼저, 없으면 DOM에서 탐색
        let memo = info['비고'] || '';
        if (!memo) {
            const memoEl = doc.querySelector('ul.ul-memo') || doc.querySelector('.memo');
            if (memoEl) memo = memoEl.textContent.trim();
        }

        return { info, market: doc.querySelector('span.badge')?.textContent.trim() || '', brokers, memo };
    } catch (e) {
        console.warn('[KOKSTOCK] detail error', idx, e.message);
        return { info: {}, market: '', brokers: [], memo: '' };
    }
}

async function refreshKokstockCache() {
    _monthCache = {};
    // 현재달 + 다음달 미리 로드
    const today = new Date();
    const y = today.getFullYear(), m = today.getMonth() + 1;
    const ny = m === 12 ? y + 1 : y, nm = m === 12 ? 1 : m + 1;
    await Promise.all([_getMonthCached(y, m), _getMonthCached(ny, nm)]);
}

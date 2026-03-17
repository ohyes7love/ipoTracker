/**
 * db.js - IndexedDB 기반 로컬 데이터 저장소
 *
 * Spring Boot + MySQL의 역할을 Capacitor 앱 내 IndexedDB로 대체합니다.
 * 서버 없이 독립 실행되며 앱 삭제 전까지 데이터가 영구 보존됩니다.
 *
 * ■ Object Store 구성
 *   - ipo_subscription : 공모주 청약 내역 (keyPath: id, autoIncrement)
 *                        인덱스: year (연도별 조회)
 *   - ipo_stock        : 자동완성용 종목 정보 (keyPath: stockName)
 *                        청약 내역 저장 시 자동 upsert
 *   - broker_fee       : 증권사별 청약수수료 (keyPath: brokerName)
 *                        앱 첫 실행 시 DEFAULT_BROKER_FEES 로 초기화
 *
 * ■ 수익 계산 공식
 *   profit = (매도가 - 공모가) × 배정수량 - 세금/수수료 - 청약수수료
 *
 * ■ 주요 함수
 *   - getIposByYear(year)       : 연도별 청약 내역 (수익 계산 포함)
 *   - createIpo(dto)            : 청약 내역 등록
 *   - updateIpo(id, dto)        : 청약 내역 수정
 *   - deleteIpo(id)             : 청약 내역 삭제
 *   - getMonthlySummary(year)   : 월별 수익 집계
 *   - getAllBrokerFees()        : 증권사 수수료 목록
 *   - updateBrokerFee(name,fee) : 증권사 수수료 수정
 */

const DB_NAME    = 'ipoTracker';
const DB_VERSION = 2;

const DEFAULT_BROKER_FEES = [
    { brokerName: '미래에셋', feeAmount: 2000 },
    { brokerName: '한국투자', feeAmount: 2000 },
    { brokerName: '삼성',     feeAmount: 2000 },
    { brokerName: 'NH',       feeAmount: 2000 },
    { brokerName: '대신',     feeAmount: 2000 },
    { brokerName: '하나',     feeAmount: 2000 },
    { brokerName: '하이',     feeAmount: 2000 },
    { brokerName: 'DB',       feeAmount: 2000 },
    { brokerName: '기흥',     feeAmount: 2000 },
    { brokerName: 'KB',       feeAmount: 1500 },
    { brokerName: '신한',     feeAmount: 2000 },
    { brokerName: '유진',     feeAmount: 2000 },
    { brokerName: '교보',     feeAmount: 2000 },
    { brokerName: '유안타',   feeAmount: 3000 },
    { brokerName: '신영',     feeAmount: 2000 },
    { brokerName: 'IBK',      feeAmount: 1500 },
    { brokerName: '한화',     feeAmount: 2000 },
];

// ── DB 연결 ──────────────────────────────────────────────
let _db = null;

/**
 * IndexedDB를 열고 연결을 반환합니다 (싱글턴).
 * 최초 호출 시 Object Store를 생성하고 초기 인덱스를 설정합니다.
 */
function openDB() {
    if (_db) return Promise.resolve(_db);
    return new Promise((resolve, reject) => {
        const req = indexedDB.open(DB_NAME, DB_VERSION);
        req.onupgradeneeded = e => {
            const db = e.target.result;
            if (!db.objectStoreNames.contains('ipo_subscription')) {
                const s = db.createObjectStore('ipo_subscription', { keyPath: 'id', autoIncrement: true });
                s.createIndex('year', 'year', { unique: false });
            }
            if (!db.objectStoreNames.contains('ipo_stock')) {
                db.createObjectStore('ipo_stock', { keyPath: 'stockName' });
            }
            if (!db.objectStoreNames.contains('broker_fee')) {
                db.createObjectStore('broker_fee', { keyPath: 'brokerName' });
            }
            // v2: 청약 체크리스트 (계좌별 신청/배정/환불 상태 관리)
            if (!db.objectStoreNames.contains('ipo_checklist')) {
                db.createObjectStore('ipo_checklist', { keyPath: 'corpName' });
            }
        };
        req.onsuccess = e => { _db = e.target.result; resolve(_db); };
        req.onerror   = e => reject(e.target.error);
    });
}

/** IndexedDB 트랜잭션 헬퍼: store에 대해 mode 트랜잭션을 열고 fn을 실행합니다. */
function tx(store, mode, fn) {
    return openDB().then(db => new Promise((resolve, reject) => {
        const t = db.transaction(store, mode);
        t.onerror = e => reject(e.target.error);
        resolve(fn(t.objectStore(store)));
    }));
}

/** IDBRequest를 Promise로 래핑하는 유틸 함수 */
function req2p(r) {
    return new Promise((res, rej) => {
        r.onsuccess = e => res(e.target.result);
        r.onerror   = e => rej(e.target.error);
    });
}

// ── 초기 데이터 (broker_fee 기본값) ─────────────────────
async function initDefaultData() {
    const db = await openDB();
    const t = db.transaction('broker_fee', 'readwrite');
    const s = t.objectStore('broker_fee');
    for (const fee of DEFAULT_BROKER_FEES) {
        const existing = await req2p(s.get(fee.brokerName));
        if (!existing) s.put(fee);
    }
    return new Promise(res => { t.oncomplete = res; t.onerror = res; });
}

// ── 수익 계산 ────────────────────────────────────────────
/**
 * 공모주 1건의 수익을 계산합니다.
 * 매도가(soldPrice)가 없으면 null 반환 (미매도 상태).
 * 수익 = (매도가 - 공모가) × 배정수량 - 세금/수수료 - 청약수수료
 */
function calcProfit(item) {
    if (item.soldPrice == null || item.soldPrice === '') return null;
    return (Number(item.soldPrice) - Number(item.offeringPrice)) * Number(item.soldQty || 0)
         - Number(item.taxAndFee || 0)
         - Number(item.subscriptionFee || 0);
}

function calcProfitRate(item) {
    const profit = calcProfit(item);
    if (profit == null || !item.offeringPrice || !item.soldQty) return null;
    const cost = Number(item.offeringPrice) * Number(item.soldQty);
    if (!cost) return null;
    return (profit / cost) * 100;
}

function enrich(item) {
    return { ...item, profit: calcProfit(item), profitRate: calcProfitRate(item) };
}

// ── IPO Subscription CRUD ────────────────────────────────
async function getIposByYear(year) {
    const db = await openDB();
    return new Promise((res, rej) => {
        const t = db.transaction('ipo_subscription', 'readonly');
        const r = t.objectStore('ipo_subscription').index('year').getAll(parseInt(year));
        r.onsuccess = e => res(e.target.result.map(enrich));
        r.onerror   = e => rej(e.target.error);
    });
}

async function createIpo(dto) {
    const item = { ...dto, id: undefined };
    delete item.id;
    const db  = await openDB();
    const id  = await req2p(db.transaction('ipo_subscription', 'readwrite').objectStore('ipo_subscription').add(item));
    await upsertIpoStock({ stockName: dto.stockName, offeringPrice: dto.offeringPrice });
    return enrich({ ...item, id });
}

async function updateIpo(id, dto) {
    const item = { ...dto, id: parseInt(id) };
    const db   = await openDB();
    await req2p(db.transaction('ipo_subscription', 'readwrite').objectStore('ipo_subscription').put(item));
    await upsertIpoStock({ stockName: dto.stockName, offeringPrice: dto.offeringPrice });
    return enrich(item);
}

async function deleteIpo(id) {
    const db = await openDB();
    return req2p(db.transaction('ipo_subscription', 'readwrite').objectStore('ipo_subscription').delete(parseInt(id)));
}

async function getMonthlySummary(year) {
    const items = await getIposByYear(year);
    const map = {};
    for (let m = 1; m <= 12; m++) map[m] = 0;
    items.forEach(item => {
        if (item.profit != null && item.soldDate) {
            const m = parseInt(item.soldDate.substring(5, 7));
            if (!isNaN(m)) map[m] += item.profit;
        }
    });
    return Object.entries(map)
        .filter(([, v]) => v !== 0)
        .map(([month, totalProfit]) => ({ month: parseInt(month), totalProfit }));
}

async function getAllIpos() {
    const db = await openDB();
    return req2p(db.transaction('ipo_subscription', 'readonly').objectStore('ipo_subscription').getAll());
}

// ── IPO Stock (자동완성용) ────────────────────────────────
async function getAllIpoStocks() {
    const db = await openDB();
    return req2p(db.transaction('ipo_stock', 'readonly').objectStore('ipo_stock').getAll());
}

async function upsertIpoStock(stock) {
    if (!stock.stockName) return;
    const db = await openDB();
    return req2p(db.transaction('ipo_stock', 'readwrite').objectStore('ipo_stock').put(stock));
}

// ── Broker Fee ───────────────────────────────────────────
async function getAllBrokerFees() {
    const db = await openDB();
    return req2p(db.transaction('broker_fee', 'readonly').objectStore('broker_fee').getAll());
}

async function updateBrokerFee(brokerName, feeAmount) {
    const db = await openDB();
    return req2p(db.transaction('broker_fee', 'readwrite').objectStore('broker_fee').put({ brokerName, feeAmount }));
}

// ── IPO Checklist (계좌별 청약 체크리스트) ───────────────
/**
 * 체크리스트 레코드 구조:
 * {
 *   corpName: "메쥬",                           // keyPath (종목명 = 고유키)
 *   kokIdx: "611",                              // kokstock 상세 팝업 IDX
 *   subscriptionStartDate: "2026-03-16",
 *   subscriptionEndDate:   "2026-03-17",
 *   listingDate:           "2026-03-25",
 *   offeringPrice:         12000,
 *   accounts: {
 *     "경록": { applied: false, qty: 0, refunded: false, registered: false },
 *     "지선": { applied: false, qty: 0, refunded: false, registered: false },
 *     "하준": { applied: false, qty: 0, refunded: false, registered: false },
 *     "하민": { applied: false, qty: 0, refunded: false, registered: false }
 *   }
 * }
 */

/** 전체 체크리스트 항목 반환 */
async function getAllChecklists() {
    const db = await openDB();
    return req2p(db.transaction('ipo_checklist', 'readonly').objectStore('ipo_checklist').getAll());
}

/** 종목명으로 단일 체크리스트 항목 반환 */
async function getChecklist(corpName) {
    const db = await openDB();
    return req2p(db.transaction('ipo_checklist', 'readonly').objectStore('ipo_checklist').get(corpName));
}

/** 체크리스트 항목 저장 (upsert) */
async function saveChecklist(item) {
    const db = await openDB();
    return req2p(db.transaction('ipo_checklist', 'readwrite').objectStore('ipo_checklist').put(item));
}

/** 체크리스트 항목 삭제 */
async function deleteChecklist(corpName) {
    const db = await openDB();
    return req2p(db.transaction('ipo_checklist', 'readwrite').objectStore('ipo_checklist').delete(corpName));
}

// ── 계좌 이름 관리 ───────────────────────────────────────

/** 기본 계좌 이름 목록 (설정이 없을 때 사용) */
const _DEFAULT_ACCOUNT_NAMES = ['경록', '지선', '하준', '하민'];

/**
 * localStorage에서 계좌 이름 목록을 반환합니다.
 * 설정되지 않은 경우 기본값을 반환합니다.
 * @returns {string[]} 계좌 이름 배열
 */
function getAccountNames() {
    const raw = localStorage.getItem('accountNames');
    if (!raw) return [..._DEFAULT_ACCOUNT_NAMES];
    try {
        const arr = JSON.parse(raw);
        return Array.isArray(arr) && arr.length ? arr : [..._DEFAULT_ACCOUNT_NAMES];
    } catch (e) { return [..._DEFAULT_ACCOUNT_NAMES]; }
}

/**
 * 계좌 이름 목록을 localStorage에 저장합니다.
 * @param {string[]} names - 저장할 계좌 이름 배열 (빈 문자열 자동 제거)
 */
function setAccountNames(names) {
    localStorage.setItem('accountNames', JSON.stringify(names.filter(n => n.trim())));
}

// ── DART 공시 검색 (자동완성 보조) ───────────────────────
function getDartApiKey() {
    return localStorage.getItem('dartApiKey') || '';
}

function setDartApiKey(key) {
    localStorage.setItem('dartApiKey', key.trim());
}

// 캐시: 하루 1회만 DART 호출
// _dartFilingsCache: [{ corpName, receiptDate }] (YYYYMMDD)
let _dartFilingsCache = null;
let _dartCacheDate = null;

async function _getDartFilingsCache() {
    const today = new Date().toISOString().slice(0, 10);
    if (_dartFilingsCache && _dartCacheDate === today) return _dartFilingsCache;

    const key = getDartApiKey();
    if (!key) return [];

    const now = new Date();
    const end = now.toISOString().slice(0, 10).replace(/-/g, '');
    const bgnDate = new Date(now);
    bgnDate.setMonth(bgnDate.getMonth() - 3);
    const bgn = bgnDate.toISOString().slice(0, 10).replace(/-/g, '');

    const baseUrl = 'https://opendart.fss.or.kr/api/list.json'
        + '?crtfc_key=' + encodeURIComponent(key)
        + '&bgn_de=' + bgn
        + '&end_de=' + end
        + '&pblntf_ty=C&page_count=100';

    const allItems = [];
    let pageNo = 1;
    while (true) {
        const r    = await fetch(baseUrl + '&page_no=' + pageNo);
        const data = await r.json();
        if (!data || data.status !== '000' || !data.list || data.list.length === 0) break;
        allItems.push(...data.list);
        const totalCount = parseInt(data.total_count || '0', 10);
        if (allItems.length >= totalCount) break;
        pageNo++;
    }

    _dartFilingsCache = allItems
        .filter(i => i.report_nm && i.report_nm.includes('지분증권') && i.corp_name)
        .map(i => ({ corpName: i.corp_name, receiptDate: i.rcept_dt || '', rceptNo: i.rcept_no || '' }));
    _dartCacheDate = today;
    return _dartFilingsCache;
}

async function searchDartCorpName(query) {
    if (!getDartApiKey() || !query) return [];
    try {
        const cache = await _getDartFilingsCache();
        const names = [...new Set(cache.map(f => f.corpName))];
        return names.filter(name => name.includes(query)).slice(0, 10);
    } catch {
        return [];
    }
}

// ipo1.json 결과 캐시: rceptNo → { corpName, subscriptionStartDate, subscriptionEndDate, listingDate }
let _ipoScheduleCache = {};
let _ipoScheduleCacheDate = null;

async function getDartIpoSchedulesByMonth(year, month) {
    if (!getDartApiKey()) return [];
    try {
        const today = new Date().toISOString().slice(0, 10);
        if (_ipoScheduleCacheDate !== today) {
            _ipoScheduleCache = {};
            _ipoScheduleCacheDate = today;
        }

        const filings = await _getDartFilingsCache();
        const key = getDartApiKey();

        // 아직 캐시에 없는 filing만 ipo1.json 병렬 호출
        const missing = filings.filter(f => f.rceptNo && !_ipoScheduleCache.hasOwnProperty(f.rceptNo));
        await Promise.all(missing.map(async f => {
            try {
                const url = 'https://opendart.fss.or.kr/api/ipo1.json'
                    + '?crtfc_key=' + encodeURIComponent(key)
                    + '&rcept_no=' + encodeURIComponent(f.rceptNo);
                const r = await fetch(url);
                const data = await r.json();
                if (!data || data.status !== '000') { _ipoScheduleCache[f.rceptNo] = null; return; }

                const toDate = s => (s && s.length === 8)
                    ? s.slice(0,4) + '-' + s.slice(4,6) + '-' + s.slice(6,8) : null;

                _ipoScheduleCache[f.rceptNo] = {
                    corpName:              f.corpName,
                    subscriptionStartDate: toDate(data.subscr_sttd),
                    subscriptionEndDate:   toDate(data.subscr_end_d),
                    listingDate:           toDate(data.lstg_dt)
                };
            } catch {
                _ipoScheduleCache[f.rceptNo] = null;
            }
        }));

        const yearMonth = String(year) + '-' + String(month).padStart(2, '0');
        return Object.values(_ipoScheduleCache).filter(s => {
            if (!s) return false;
            return (s.subscriptionStartDate && s.subscriptionStartDate.startsWith(yearMonth))
                || (s.subscriptionEndDate   && s.subscriptionEndDate.startsWith(yearMonth))
                || (s.listingDate           && s.listingDate.startsWith(yearMonth));
        });
    } catch {
        return [];
    }
}

// 앱 시작 시 초기화
initDefaultData().catch(console.error);

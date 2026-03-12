/**
 * db.js - IndexedDB 기반 로컬 데이터 저장소
 * Spring Boot + MySQL 역할을 브라우저 IndexedDB로 대체
 */

const DB_NAME    = 'ipoTracker';
const DB_VERSION = 1;

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
        };
        req.onsuccess = e => { _db = e.target.result; resolve(_db); };
        req.onerror   = e => reject(e.target.error);
    });
}

function tx(store, mode, fn) {
    return openDB().then(db => new Promise((resolve, reject) => {
        const t = db.transaction(store, mode);
        t.onerror = e => reject(e.target.error);
        resolve(fn(t.objectStore(store)));
    }));
}

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

// ── DART 공시 검색 (자동완성 보조) ───────────────────────
function getDartApiKey() {
    return localStorage.getItem('dartApiKey') || '';
}

function setDartApiKey(key) {
    localStorage.setItem('dartApiKey', key.trim());
}

async function searchDartCorpName(query) {
    const key = getDartApiKey();
    if (!key || !query) return [];
    try {
        const url = 'https://opendart.fss.or.kr/api/list.json'
            + '?crtfc_key=' + encodeURIComponent(key)
            + '&corp_name=' + encodeURIComponent(query)
            + '&pblntf_ty=A&page_count=20';
        const r    = await fetch(url);
        const data = await r.json();
        if (!data || data.status !== '000' || !data.list) return [];
        return [...new Set(data.list.map(i => i.corp_name).filter(Boolean))].slice(0, 10);
    } catch {
        return [];
    }
}

// 앱 시작 시 초기화
initDefaultData().catch(console.error);

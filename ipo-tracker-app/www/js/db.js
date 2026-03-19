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

/** IndexedDB 데이터베이스 이름 */
const DB_NAME    = 'ipoTracker';

/** IndexedDB 스키마 버전 (Object Store 구조 변경 시 증가) */
const DB_VERSION = 2;

/**
 * 증권사별 기본 청약수수료 목록.
 * broker_fee Object Store가 비어 있을 때 initDefaultData()가 이 값으로 초기화합니다.
 * 단위: 원(KRW)
 */
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

/**
 * 싱글턴 IDBDatabase 인스턴스.
 * openDB() 최초 호출 후 이 변수에 캐시되어 이후 호출에서 재사용됩니다.
 * @type {IDBDatabase|null}
 */
let _db = null;

/**
 * IndexedDB를 열고 연결을 반환합니다 (싱글턴).
 * 최초 호출 시 Object Store를 생성하고 초기 인덱스를 설정합니다.
 *
 * onupgradeneeded 이벤트에서 처리하는 작업:
 *   - ipo_subscription: 청약 내역 스토어 + year 인덱스 생성
 *   - ipo_stock: 자동완성용 종목 스토어 생성
 *   - broker_fee: 증권사 수수료 스토어 생성
 *   - ipo_checklist: v2에서 추가된 청약 체크리스트 스토어 생성
 *
 * @returns {Promise<IDBDatabase>} 열린 데이터베이스 인스턴스
 */
function openDB() {
    if (_db) return Promise.resolve(_db);
    return new Promise((resolve, reject) => {
        const req = indexedDB.open(DB_NAME, DB_VERSION);
        req.onupgradeneeded = e => {
            const db = e.target.result;
            // ipo_subscription 스토어: id를 autoIncrement PK로 사용, year 인덱스로 연도별 조회 지원
            if (!db.objectStoreNames.contains('ipo_subscription')) {
                const s = db.createObjectStore('ipo_subscription', { keyPath: 'id', autoIncrement: true });
                s.createIndex('year', 'year', { unique: false });
            }
            // ipo_stock 스토어: 종목명(stockName)을 PK로 사용하는 자동완성용 종목 목록
            if (!db.objectStoreNames.contains('ipo_stock')) {
                db.createObjectStore('ipo_stock', { keyPath: 'stockName' });
            }
            // broker_fee 스토어: 증권사명(brokerName)을 PK로 사용하는 수수료 목록
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

/**
 * IndexedDB 트랜잭션 헬퍼: store에 대해 mode 트랜잭션을 열고 fn을 실행합니다.
 *
 * @param {string} store - 대상 Object Store 이름
 * @param {IDBTransactionMode} mode - 트랜잭션 모드 ('readonly' | 'readwrite')
 * @param {function(IDBObjectStore): IDBRequest} fn - 스토어를 인자로 받아 IDBRequest를 반환하는 함수
 * @returns {Promise<any>} fn이 반환한 IDBRequest의 결과값
 */
function tx(store, mode, fn) {
    return openDB().then(db => new Promise((resolve, reject) => {
        const t = db.transaction(store, mode);
        t.onerror = e => reject(e.target.error);
        resolve(fn(t.objectStore(store)));
    }));
}

/**
 * IDBRequest를 Promise로 래핑하는 유틸 함수.
 * IndexedDB의 콜백 기반 API를 async/await 패턴으로 사용할 수 있게 합니다.
 *
 * @param {IDBRequest} r - 래핑할 IDBRequest 객체
 * @returns {Promise<any>} 요청 성공 시 result, 실패 시 error로 reject
 */
function req2p(r) {
    return new Promise((res, rej) => {
        r.onsuccess = e => res(e.target.result);
        r.onerror   = e => rej(e.target.error);
    });
}

// ── 초기 데이터 (broker_fee 기본값) ─────────────────────

/**
 * broker_fee 스토어에 기본 증권사 수수료 데이터를 초기화합니다.
 * 이미 존재하는 항목(기존에 사용자가 수정한 수수료)은 덮어쓰지 않고 건너뜁니다.
 * 앱 시작 시 한 번 호출됩니다.
 *
 * @returns {Promise<void>} 초기화 완료 시 resolve
 */
async function initDefaultData() {
    const db = await openDB();
    const t = db.transaction('broker_fee', 'readwrite');
    const s = t.objectStore('broker_fee');
    for (const fee of DEFAULT_BROKER_FEES) {
        // 이미 존재하는 증권사 수수료는 유지 (사용자 커스텀 값 보호)
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
 *
 * @param {Object} item - 청약 내역 객체
 * @param {number|string} item.soldPrice - 매도가 (없으면 null/빈 문자열)
 * @param {number|string} item.offeringPrice - 공모가
 * @param {number|string} item.soldQty - 매도 수량 (배정 수량)
 * @param {number|string} [item.taxAndFee] - 매도 시 발생한 세금 및 증권사 수수료
 * @param {number|string} [item.subscriptionFee] - 청약 시 납부한 청약수수료
 * @returns {number|null} 계산된 수익(원), 미매도인 경우 null
 */
function calcProfit(item) {
    if (item.soldPrice == null || item.soldPrice === '') return null;
    return (Number(item.soldPrice) - Number(item.offeringPrice)) * Number(item.soldQty || 0)
         - Number(item.taxAndFee || 0)
         - Number(item.subscriptionFee || 0);
}

/**
 * 공모주 1건의 수익률(%)을 계산합니다.
 * 수익률 = 수익 / (공모가 × 배정수량) × 100
 *
 * @param {Object} item - 청약 내역 객체 (calcProfit과 동일한 구조)
 * @returns {number|null} 수익률(%), 계산 불가 시 null
 */
function calcProfitRate(item) {
    const profit = calcProfit(item);
    if (profit == null || !item.offeringPrice || !item.soldQty) return null;
    // 투자 원금 = 공모가 × 배정수량
    const cost = Number(item.offeringPrice) * Number(item.soldQty);
    if (!cost) return null;
    return (profit / cost) * 100;
}

/**
 * 청약 내역 객체에 계산된 profit과 profitRate 필드를 추가하여 반환합니다.
 * 원본 객체를 변경하지 않고 새 객체를 반환합니다(불변성 유지).
 *
 * @param {Object} item - 원본 청약 내역 객체
 * @returns {Object} profit과 profitRate가 추가된 새 객체
 */
function enrich(item) {
    return { ...item, profit: calcProfit(item), profitRate: calcProfitRate(item) };
}

// ── IPO Subscription CRUD ────────────────────────────────

/**
 * 특정 연도의 공모주 청약 내역 전체를 조회합니다.
 * year 인덱스를 사용하여 효율적으로 필터링하며, 각 항목에 수익 계산 결과를 포함합니다.
 *
 * @param {number|string} year - 조회할 연도 (예: 2024)
 * @returns {Promise<Object[]>} profit/profitRate가 포함된 청약 내역 배열
 */
async function getIposByYear(year) {
    const db = await openDB();
    return new Promise((res, rej) => {
        const t = db.transaction('ipo_subscription', 'readonly');
        const r = t.objectStore('ipo_subscription').index('year').getAll(parseInt(year));
        r.onsuccess = e => res(e.target.result.map(enrich));
        r.onerror   = e => rej(e.target.error);
    });
}

/**
 * 새로운 공모주 청약 내역을 등록합니다.
 * id는 autoIncrement이므로 dto에서 제거 후 저장합니다.
 * 저장 완료 후 ipo_stock 스토어에도 종목 정보를 upsert합니다.
 *
 * @param {Object} dto - 청약 내역 데이터 전송 객체
 * @param {string} dto.stockName - 종목명 (자동완성 스토어에도 저장됨)
 * @param {number} dto.offeringPrice - 공모가
 * @param {number} dto.year - 청약 연도 (인덱스용)
 * @returns {Promise<Object>} 생성된 id와 수익 계산 결과가 포함된 청약 내역 객체
 */
async function createIpo(dto) {
    const item = { ...dto, id: undefined };
    delete item.id;
    const db  = await openDB();
    const id  = await req2p(db.transaction('ipo_subscription', 'readwrite').objectStore('ipo_subscription').add(item));
    // 자동완성을 위해 종목 정보를 ipo_stock 스토어에 함께 저장
    await upsertIpoStock({ stockName: dto.stockName, offeringPrice: dto.offeringPrice });
    return enrich({ ...item, id });
}

/**
 * 기존 공모주 청약 내역을 수정합니다.
 * id를 int로 변환하여 기존 레코드를 덮어씁니다(put).
 * 수정 후 ipo_stock 스토어의 종목 정보도 갱신합니다.
 *
 * @param {number|string} id - 수정할 청약 내역의 ID
 * @param {Object} dto - 수정할 데이터 (전체 필드 교체)
 * @returns {Promise<Object>} 수익 계산 결과가 포함된 수정된 청약 내역 객체
 */
async function updateIpo(id, dto) {
    const item = { ...dto, id: parseInt(id) };
    const db   = await openDB();
    await req2p(db.transaction('ipo_subscription', 'readwrite').objectStore('ipo_subscription').put(item));
    // 종목명이나 공모가 변경 시 자동완성 스토어도 갱신
    await upsertIpoStock({ stockName: dto.stockName, offeringPrice: dto.offeringPrice });
    return enrich(item);
}

/**
 * 공모주 청약 내역을 삭제합니다.
 *
 * @param {number|string} id - 삭제할 청약 내역의 ID
 * @returns {Promise<undefined>} 삭제 완료 시 resolve
 */
async function deleteIpo(id) {
    const db = await openDB();
    return req2p(db.transaction('ipo_subscription', 'readwrite').objectStore('ipo_subscription').delete(parseInt(id)));
}

/**
 * 출금완료 여부만 업데이트합니다 (다른 필드 보존).
 * 전체 레코드를 교체하는 updateIpo와 달리, 기존 데이터를 읽어온 뒤
 * withdrawn 필드만 변경하는 부분 업데이트(patch) 방식으로 동작합니다.
 *
 * @param {number|string} id - 수정할 청약 내역의 ID
 * @param {boolean} withdrawn - 출금 완료 여부
 * @returns {Promise<Object|undefined>} 수정된 청약 내역 객체, ID 미존재 시 undefined
 */
async function patchIpoWithdrawn(id, withdrawn) {
    const db = await openDB();
    return new Promise((res, rej) => {
        const t     = db.transaction('ipo_subscription', 'readwrite');
        const store = t.objectStore('ipo_subscription');
        const get   = store.get(parseInt(id));
        get.onsuccess = e => {
            const item = e.target.result;
            if (!item) { res(); return; }
            // withdrawn 필드만 교체하고 나머지 필드는 유지
            item.withdrawn = withdrawn;
            const put = store.put(item);
            put.onsuccess = () => res(item);
            put.onerror   = e => rej(e.target.error);
        };
        get.onerror = e => rej(e.target.error);
    });
}

/**
 * 특정 연도의 월별 수익 집계를 반환합니다.
 * 매도일(soldDate) 기준으로 집계하며, 수익이 0인 월은 결과에서 제외합니다.
 *
 * @param {number|string} year - 집계할 연도
 * @returns {Promise<Array<{month: number, totalProfit: number}>>}
 *   수익이 있는 월의 월번호(1~12)와 총 수익 합계 배열
 */
async function getMonthlySummary(year) {
    const items = await getIposByYear(year);
    // 1~12월 초기값 0으로 설정
    const map = {};
    for (let m = 1; m <= 12; m++) map[m] = 0;
    items.forEach(item => {
        // 수익이 계산된 항목이고 매도일이 존재하는 경우에만 집계
        if (item.profit != null && item.soldDate) {
            const m = parseInt(item.soldDate.substring(5, 7));
            if (!isNaN(m)) map[m] += item.profit;
        }
    });
    // 수익이 0인 월은 제거하고 반환
    return Object.entries(map)
        .filter(([, v]) => v !== 0)
        .map(([month, totalProfit]) => ({ month: parseInt(month), totalProfit }));
}

/**
 * 모든 연도의 공모주 청약 내역을 조회합니다 (백업 등에 사용).
 * year 인덱스를 사용하지 않고 전체 스토어를 순회합니다.
 *
 * @returns {Promise<Object[]>} 전체 청약 내역 배열 (수익 계산 없음)
 */
async function getAllIpos() {
    const db = await openDB();
    return req2p(db.transaction('ipo_subscription', 'readonly').objectStore('ipo_subscription').getAll());
}

// ── IPO Stock (자동완성용) ────────────────────────────────

/**
 * 자동완성용 종목 정보 전체를 조회합니다.
 * 청약 내역 등록/수정 시 자동으로 추가되므로 별도 관리가 필요 없습니다.
 *
 * @returns {Promise<Array<{stockName: string, offeringPrice: number}>>} 종목 정보 배열
 */
async function getAllIpoStocks() {
    const db = await openDB();
    return req2p(db.transaction('ipo_stock', 'readonly').objectStore('ipo_stock').getAll());
}

/**
 * 자동완성용 종목 정보를 저장합니다 (없으면 insert, 있으면 update).
 * stockName이 없는 경우 아무 동작도 하지 않습니다.
 *
 * @param {{stockName: string, offeringPrice: number}} stock - 저장할 종목 정보
 * @returns {Promise<string>} 저장된 stockName (IDBRequest 결과값)
 */
async function upsertIpoStock(stock) {
    if (!stock.stockName) return;
    const db = await openDB();
    return req2p(db.transaction('ipo_stock', 'readwrite').objectStore('ipo_stock').put(stock));
}

// ── Broker Fee ───────────────────────────────────────────

/**
 * 모든 증권사의 청약수수료 목록을 조회합니다.
 *
 * @returns {Promise<Array<{brokerName: string, feeAmount: number}>>} 증권사 수수료 배열
 */
async function getAllBrokerFees() {
    const db = await openDB();
    return req2p(db.transaction('broker_fee', 'readonly').objectStore('broker_fee').getAll());
}

/**
 * 특정 증권사의 청약수수료를 수정합니다.
 * brokerName이 PK이므로 존재하면 update, 없으면 insert됩니다.
 *
 * @param {string} brokerName - 수수료를 수정할 증권사명
 * @param {number} feeAmount - 새로운 수수료 금액(원)
 * @returns {Promise<string>} 저장된 brokerName (IDBRequest 결과값)
 */
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

/**
 * 전체 체크리스트 항목을 조회합니다.
 * 반환 배열의 순서는 삽입 순서를 따릅니다.
 *
 * @returns {Promise<Object[]>} 체크리스트 항목 배열
 */
async function getAllChecklists() {
    const db = await openDB();
    return req2p(db.transaction('ipo_checklist', 'readonly').objectStore('ipo_checklist').getAll());
}

/**
 * 종목명으로 단일 체크리스트 항목을 조회합니다.
 *
 * @param {string} corpName - 조회할 종목명 (PK)
 * @returns {Promise<Object|undefined>} 해당 종목의 체크리스트 항목, 없으면 undefined
 */
async function getChecklist(corpName) {
    const db = await openDB();
    return req2p(db.transaction('ipo_checklist', 'readonly').objectStore('ipo_checklist').get(corpName));
}

/**
 * 체크리스트 항목을 저장합니다 (없으면 insert, 있으면 update).
 * corpName이 PK이므로 동일 종목명이면 전체 교체됩니다.
 *
 * @param {Object} item - 저장할 체크리스트 항목 (corpName 필수)
 * @returns {Promise<string>} 저장된 corpName (IDBRequest 결과값)
 */
async function saveChecklist(item) {
    const db = await openDB();
    return req2p(db.transaction('ipo_checklist', 'readwrite').objectStore('ipo_checklist').put(item));
}

/**
 * 체크리스트 항목을 삭제합니다.
 *
 * @param {string} corpName - 삭제할 종목명 (PK)
 * @returns {Promise<undefined>} 삭제 완료 시 resolve
 */
async function deleteChecklist(corpName) {
    const db = await openDB();
    return req2p(db.transaction('ipo_checklist', 'readwrite').objectStore('ipo_checklist').delete(corpName));
}

// ── 계좌 이름 관리 ───────────────────────────────────────

/**
 * 기본 계좌 이름 목록 (localStorage에 설정이 없을 때 사용).
 * getAccountNames()에서 파싱 실패 또는 빈 배열일 때도 이 값이 반환됩니다.
 * @type {string[]}
 */
const _DEFAULT_ACCOUNT_NAMES = ['본인', '아내', '자녀1', '자녀2'];

/**
 * localStorage에서 계좌 이름 목록을 반환합니다.
 * 설정되지 않은 경우 기본값(_DEFAULT_ACCOUNT_NAMES)을 반환합니다.
 * JSON 파싱 실패 시에도 기본값을 반환하며 오류를 전파하지 않습니다.
 *
 * @returns {string[]} 계좌 이름 배열 (최소 1개 이상)
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
 * 빈 문자열(공백만 있는 항목 포함)은 자동으로 제거됩니다.
 *
 * @param {string[]} names - 저장할 계좌 이름 배열 (빈 문자열 자동 제거)
 */
function setAccountNames(names) {
    localStorage.setItem('accountNames', JSON.stringify(names.filter(n => n.trim())));
}

// ── DART 공시 검색 (자동완성 보조) ───────────────────────

/**
 * localStorage에서 DART Open API 키를 반환합니다.
 * 키가 없으면 빈 문자열을 반환합니다.
 *
 * @returns {string} DART API 인증키 (없으면 빈 문자열)
 */
function getDartApiKey() {
    return localStorage.getItem('dartApiKey') || '';
}

/**
 * DART Open API 키를 localStorage에 저장합니다.
 * 앞뒤 공백은 자동으로 제거됩니다.
 *
 * @param {string} key - 저장할 DART API 인증키
 */
function setDartApiKey(key) {
    localStorage.setItem('dartApiKey', key.trim());
}

// ── 백업 / 복원 ──────────────────────────────────────────

/**
 * 청약내역 + 체크리스트 전체를 JSON 파일로 내보냅니다.
 * Android(Capacitor): @capacitor/filesystem으로 다운로드 폴더에 저장
 * 브라우저 fallback: data URI 다운로드
 *
 * 저장 우선순위 (Capacitor 환경):
 *   1순위: 공개 Download 폴더 (EXTERNAL_STORAGE)
 *   2순위: 앱 전용 외부 폴더 (EXTERNAL)
 *
 * 백업 파일 구조:
 *   { version, exportedAt, accountNames, ipos[], checklists[] }
 *
 * @returns {Promise<void>}
 */
async function exportBackup() {
    const [ipos, checklists] = await Promise.all([getAllIpos(), getAllChecklists()]);
    const data = {
        version:      1,
        exportedAt:   new Date().toISOString(),
        accountNames: getAccountNames(),
        ipos,
        checklists
    };
    const jsonStr  = JSON.stringify(data, null, 2);
    const filename = `ipo-backup-${new Date().toISOString().slice(0, 10)}.json`;

    // ① Capacitor 앱: Filesystem 플러그인으로 저장
    if (window.Capacitor?.isNativePlatform()) {
        const { Filesystem } = window.Capacitor.Plugins;
        try { await Filesystem.requestPermissions(); } catch (_) {}

        // 1순위: 공개 Download 폴더
        try {
            await Filesystem.writeFile({
                path:      `Download/${filename}`,
                data:      jsonStr,
                directory: 'EXTERNAL_STORAGE',
                encoding:  'utf8',
                recursive: true
            });
            alert(`✅ 다운로드 폴더에 저장됐습니다!\n파일명: ${filename}`);
            return;
        } catch (e) { console.warn('ExternalStorage 실패, External 시도:', e.message); }

        // 2순위: 앱 전용 외부 폴더
        try {
            await Filesystem.writeFile({
                path:      filename,
                data:      jsonStr,
                directory: 'EXTERNAL',
                encoding:  'utf8',
                recursive: true
            });
            alert(`✅ 저장됐습니다!\n📂 파일 관리자 → 내부저장소 → Android → data → [앱] → files\n파일명: ${filename}`);
            return;
        } catch (e2) {
            alert('저장 실패: ' + e2.message);
            return;
        }
    }

    // ② 브라우저 fallback: data URI 다운로드
    const a = document.createElement('a');
    a.href     = 'data:application/json;charset=utf-8,' + encodeURIComponent(jsonStr);
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

/**
 * JSON 백업 파일을 읽어 복원합니다.
 * 기존 데이터는 덮어쓰기 방식으로 병합됩니다 (삭제 후 재삽입이 아닌 put 방식).
 * 버전 필드(version)가 없으면 올바르지 않은 파일로 간주하여 오류를 던집니다.
 *
 * 복원 항목:
 *   - ipo_subscription: 청약 내역 (기존 id 그대로 put)
 *   - ipo_checklist: 체크리스트 항목
 *   - accountNames: 계좌 이름 목록 (localStorage)
 *
 * @param {File} file - input[type=file]에서 선택된 파일
 * @returns {Promise<{ipos: number, checklists: number}>} 복원된 청약내역/체크리스트 건수
 * @throws {Error} 파일 읽기 실패 또는 유효하지 않은 백업 파일인 경우
 */
async function importBackup(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = async e => {
            try {
                const data = JSON.parse(e.target.result);
                if (!data.version) throw new Error('올바른 백업 파일이 아닙니다.');

                const db = await openDB();
                let ipoCount = 0, clCount = 0;

                // 청약내역 복원 (기존 id 그대로 put)
                if (Array.isArray(data.ipos)) {
                    const t = db.transaction('ipo_subscription', 'readwrite');
                    const s = t.objectStore('ipo_subscription');
                    for (const item of data.ipos) { s.put(item); ipoCount++; }
                    await new Promise(r => { t.oncomplete = r; t.onerror = r; });
                }

                // 체크리스트 복원
                if (Array.isArray(data.checklists)) {
                    const t = db.transaction('ipo_checklist', 'readwrite');
                    const s = t.objectStore('ipo_checklist');
                    for (const item of data.checklists) { s.put(item); clCount++; }
                    await new Promise(r => { t.oncomplete = r; t.onerror = r; });
                }

                // 계좌 이름 복원
                if (Array.isArray(data.accountNames) && data.accountNames.length) {
                    setAccountNames(data.accountNames);
                }

                resolve({ ipos: ipoCount, checklists: clCount });
            } catch (err) { reject(err); }
        };
        reader.onerror = () => reject(new Error('파일 읽기 실패'));
        reader.readAsText(file);
    });
}

// 캐시: 하루 1회만 DART 호출
// _dartFilingsCache: [{ corpName, receiptDate }] (YYYYMMDD)

/**
 * DART 공시 목록 캐시 (하루 1회만 API 호출).
 * null이면 아직 로드되지 않은 상태입니다.
 * @type {Array<{corpName: string, receiptDate: string, rceptNo: string}>|null}
 */
let _dartFilingsCache = null;

/**
 * 마지막으로 DART API를 호출한 날짜 (YYYY-MM-DD 형식).
 * 오늘 날짜와 다르면 캐시를 무효화하고 재호출합니다.
 * @type {string|null}
 */
let _dartCacheDate = null;

/**
 * DART Open API에서 최근 3개월 내 증권신고서(지분증권) 공시 목록을 가져와 캐시합니다.
 * 하루에 한 번만 실제 API를 호출하고, 이후에는 메모리 캐시를 반환합니다.
 * API 키가 없으면 빈 배열을 반환합니다.
 *
 * 페이지네이션을 처리하여 전체 결과를 가져옵니다(page_count=100).
 * 결과는 report_nm에 '지분증권'이 포함된 항목만 필터링합니다.
 *
 * @returns {Promise<Array<{corpName: string, receiptDate: string, rceptNo: string}>>}
 *   공시 목록 배열 (corpName: 회사명, receiptDate: 접수일자 YYYYMMDD, rceptNo: 접수번호)
 */
async function _getDartFilingsCache() {
    const today = new Date().toISOString().slice(0, 10);
    // 오늘 이미 조회했다면 캐시 반환
    if (_dartFilingsCache && _dartCacheDate === today) return _dartFilingsCache;

    const key = getDartApiKey();
    if (!key) return [];

    // 조회 기간: 오늘부터 3개월 전까지
    const now = new Date();
    const end = now.toISOString().slice(0, 10).replace(/-/g, '');
    const bgnDate = new Date(now);
    bgnDate.setMonth(bgnDate.getMonth() - 3);
    const bgn = bgnDate.toISOString().slice(0, 10).replace(/-/g, '');

    // DART API 기본 URL: 공시유형 C(발행공시), 100건씩 조회
    const baseUrl = 'https://opendart.fss.or.kr/api/list.json'
        + '?crtfc_key=' + encodeURIComponent(key)
        + '&bgn_de=' + bgn
        + '&end_de=' + end
        + '&pblntf_ty=C&page_count=100';

    const allItems = [];
    let pageNo = 1;
    // 전체 데이터를 페이지 단위로 반복 조회
    while (true) {
        const r    = await fetch(baseUrl + '&page_no=' + pageNo);
        const data = await r.json();
        if (!data || data.status !== '000' || !data.list || data.list.length === 0) break;
        allItems.push(...data.list);
        const totalCount = parseInt(data.total_count || '0', 10);
        // 전체 건수를 모두 가져왔으면 종료
        if (allItems.length >= totalCount) break;
        pageNo++;
    }

    // '지분증권'이 포함된 공시만 필터링하여 필요한 필드만 추출
    _dartFilingsCache = allItems
        .filter(i => i.report_nm && i.report_nm.includes('지분증권') && i.corp_name)
        .map(i => ({ corpName: i.corp_name, receiptDate: i.rcept_dt || '', rceptNo: i.rcept_no || '' }));
    _dartCacheDate = today;
    return _dartFilingsCache;
}

/**
 * 입력한 검색어로 DART 공시 목록에서 회사명을 자동완성 검색합니다.
 * API 키나 검색어가 없으면 빈 배열을 반환합니다.
 * 최대 10개의 결과를 반환합니다.
 *
 * @param {string} query - 검색할 회사명 (부분 일치)
 * @returns {Promise<string[]>} 일치하는 회사명 배열 (최대 10개)
 */
async function searchDartCorpName(query) {
    if (!getDartApiKey() || !query) return [];
    try {
        const cache = await _getDartFilingsCache();
        // 중복 제거 후 검색어가 포함된 회사명만 반환
        const names = [...new Set(cache.map(f => f.corpName))];
        return names.filter(name => name.includes(query)).slice(0, 10);
    } catch {
        return [];
    }
}

// ipo1.json 결과 캐시: rceptNo → { corpName, subscriptionStartDate, subscriptionEndDate, listingDate }

/**
 * DART ipo1.json API 결과 캐시.
 * 접수번호(rceptNo)를 키로, 청약일정 정보를 값으로 저장합니다.
 * API 호출 실패 시 해당 키의 값이 null로 저장됩니다.
 * @type {Object.<string, {corpName: string, subscriptionStartDate: string|null, subscriptionEndDate: string|null, listingDate: string|null}|null>}
 */
let _ipoScheduleCache = {};

/**
 * ipo1.json 캐시를 마지막으로 초기화한 날짜 (YYYY-MM-DD).
 * 날짜가 바뀌면 캐시를 전체 초기화합니다.
 * @type {string|null}
 */
let _ipoScheduleCacheDate = null;

/**
 * 특정 연도/월에 청약일정이 있는 공모주 목록을 DART API에서 조회합니다.
 * _getDartFilingsCache()로 공시 목록을 먼저 가져온 뒤,
 * 캐시에 없는 접수번호에 대해서만 ipo1.json API를 병렬로 호출합니다.
 *
 * 조건: subscriptionStartDate, subscriptionEndDate, listingDate 중 하나라도
 * 해당 연월(YYYY-MM)에 해당하면 결과에 포함됩니다.
 *
 * @param {number} year - 조회할 연도
 * @param {number} month - 조회할 월 (1~12)
 * @returns {Promise<Array<{corpName: string, subscriptionStartDate: string|null, subscriptionEndDate: string|null, listingDate: string|null}>>}
 *   해당 월의 공모주 일정 배열. API 키 없거나 오류 시 빈 배열 반환.
 */
async function getDartIpoSchedulesByMonth(year, month) {
    if (!getDartApiKey()) return [];
    try {
        const today = new Date().toISOString().slice(0, 10);
        // 날짜가 바뀌면 ipo1.json 캐시 전체 초기화
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

                // YYYYMMDD → YYYY-MM-DD 변환 헬퍼
                const toDate = s => (s && s.length === 8)
                    ? s.slice(0,4) + '-' + s.slice(4,6) + '-' + s.slice(6,8) : null;

                // 청약 일정 정보를 캐시에 저장
                _ipoScheduleCache[f.rceptNo] = {
                    corpName:              f.corpName,
                    subscriptionStartDate: toDate(data.subscr_sttd),
                    subscriptionEndDate:   toDate(data.subscr_end_d),
                    listingDate:           toDate(data.lstg_dt)
                };
            } catch {
                // 개별 API 호출 실패 시 null로 마킹하여 재호출 방지
                _ipoScheduleCache[f.rceptNo] = null;
            }
        }));

        // 해당 연월에 청약 시작일/마감일/상장일 중 하나라도 포함된 항목만 반환
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

// 앱 시작 시 초기화: broker_fee 스토어에 기본 수수료 데이터 삽입
initDefaultData().catch(console.error);

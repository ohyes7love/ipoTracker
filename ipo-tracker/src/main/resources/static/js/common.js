/**
 * common.js - Spring Boot 템플릿 공통 유틸리티
 *
 * 계좌 이름, 앱 설정 등을 localStorage를 통해 관리합니다.
 * 모든 Thymeleaf 템플릿에서 공통으로 사용됩니다.
 *
 * 사용처:
 * - index.html  : 설정 모달에서 계좌 이름 읽기/저장
 * - ipo.html    : 계좌 체크박스 렌더링, 계좌 이름 목록 조회
 * - checklist.html : 계좌별 청약 상태 관리
 * - stats.html  : 계좌별 수익 집계
 */

/** 기본 계좌 이름 목록 (설정이 없을 때 사용) */
const _DEFAULT_ACCOUNT_NAMES = ['본인', '아내', '자녀1', '자녀2'];

/**
 * localStorage에서 계좌 이름 목록을 반환합니다.
 * 설정되지 않은 경우 기본값을 반환합니다.
 *
 * localStorage 키: 'accountNames'
 * 저장 형식: JSON 배열 문자열 (예: '["본인","아내","자녀1"]')
 *
 * 오류 처리:
 * - JSON 파싱 실패 시 기본값 반환
 * - 빈 배열인 경우 기본값 반환
 *
 * @returns {string[]} 계좌 이름 배열 (스프레드로 복사본 반환)
 */
function getAccountNames() {
    const raw = localStorage.getItem('accountNames');
    if (!raw) return [..._DEFAULT_ACCOUNT_NAMES];
    try {
        const arr = JSON.parse(raw);
        // 유효한 배열이고 최소 1개 이상의 항목이 있어야 사용
        return Array.isArray(arr) && arr.length ? arr : [..._DEFAULT_ACCOUNT_NAMES];
    } catch (e) { return [..._DEFAULT_ACCOUNT_NAMES]; }
}

/**
 * 계좌 이름 목록을 localStorage에 저장합니다.
 * 빈 문자열(공백만 있는 항목 포함)은 자동으로 제거됩니다.
 *
 * localStorage 키: 'accountNames'
 * 저장 형식: JSON 배열 문자열
 *
 * 사용 예시:
 *   setAccountNames(['본인', '아내', '자녀1']); // 저장
 *   setAccountNames(['본인', '', '아내']);       // 빈 문자열 제거 후 저장
 *
 * @param {string[]} names - 저장할 계좌 이름 배열 (빈 문자열 자동 제거)
 */
function setAccountNames(names) {
    // trim() 후 빈 문자열 필터링하여 불필요한 항목 제거
    localStorage.setItem('accountNames', JSON.stringify(names.filter(n => n.trim())));
}

/**
 * common.js - Spring Boot 템플릿 공통 유틸리티
 *
 * 계좌 이름, 앱 설정 등을 localStorage를 통해 관리합니다.
 * 모든 Thymeleaf 템플릿에서 공통으로 사용됩니다.
 */

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

/**
 * swipe.js — 모바일 스와이프 제스처 헬퍼
 *
 * initTabSwipe()     : 하단 탭 순서로 페이지 전환
 * initCalendarSwipe(): 캘린더 월 이동 + 빈영역 탭 전환
 * initPullRefresh()  : 최상단에서 아래로 스와이프 → 새로고침
 */

// 하단 탭 순서
const _TABS = ['index.html', 'ipo.html', 'calendar.html', 'checklist.html', 'stats.html'];

/**
 * 스와이프 감지 핵심 함수
 * @param {function} onLeft  - 왼쪽 스와이프 (→ 다음) 콜백
 * @param {function} onRight - 오른쪽 스와이프 (← 이전) 콜백
 * @param {function} [skip]  - truthy 반환 시 해당 터치 무시
 */
function _initSwipe(onLeft, onRight, skip) {
    let startX = 0, startY = 0, locked = false;

    document.addEventListener('touchstart', e => {
        startX  = e.touches[0].clientX;
        startY  = e.touches[0].clientY;
        locked  = skip ? skip(e.target) : false;
    }, { passive: true });

    document.addEventListener('touchend', e => {
        if (locked) return;
        const dx = e.changedTouches[0].clientX - startX;
        const dy = e.changedTouches[0].clientY - startY;
        // 수평 스와이프만 (최소 55px, 수직보다 1.5배 이상 길어야)
        if (Math.abs(dx) < 55 || Math.abs(dx) < Math.abs(dy) * 1.5) return;
        if (dx < 0) onLeft();
        else        onRight();
    }, { passive: true });
}

/**
 * 탭 전환 스와이프 초기화
 * 현재 파일명을 기준으로 왼쪽 → 다음 탭, 오른쪽 → 이전 탭
 */
function initTabSwipe() {
    const here = location.pathname.split('/').pop() || 'index.html';
    const idx  = _TABS.indexOf(here);

    _initSwipe(
        () => { if (idx < _TABS.length - 1) location.href = _TABS[idx + 1]; },
        () => { if (idx > 0)                location.href = _TABS[idx - 1]; },
        // 입력 요소·스크롤 영역·모달 안쪽에서 시작한 스와이프는 무시
        t => !!t.closest('input, textarea, select, button, a, .modal, .table-wrap, [data-swipe-ignore]')
    );
}

/**
 * 캘린더 전용 스와이프
 * - 캘린더 그리드(#calWrapper) 위 → 월 이동
 * - 그 외 빈 영역 → 탭 전환
 */
function initCalendarSwipe(calWrapperId) {
    const here = location.pathname.split('/').pop() || 'calendar.html';
    const idx  = _TABS.indexOf(here);

    let startX = 0, startY = 0, onCalendar = false;

    document.addEventListener('touchstart', e => {
        startX     = e.touches[0].clientX;
        startY     = e.touches[0].clientY;
        const cal  = document.getElementById(calWrapperId);
        onCalendar = !!(cal && cal.contains(e.target));
    }, { passive: true });

    document.addEventListener('touchend', e => {
        // 버튼·링크·입력·모달은 무시
        if (e.target.closest('input, textarea, select, button, a, .modal, [data-swipe-ignore]')) return;

        const dx = e.changedTouches[0].clientX - startX;
        const dy = e.changedTouches[0].clientY - startY;
        if (Math.abs(dx) < 55 || Math.abs(dx) < Math.abs(dy) * 1.5) return;

        if (onCalendar) {
            // 캘린더 그리드: 월 이동
            dx < 0 ? moveMonth(1) : moveMonth(-1);
        } else {
            // 빈 영역: 탭 전환
            if (dx < 0) { if (idx < _TABS.length - 1) location.href = _TABS[idx + 1]; }
            else         { if (idx > 0)                location.href = _TABS[idx - 1]; }
        }
    }, { passive: true });
}

/**
 * 풀-투-리프레시: 페이지 최상단에서 아래로 스와이프 → 새로고침
 * - 스크롤이 맨 위(scrollY===0)일 때만 동작
 * - 최소 80px 이상 수직으로 당겨야 실행
 */
function initPullRefresh() {
    let startY = 0, startX = 0, atTop = false;
    let indicator = null;

    // 인디케이터 엘리먼트 생성
    function getIndicator() {
        if (!indicator) {
            indicator = document.createElement('div');
            indicator.style.cssText = [
                'position:fixed', 'top:0', 'left:50%', 'transform:translateX(-50%)',
                'background:#0d6efd', 'color:#fff', 'border-radius:0 0 20px 20px',
                'padding:4px 18px', 'font-size:12px', 'font-weight:700',
                'z-index:9999', 'transition:opacity .2s', 'opacity:0', 'pointer-events:none'
            ].join(';');
            indicator.textContent = '↓ 새로고침';
            document.body.appendChild(indicator);
        }
        return indicator;
    }

    document.addEventListener('touchstart', e => {
        startY = e.touches[0].clientY;
        startX = e.touches[0].clientX;
        // 오버레이·시트·모달 안에서 시작한 터치는 무시
        const inOverlay = !!e.target.closest(
            '.modal, [class*="sheet"], #sheetOverlay, [data-no-refresh]'
        );
        atTop = !inOverlay &&
                (window.scrollY || document.documentElement.scrollTop) === 0;
    }, { passive: true });

    document.addEventListener('touchmove', e => {
        if (!atTop) return;
        const dy = e.touches[0].clientY - startY;
        const dx = Math.abs(e.touches[0].clientX - startX);
        if (dy > 30 && dy > dx * 1.5) {
            getIndicator().style.opacity = dy > 80 ? '1' : String((dy - 30) / 50);
        }
    }, { passive: true });

    document.addEventListener('touchend', e => {
        if (indicator) indicator.style.opacity = '0';
        if (!atTop) return;
        const dy = e.changedTouches[0].clientY - startY;
        const dx = Math.abs(e.changedTouches[0].clientX - startX);
        if (dy > 80 && dy > dx * 1.5) location.reload();
    }, { passive: true });
}

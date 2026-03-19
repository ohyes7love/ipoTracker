/**
 * swipe.js — 모바일 스와이프 제스처 헬퍼
 *
 * initTabSwipe()     : 하단 탭 순서로 페이지 전환
 * initCalendarSwipe(): 캘린더 월 이동 + 빈영역 탭 전환
 * initPullRefresh()  : 최상단에서 아래로 스와이프 → 새로고침
 */

/**
 * 앱의 하단 탭 네비게이션 순서.
 * 스와이프 방향에 따라 이 배열의 인접 항목으로 페이지를 이동합니다.
 * 배열 순서 = 왼쪽(인덱스 0)에서 오른쪽(마지막 인덱스) 방향입니다.
 * @type {string[]}
 */
// 하단 탭 순서
const _TABS = ['index.html', 'ipo.html', 'calendar.html', 'checklist.html', 'stats.html'];

/**
 * 스와이프 감지 핵심 함수.
 * document 전체에 touchstart/touchend 이벤트를 등록하여 좌우 스와이프를 감지합니다.
 *
 * 판정 기준:
 *   - 수평 이동 거리가 최소 55px 이상이어야 합니다.
 *   - 수평 이동 거리가 수직 이동 거리의 1.5배 이상이어야 수평 스와이프로 인정합니다.
 *     (세로 스크롤과 가로 스와이프를 구분하기 위한 조건)
 *
 * @param {function(): void} onLeft  - 왼쪽 스와이프(손가락이 오른쪽→왼쪽으로 이동) 시 호출되는 콜백
 * @param {function(): void} onRight - 오른쪽 스와이프(손가락이 왼쪽→오른쪽으로 이동) 시 호출되는 콜백
 * @param {function(EventTarget): boolean} [skip] - 터치 시작 대상 요소를 받아 truthy 반환 시 해당 터치를 무시
 */
function _initSwipe(onLeft, onRight, skip) {
    let startX = 0, startY = 0, locked = false;

    document.addEventListener('touchstart', e => {
        startX  = e.touches[0].clientX;
        startY  = e.touches[0].clientY;
        // skip 함수가 truthy를 반환하면 이 터치 이벤트는 스와이프로 처리하지 않음
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
 * 탭 전환 스와이프를 초기화합니다.
 * 현재 파일명을 _TABS 배열에서 찾아 인접 탭으로 페이지를 이동합니다.
 *
 * 동작:
 *   - 왼쪽 스와이프 → 다음 탭 (인덱스 +1)
 *   - 오른쪽 스와이프 → 이전 탭 (인덱스 -1)
 *   - 첫 번째/마지막 탭에서는 더 이상 이동하지 않음
 *
 * 스와이프가 무시되는 경우 (입력 요소 또는 스크롤/모달 영역 내부):
 *   input, textarea, select, button, a, .modal, .table-wrap, [data-swipe-ignore]
 */
function initTabSwipe() {
    // 현재 페이지의 파일명을 URL pathname에서 추출 (기본값: 'index.html')
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
 * 캘린더 페이지 전용 스와이프를 초기화합니다.
 * 터치 시작 위치가 캘린더 그리드 내부인지 외부인지에 따라 동작이 분기됩니다.
 *
 * 동작:
 *   - 캘린더 그리드(calWrapperId 요소) 위에서 스와이프:
 *       왼쪽 → 다음 달 이동 (moveMonth(1))
 *       오른쪽 → 이전 달 이동 (moveMonth(-1))
 *   - 캘린더 그리드 밖 빈 영역에서 스와이프:
 *       왼쪽 → 다음 탭 페이지 이동
 *       오른쪽 → 이전 탭 페이지 이동
 *
 * 스와이프가 무시되는 경우:
 *   input, textarea, select, button, a, .modal, [data-swipe-ignore]
 *
 * @param {string} calWrapperId - 캘린더 그리드 컨테이너 요소의 id 속성값
 */
function initCalendarSwipe(calWrapperId) {
    // 현재 페이지가 _TABS 배열에서 몇 번째 탭인지 파악
    const here = location.pathname.split('/').pop() || 'calendar.html';
    const idx  = _TABS.indexOf(here);

    let startX = 0, startY = 0, onCalendar = false;

    document.addEventListener('touchstart', e => {
        startX     = e.touches[0].clientX;
        startY     = e.touches[0].clientY;
        const cal  = document.getElementById(calWrapperId);
        // 터치가 캘린더 그리드 영역 내에서 시작됐는지 확인
        onCalendar = !!(cal && cal.contains(e.target));
    }, { passive: true });

    document.addEventListener('touchend', e => {
        // 버튼·링크·입력·모달은 무시
        if (e.target.closest('input, textarea, select, button, a, .modal, [data-swipe-ignore]')) return;

        const dx = e.changedTouches[0].clientX - startX;
        const dy = e.changedTouches[0].clientY - startY;
        // 수평 스와이프 판정: 최소 55px, 수직 이동의 1.5배 이상
        if (Math.abs(dx) < 55 || Math.abs(dx) < Math.abs(dy) * 1.5) return;

        if (onCalendar) {
            // 캘린더 그리드: 월 이동 (moveMonth는 calendar.html에서 전역 정의됨)
            dx < 0 ? moveMonth(1) : moveMonth(-1);
        } else {
            // 빈 영역: 탭 전환
            if (dx < 0) { if (idx < _TABS.length - 1) location.href = _TABS[idx + 1]; }
            else         { if (idx > 0)                location.href = _TABS[idx - 1]; }
        }
    }, { passive: true });
}

/**
 * 풀-투-리프레시(pull-to-refresh) 제스처를 초기화합니다.
 * 페이지 최상단(scrollY === 0)에서 아래 방향으로 스와이프하면 페이지를 새로고침합니다.
 *
 * 동작 조건:
 *   - 스크롤이 맨 위(window.scrollY === 0)인 상태에서만 활성화됩니다.
 *   - 수직 이동 거리가 80px 이상이어야 새로고침이 실행됩니다.
 *   - 수직 이동이 수평 이동보다 1.5배 이상 길어야 세로 제스처로 인정합니다.
 *
 * 무시되는 경우:
 *   - 모달, 바텀시트, 오버레이 내부에서 시작한 터치
 *   - [data-no-refresh] 속성을 가진 요소 내부에서 시작한 터치
 *
 * 시각적 피드백:
 *   - 30px 이상 당기면 상단에 '↓ 새로고침' 인디케이터가 서서히 나타납니다.
 *   - 80px 이상 당기면 인디케이터가 완전히 불투명해집니다.
 *   - 터치를 떼면 인디케이터가 사라집니다.
 */
function initPullRefresh() {
    let startY = 0, startX = 0, atTop = false;
    let indicator = null;

    /**
     * 풀-투-리프레시 시각적 인디케이터 요소를 생성하고 반환합니다.
     * 처음 호출 시에만 DOM에 추가하며, 이후에는 캐시된 요소를 반환합니다(싱글턴).
     *
     * @returns {HTMLDivElement} 인디케이터 div 요소
     */
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
        // 오버레이 내부가 아니고 스크롤이 맨 위일 때만 풀-투-리프레시 활성화
        atTop = !inOverlay &&
                (window.scrollY || document.documentElement.scrollTop) === 0;
    }, { passive: true });

    document.addEventListener('touchmove', e => {
        if (!atTop) return;
        const dy = e.touches[0].clientY - startY;
        const dx = Math.abs(e.touches[0].clientX - startX);
        // 30px 이상 아래로, 수직 방향으로 당길 때 인디케이터 표시
        if (dy > 30 && dy > dx * 1.5) {
            // 30px~80px 범위에서 0→1로 투명도를 선형 보간
            getIndicator().style.opacity = dy > 80 ? '1' : String((dy - 30) / 50);
        }
    }, { passive: true });

    document.addEventListener('touchend', e => {
        // 터치를 떼면 인디케이터 숨김
        if (indicator) indicator.style.opacity = '0';
        if (!atTop) return;
        const dy = e.changedTouches[0].clientY - startY;
        const dx = Math.abs(e.changedTouches[0].clientX - startX);
        // 80px 이상 수직으로 당겼을 때 새로고침 실행
        if (dy > 80 && dy > dx * 1.5) location.reload();
    }, { passive: true });
}

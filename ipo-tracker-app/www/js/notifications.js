/**
 * notifications.js — 공모주 푸시 알림 스케줄러
 *
 * scheduleIpoNotifications() 호출 시:
 *  - 청약 신청한 종목: 마감 D-1 오전 9시 알림
 *  - 배정받은 종목:   상장당일 오전 8시 30분 알림
 *
 * Capacitor LocalNotifications 플러그인 필요:
 *   npm install @capacitor/local-notifications && npx cap sync android
 */

/**
 * 체크리스트에 등록된 공모주 종목에 대한 로컬 푸시 알림을 스케줄링합니다.
 *
 * 알림 종류:
 *   1. 청약마감 D-1 알림 (ID 범위: 10000~19999)
 *      - 조건: 해당 종목에 applied=true인 계좌가 1개 이상 존재
 *      - 시각: 청약 마감일(subscriptionEndDate) 전날 오전 9시 정각
 *      - 미래 시각인 경우에만 등록 (이미 지난 알림은 건너뜀)
 *
 *   2. 상장일 아침 알림 (ID 범위: 20000~29999)
 *      - 조건: 해당 종목에 qty > 0인 계좌가 1개 이상 존재 (배정 완료)
 *      - 시각: 상장일(listingDate) 오전 8시 30분
 *      - 미래 시각인 경우에만 등록 (이미 지난 알림은 건너뜀)
 *
 * 실행 흐름:
 *   1. Capacitor 네이티브 플랫폼 여부 확인 (웹에서는 실행하지 않음)
 *   2. LocalNotifications 플러그인 존재 여부 확인
 *   3. 알림 권한 요청 (거부 시 종료)
 *   4. 기존에 등록된 알림(ID 10000~29999) 전체 취소
 *   5. IndexedDB에서 체크리스트 전체 로드
 *   6. 각 종목별 알림 조건 검사 후 알림 객체 배열 구성
 *   7. 알림 일괄 스케줄 등록
 *
 * @returns {Promise<void>}
 *   - Capacitor 환경이 아니거나, 플러그인 없거나, 권한 거부 시 조기 종료
 *   - 오류 발생 시 console.warn으로 기록 후 조용히 종료
 */
async function scheduleIpoNotifications() {
    // Capacitor 환경이 아니면 무시 (웹 브라우저 등)
    if (typeof Capacitor === 'undefined' || !Capacitor.isNativePlatform()) return;

    const lns = Capacitor.Plugins.LocalNotifications;
    if (!lns) return;

    // 알림 권한 요청: 사용자가 거부하거나 오류 발생 시 함수 종료
    const perm = await lns.requestPermissions().catch(() => null);
    if (!perm || perm.display !== 'granted') return;

    try {
        // 기존 앱 알림 취소 (ID 10000–29999 범위)
        // 체크리스트가 변경될 때마다 이전 알림을 모두 지우고 재등록합니다.
        const pending = await lns.getPending().catch(() => ({ notifications: [] }));
        const toCancel = (pending.notifications || []).filter(n => n.id >= 10000 && n.id <= 29999);
        if (toCancel.length) await lns.cancel({ notifications: toCancel }).catch(() => {});

        // 체크리스트 로드: db.js의 getAllChecklists() 사용
        const checklists = await getAllChecklists().catch(() => []);
        const now = Date.now();
        /** @type {Array<Object>} 등록할 알림 객체 배열 */
        const scheduled = [];

        checklists.forEach((item, idx) => {
            const accs = item.accounts || {};

            // ── 청약마감 D-1 알림 (신청한 계좌가 있는 경우) ──
            // accounts 객체의 values 중 applied=true인 것이 하나라도 있으면 알림 등록
            const hasApplied = Object.values(accs).some(a => a && a.applied);
            if (hasApplied && item.subscriptionEndDate) {
                // 알림 시각 계산: 마감일 전날 오전 9시
                const endDate   = new Date(item.subscriptionEndDate);
                const notifyAt  = new Date(endDate);
                notifyAt.setDate(notifyAt.getDate() - 1);
                notifyAt.setHours(9, 0, 0, 0);

                // 현재 시각 이후인 경우에만 스케줄 등록 (이미 지난 알림 제외)
                if (notifyAt.getTime() > now) {
                    scheduled.push({
                        id:         10000 + idx,   // 체크리스트 인덱스를 ID로 사용 (10000번대)
                        title:      '📅 청약마감 D-1',
                        body:       `${item.corpName} — 내일 청약 마감입니다!`,
                        schedule:   { at: notifyAt },
                        sound:      'default',
                        smallIcon:  'ic_stat_icon_config_sample',
                        iconColor:  '#0d6efd'
                    });
                }
            }

            // ── 상장일 아침 알림 (배정받은 계좌가 있는 경우) ──
            // accounts 객체의 values 중 qty > 0인 것이 하나라도 있으면 알림 등록
            const hasQty = Object.values(accs).some(a => a && a.qty > 0);
            if (hasQty && item.listingDate) {
                // 알림 시각 계산: 상장일 당일 오전 8시 30분
                const listAt = new Date(item.listingDate);
                listAt.setHours(8, 30, 0, 0);

                // 현재 시각 이후인 경우에만 스케줄 등록 (이미 상장한 종목 제외)
                if (listAt.getTime() > now) {
                    scheduled.push({
                        id:         20000 + idx,   // 체크리스트 인덱스를 ID로 사용 (20000번대)
                        title:      '🎉 오늘 상장!',
                        body:       `${item.corpName} — 오늘 상장합니다. 출금 확인하세요!`,
                        schedule:   { at: listAt },
                        sound:      'default',
                        smallIcon:  'ic_stat_icon_config_sample',
                        iconColor:  '#ffc107'
                    });
                }
            }
        });

        // 등록할 알림이 있을 때만 schedule API 호출
        if (scheduled.length) {
            await lns.schedule({ notifications: scheduled });
            console.log(`[알림] ${scheduled.length}개 스케줄 등록 완료`);
        }
    } catch (e) {
        console.warn('[알림] 스케줄 실패:', e);
    }
}

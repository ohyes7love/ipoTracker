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

async function scheduleIpoNotifications() {
    // Capacitor 환경이 아니면 무시 (웹 브라우저 등)
    if (typeof Capacitor === 'undefined' || !Capacitor.isNativePlatform()) return;

    const lns = Capacitor.Plugins.LocalNotifications;
    if (!lns) return;

    // 알림 권한 요청
    const perm = await lns.requestPermissions().catch(() => null);
    if (!perm || perm.display !== 'granted') return;

    try {
        // 기존 앱 알림 취소 (ID 10000–29999 범위)
        const pending = await lns.getPending().catch(() => ({ notifications: [] }));
        const toCancel = (pending.notifications || []).filter(n => n.id >= 10000 && n.id <= 29999);
        if (toCancel.length) await lns.cancel({ notifications: toCancel }).catch(() => {});

        // 체크리스트 로드
        const checklists = await getAllChecklists().catch(() => []);
        const now = Date.now();
        const scheduled = [];

        checklists.forEach((item, idx) => {
            const accs = item.accounts || {};

            // ── 청약마감 D-1 알림 (신청한 계좌가 있는 경우) ──
            const hasApplied = Object.values(accs).some(a => a && a.applied);
            if (hasApplied && item.subscriptionEndDate) {
                const endDate   = new Date(item.subscriptionEndDate);
                const notifyAt  = new Date(endDate);
                notifyAt.setDate(notifyAt.getDate() - 1);
                notifyAt.setHours(9, 0, 0, 0);

                if (notifyAt.getTime() > now) {
                    scheduled.push({
                        id:         10000 + idx,
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
            const hasQty = Object.values(accs).some(a => a && a.qty > 0);
            if (hasQty && item.listingDate) {
                const listAt = new Date(item.listingDate);
                listAt.setHours(8, 30, 0, 0);

                if (listAt.getTime() > now) {
                    scheduled.push({
                        id:         20000 + idx,
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

        if (scheduled.length) {
            await lns.schedule({ notifications: scheduled });
            console.log(`[알림] ${scheduled.length}개 스케줄 등록 완료`);
        }
    } catch (e) {
        console.warn('[알림] 스케줄 실패:', e);
    }
}

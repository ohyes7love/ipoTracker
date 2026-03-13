# 📈 공모주 수익 관리 (IPO Tracker)

공모주 청약 내역을 등록하고 수익을 관리하는 애플리케이션입니다.
**Spring Boot 웹 서버** 버전과 **Android APK (Capacitor)** 버전 두 가지로 제공됩니다.

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| **청약 내역 관리** | 공모주 청약 내역 등록/수정/삭제, 계좌별(경록/지선/하준/하민) 관리 |
| **수익 계산** | (매도가 - 공모가) × 배정수량 - 세금/수수료 - 청약수수료 자동 계산 |
| **공모주 캘린더** | kokstock.com 스크래핑 기반 월별 청약/상장 일정 캘린더 (월~금) |
| **종목 자동완성** | kokstock 일정 검색 + DB 저장 종목 통합 자동완성, 날짜/공모가 자동채움 |
| **증권사 통계** | 이달 공모주에 참여하는 증권사별 건수 집계 (계좌 개설 참고용) |
| **수익 통계** | 증권사×월 피벗 테이블, 계좌별 수익 현황 |
| **수수료 관리** | 증권사별 청약수수료 설정 |

---

## 아키텍처

### Spring Boot 버전 (`ipo-tracker/`)

```
┌─────────────────────────────────────────────────────────┐
│                     Browser (PC/Mobile)                  │
│   Thymeleaf HTML ──► REST API ──► Spring Boot Server     │
└──────────────────────────┬──────────────────────────────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
        MySQL (DB)   kokstock.com   (DART API)
        청약내역       공모일정        [비활성]
```

```
ipo-tracker/
├── controller/
│   ├── IpoApiController.java      # 청약 내역 CRUD REST API  (/api/ipo)
│   ├── DartApiController.java     # kokstock 스크래핑 API    (/api/dart)
│   ├── BrokerFeeApiController.java# 수수료 관리 API          (/api/broker-fee)
│   ├── StockPriceApiController.java# 현재가 조회 (KIS API)
│   ├── KrxApiController.java      # KRX 데이터
│   └── PageController.java        # Thymeleaf 페이지 라우팅
├── service/
│   ├── IpoService.java            # 청약 내역 비즈니스 로직
│   ├── IpoStockService.java       # kokstock.com 스크래퍼
│   ├── BrokerFeeService.java      # 수수료 서비스
│   ├── DartApiService.java        # DART 공시 API (현재 미사용)
│   └── KisApiService.java         # 한국투자증권 API
├── domain/                        # JPA 엔티티
├── repository/                    # MyBatis 리포지토리
├── dto/                           # DTO 클래스
└── resources/templates/           # Thymeleaf HTML 템플릿
    ├── index.html                 # 대시보드
    ├── ipo.html                   # 청약 내역
    ├── calendar.html              # 공모주 캘린더
    ├── stats.html                 # 통계
    └── broker-fees.html           # 수수료 관리
```

### Android APK 버전 (`ipo-tracker-app/`)

```
┌──────────────────────────────────────────────────────────┐
│                  Android App (Capacitor)                  │
│                                                          │
│  ┌─────────────────────────────────────────────────┐    │
│  │              WebView (www/)                      │    │
│  │  HTML/JS ──► IndexedDB ──► CapacitorHttp         │    │
│  └─────────────────┬────────────────────────────────┘    │
│                    │ CapacitorHttp (CORS 우회)            │
│                    ▼                                      │
│             kokstock.com                                  │
│           (EUC-KR 스크래핑)                               │
└──────────────────────────────────────────────────────────┘
```

```
ipo-tracker-app/www/
├── js/
│   ├── db.js          # IndexedDB CRUD (MySQL 대체)
│   └── kokstock.js    # kokstock.com 스크래퍼 (서버리스)
├── index.html         # 대시보드
├── ipo.html           # 청약 내역
├── calendar.html      # 공모주 캘린더
├── stats.html         # 통계
└── broker-fees.html   # 수수료 관리
```

---

## 데이터 흐름

### 공모주 캘린더 흐름

```
사용자가 캘린더 접근
        │
        ▼
해당 월 캐시 있음? ──Yes──► 캐시 데이터 렌더링
        │
       No
        ▼
kokstock.com/stock/ipo.asp       (청약일정)
kokstock.com/stock/ipo_listing.asp (상장일정)
        │
        ▼
종목명 기준으로 병합
        │
        ▼
캐시 저장 + 캘린더 렌더링 (월~금 5열 그리드)
        │
        ▼
종목 클릭 시 → kokstock.com/Ajax/popStockIPO.asp?I_IDX={idx}
               상세 정보 팝업 (공모가/경쟁률/증권사/비고)
```

### 청약 내역 자동완성 흐름

```
사용자 종목명 입력
        │
        ├──► DB 저장 종목 즉시 표시 (공모가 자동채움)
        │
        └──► kokstock 검색 (300ms debounce)
                    │
                    ▼
             청약일/상장일 + 확정공모가 자동채움
             (getKokstockDetail(idx) 호출)
```

### EUC-KR 인코딩 처리 (APK)

```
CapacitorHttp.request({ responseType: 'arraybuffer' })
        │
        ▼
Android: base64 문자열 반환
        │
        ▼
atob(base64) → Uint8Array
        │
        ▼
TextDecoder('euc-kr').decode(bytes) → 한글 정상 표시
```

---

## 기술 스택

### Spring Boot 버전
| 항목 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| DB | MySQL + MyBatis |
| HTML | Thymeleaf |
| HTTP Client | Apache HttpClient 5 |
| HTML Parser | Jsoup |

### Android APK 버전
| 항목 | 기술 |
|------|------|
| Framework | Capacitor 6 |
| 로컬 DB | IndexedDB (브라우저 내장) |
| HTTP | CapacitorHttp 플러그인 (CORS 우회) |
| 인코딩 | TextDecoder('euc-kr') |
| 빌드 | Android Studio / Gradle |

---

## 설치 및 실행

### Spring Boot

```bash
cd ipo-tracker
# application.properties 에 DB 설정 필요
./gradlew bootRun
# 접속: http://localhost:8080
```

### Android APK 빌드

```bash
cd ipo-tracker-app

# 1. 웹 자산 동기화
npx cap sync android

# 2. APK 빌드 (debug)
cd android
./gradlew assembleDebug

# 빌드 결과: android/app/build/outputs/apk/debug/app-debug.apk

# 또는 Android Studio에서 Build > Generate Signed Bundle/APK
```

---

## API 엔드포인트 (Spring Boot)

### 청약 내역 `/api/ipo`
| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/ipo?year=2026` | 연도별 청약 내역 |
| POST | `/api/ipo` | 청약 내역 등록 |
| PUT | `/api/ipo/{id}` | 청약 내역 수정 |
| DELETE | `/api/ipo/{id}` | 청약 내역 삭제 |
| GET | `/api/ipo/monthly?year=2026` | 월별 수익 집계 |

### kokstock 스크래핑 `/api/dart`
| Method | URL | 설명 |
|--------|-----|------|
| GET | `/api/dart/calendar?year=2026&month=3` | 월별 공모주 일정 |
| GET | `/api/dart/search?q=메쥬` | 종목명 자동완성 |
| GET | `/api/dart/ipo-detail?idx=611` | 종목 상세 |
| GET | `/api/dart/broker-stats?year=2026&month=3` | 증권사별 공모 건수 |
| POST | `/api/dart/refresh` | 캐시 강제 갱신 |

---

## 주의사항

- kokstock.com 스크래핑 기반이므로 사이트 구조 변경 시 파싱 로직 수정 필요
- DART API는 현재 비활성 (Connection reset 이슈로 kokstock으로 대체)
- APK의 증권사 공모 건수 조회는 종목 수만큼 HTTP 요청 발생 (느릴 수 있음)

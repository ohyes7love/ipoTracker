# APK 빌드 방법

## 1. 사전 설치 (최초 1회)

### Node.js 설치
https://nodejs.org → LTS 버전 다운로드 & 설치

### Android Studio 설치
https://developer.android.com/studio → 설치 후 SDK 셋업

### JAVA_HOME 환경변수 확인
Android Studio 설치 시 JDK 포함됨. 별도 설정 불필요.

---

## 2. Capacitor 초기화 (최초 1회)

```cmd
cd C:\Users\rok\git\ipoTracker\ipo-tracker-app

npm install

npx cap init "공모주 수익 관리" "com.ipo.tracker" --web-dir www

npx cap add android
```

---

## 3. APK 빌드

```cmd
cd C:\Users\rok\git\ipoTracker\ipo-tracker-app

npx cap sync android

npx cap open android
```

Android Studio가 열리면:
- 상단 메뉴 → **Build → Build Bundle(s) / APK(s) → Build APK(s)**
- 빌드 완료 후 **"locate"** 클릭 → APK 파일 위치 확인

APK 경로:
```
android\app\build\outputs\apk\debug\app-debug.apk
```

---

## 4. 폰에 설치

1. APK 파일을 카카오톡/이메일 등으로 폰에 전송
2. 폰에서 파일 탭 → APK 파일 실행
3. "알 수 없는 출처 앱 허용" → 설치

---

## 5. HTML 수정 후 재빌드

www/ 파일 수정 시:
```cmd
npx cap sync android
```
그 다음 Android Studio에서 다시 APK 빌드

---

## 6. DART API 키 설정 (자동완성용)

앱 실행 후 우측 상단 ⚙️ → DART API 키 입력
- 발급: https://opendart.fss.or.kr → 마이페이지 → API 키 신청

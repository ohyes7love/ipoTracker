/**
 * generate-icon.js
 * 공모주 앱 아이콘 PNG 자동 생성 스크립트
 * 사용: node generate-icon.js
 * 필요: npm install sharp
 */
const sharp = require('sharp');
const path  = require('path');
const fs    = require('fs');

// 생성할 사이즈 목록 (Android mipmap 폴더별)
const SIZES = [
    { dir: 'mipmap-mdpi',    size: 48  },
    { dir: 'mipmap-hdpi',    size: 72  },
    { dir: 'mipmap-xhdpi',   size: 96  },
    { dir: 'mipmap-xxhdpi',  size: 144 },
    { dir: 'mipmap-xxxhdpi', size: 192 },
];

const RES_DIR = path.join(__dirname, 'android/app/src/main/res');

// ── SVG 아이콘 정의 (1024×1024 기준) ──────────────────────
function buildSvg(size) {
    const s = size / 1024;
    const sc = n => Math.round(n * s * 10) / 10;

    return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%"   stop-color="#0a2260"/>
      <stop offset="100%" stop-color="#1565e8"/>
    </linearGradient>
  </defs>

  <!-- 배경 (둥근 사각형) -->
  <rect width="1024" height="1024" rx="230" ry="230" fill="url(#bg)"/>

  <!-- 기준선 -->
  <line x1="160" y1="790" x2="864" y2="790"
        stroke="rgba(255,255,255,0.35)" stroke-width="18" stroke-linecap="round"/>

  <!-- Bar 1 (낮음, 60% 흰색) -->
  <rect x="160" y="580" width="160" height="210" rx="32" ry="32"
        fill="rgba(255,255,255,0.60)"/>

  <!-- Bar 2 (중간, 80% 흰색) -->
  <rect x="432" y="400" width="160" height="390" rx="32" ry="32"
        fill="rgba(255,255,255,0.80)"/>

  <!-- Bar 3 (높음, 100% 흰색) -->
  <rect x="704" y="200" width="160" height="590" rx="32" ry="32"
        fill="rgba(255,255,255,1.00)"/>

  <!-- 골드 상승 추세선 -->
  <polyline points="240,580 512,400 784,200"
            fill="none" stroke="#FFD000" stroke-width="36"
            stroke-linecap="round" stroke-linejoin="round"/>

  <!-- 골드 피크 도트 -->
  <circle cx="784" cy="200" r="46" fill="#FFD000"/>

  <!-- 우상향 화살표 -->
  <polygon points="870,110 960,80 935,168" fill="#FFD000"/>
</svg>`;
}

async function generate() {
    console.log('아이콘 PNG 생성 중...');
    for (const { dir, size } of SIZES) {
        const outDir = path.join(RES_DIR, dir);
        if (!fs.existsSync(outDir)) { fs.mkdirSync(outDir, { recursive: true }); }

        const svg = Buffer.from(buildSvg(size));
        const outFile = path.join(outDir, 'ic_launcher.png');
        const outRound = path.join(outDir, 'ic_launcher_round.png');

        await sharp(svg).png().toFile(outFile);
        await sharp(svg).png().toFile(outRound);
        console.log(`  ✅ ${dir} (${size}×${size})`);
    }

    // foreground도 투명 배경으로 별도 생성
    for (const { dir, size } of SIZES) {
        const svg = buildSvg(size).replace(/url\(#bg\)/, '#00000000').replace(/<rect width="1024" height="1024"[^\/]*\/>/, '');
        const outFile = path.join(RES_DIR, dir, 'ic_launcher_foreground.png');
        // foreground는 XML이 있으므로 스킵해도 무방
    }

    console.log('\n완료! 다음을 실행하세요:');
    console.log('  npx cap sync android');
    console.log('  cd android && gradlew assembleDebug');
}

generate().catch(err => {
    if (err.code === 'MODULE_NOT_FOUND') {
        console.error('sharp가 없습니다. 먼저 설치하세요:');
        console.error('  npm install sharp');
    } else {
        console.error(err);
    }
});

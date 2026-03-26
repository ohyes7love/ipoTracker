/**
 * generate-icon.js
 * 공모주 앱 아이콘 PNG 자동 생성 스크립트 (로켓 디자인)
 * 사용: node generate-icon.js
 */
const sharp = require('sharp');
const path  = require('path');
const fs    = require('fs');

const SIZES = [
    { dir: 'mipmap-mdpi',    size: 48  },
    { dir: 'mipmap-hdpi',    size: 72  },
    { dir: 'mipmap-xhdpi',   size: 96  },
    { dir: 'mipmap-xxhdpi',  size: 144 },
    { dir: 'mipmap-xxxhdpi', size: 192 },
];

const RES_DIR = path.join(__dirname, 'android/app/src/main/res');

// ── 전체 아이콘 SVG (배경 포함) ───────────────────────────
function buildFullSvg(size) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%"   stop-color="#0a2260"/>
      <stop offset="100%" stop-color="#1565e8"/>
    </linearGradient>
  </defs>

  <!-- 배경 -->
  <rect width="1024" height="1024" rx="220" ry="220" fill="url(#bg)"/>

  <!-- 별 장식 -->
  <circle cx="210" cy="230" r="18" fill="white" opacity="0.75"/>
  <circle cx="800" cy="290" r="14" fill="white" opacity="0.55"/>
  <circle cx="165" cy="480" r="10" fill="white" opacity="0.45"/>
  <circle cx="845" cy="510" r="9"  fill="white" opacity="0.35"/>
  <circle cx="310" cy="155" r="13" fill="white" opacity="0.65"/>
  <circle cx="720" cy="160" r="9"  fill="white" opacity="0.50"/>

  <!-- 불꽃 (골드) -->
  <path d="M400,665 L624,665 Q582,830 512,930 Q442,830 400,665 Z" fill="#FFD000"/>
  <!-- 불꽃 안쪽 하이라이트 -->
  <path d="M445,665 L579,665 Q550,795 512,875 Q474,795 445,665 Z" fill="white" opacity="0.30"/>

  <!-- 로켓 몸체 (흰색) -->
  <path d="M388,665 L388,355 Q388,135 512,105 Q636,135 636,355 L636,665 Z" fill="white"/>

  <!-- 왼쪽 날개 -->
  <path d="M388,665 L285,825 L388,755 Z" fill="white"/>

  <!-- 오른쪽 날개 -->
  <path d="M636,665 L739,825 L636,755 Z" fill="white"/>

  <!-- 창문 (파란 원) -->
  <circle cx="512" cy="415" r="75" fill="#1262d4"/>
  <!-- 창문 광택 -->
  <circle cx="488" cy="392" r="22" fill="white" opacity="0.55"/>
</svg>`;
}

// ── foreground 전용 SVG (투명 배경) ──────────────────────
function buildFgSvg(size) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024">
  <circle cx="210" cy="230" r="18" fill="white" opacity="0.75"/>
  <circle cx="800" cy="290" r="14" fill="white" opacity="0.55"/>
  <circle cx="165" cy="480" r="10" fill="white" opacity="0.45"/>
  <circle cx="310" cy="155" r="13" fill="white" opacity="0.65"/>

  <path d="M400,665 L624,665 Q582,830 512,930 Q442,830 400,665 Z" fill="#FFD000"/>
  <path d="M445,665 L579,665 Q550,795 512,875 Q474,795 445,665 Z" fill="white" opacity="0.30"/>

  <path d="M388,665 L388,355 Q388,135 512,105 Q636,135 636,355 L636,665 Z" fill="white"/>
  <path d="M388,665 L285,825 L388,755 Z" fill="white"/>
  <path d="M636,665 L739,825 L636,755 Z" fill="white"/>

  <circle cx="512" cy="415" r="75" fill="#1262d4"/>
  <circle cx="488" cy="392" r="22" fill="white" opacity="0.55"/>
</svg>`;
}

async function generate() {
    console.log('🚀 로켓 아이콘 PNG 생성 중...\n');
    for (const { dir, size } of SIZES) {
        const outDir = path.join(RES_DIR, dir);
        if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

        const fullSvg = Buffer.from(buildFullSvg(size));
        await sharp(fullSvg).png().toFile(path.join(outDir, 'ic_launcher.png'));
        await sharp(fullSvg).png().toFile(path.join(outDir, 'ic_launcher_round.png'));

        const fgSvg = Buffer.from(buildFgSvg(size));
        await sharp(fgSvg).png().toFile(path.join(outDir, 'ic_launcher_foreground.png'));

        console.log(`  ✅ ${dir} (${size}px)`);
    }
    console.log('\n완료!');
}

generate().catch(err => {
    if (err.code === 'MODULE_NOT_FOUND') {
        console.error('sharp 없음. 먼저: npm install sharp');
    } else {
        console.error(err);
    }
});

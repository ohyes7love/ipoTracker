/**
 * generate-icon.js
 * 공모주 앱 아이콘 PNG 자동 생성 스크립트
 * 사용: node generate-icon.js
 * 필요: npm install sharp
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
  <rect width="1024" height="1024" rx="230" ry="230" fill="url(#bg)"/>
  <line x1="160" y1="790" x2="864" y2="790"
        stroke="rgba(255,255,255,0.35)" stroke-width="18" stroke-linecap="round"/>
  <rect x="160" y="580" width="160" height="210" rx="32" ry="32" fill="rgba(255,255,255,0.60)"/>
  <rect x="432" y="400" width="160" height="390" rx="32" ry="32" fill="rgba(255,255,255,0.80)"/>
  <rect x="704" y="200" width="160" height="590" rx="32" ry="32" fill="rgba(255,255,255,1.00)"/>
  <polyline points="240,580 512,400 784,200"
            fill="none" stroke="#FFD000" stroke-width="36"
            stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="784" cy="200" r="46" fill="#FFD000"/>
  <polygon points="870,110 960,80 935,168" fill="#FFD000"/>
</svg>`;
}

// ── foreground 전용 SVG (투명 배경) ──────────────────────
function buildFgSvg(size) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024">
  <line x1="160" y1="790" x2="864" y2="790"
        stroke="rgba(255,255,255,0.35)" stroke-width="18" stroke-linecap="round"/>
  <rect x="160" y="580" width="160" height="210" rx="32" ry="32" fill="rgba(255,255,255,0.60)"/>
  <rect x="432" y="400" width="160" height="390" rx="32" ry="32" fill="rgba(255,255,255,0.80)"/>
  <rect x="704" y="200" width="160" height="590" rx="32" ry="32" fill="rgba(255,255,255,1.00)"/>
  <polyline points="240,580 512,400 784,200"
            fill="none" stroke="#FFD000" stroke-width="36"
            stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="784" cy="200" r="46" fill="#FFD000"/>
  <polygon points="870,110 960,80 935,168" fill="#FFD000"/>
</svg>`;
}

async function generate() {
    console.log('아이콘 PNG 생성 중...\n');
    for (const { dir, size } of SIZES) {
        const outDir = path.join(RES_DIR, dir);
        if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

        // ic_launcher.png / ic_launcher_round.png (배경 포함)
        const fullSvg = Buffer.from(buildFullSvg(size));
        await sharp(fullSvg).png().toFile(path.join(outDir, 'ic_launcher.png'));
        await sharp(fullSvg).png().toFile(path.join(outDir, 'ic_launcher_round.png'));

        // ic_launcher_foreground.png (투명 배경)
        const fgSvg = Buffer.from(buildFgSvg(size));
        await sharp(fgSvg).png().toFile(path.join(outDir, 'ic_launcher_foreground.png'));

        console.log(`  ✅ ${dir} (${size}px) — launcher + round + foreground`);
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

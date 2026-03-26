/**
 * generate-icon.js
 * 공모주 앱 아이콘 PNG 자동 생성 스크립트
 * 디자인: 네이비 배경 + 반투명 주식 그래프 + 흰색 로켓 + 골드 불꽃
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

// 주식 그래프 우상향 꺾은선 (현실감 있게 등락 포함)
const GRAPH_POINTS = '60,820 140,760 195,800 280,710 340,745 420,640 480,670 555,565 615,600 695,500 760,535 840,435 920,390 980,345';
// 그래프 아래 채움 영역 (클로즈 패스용)
const GRAPH_FILL   = `60,820 ${GRAPH_POINTS.split(' ').slice(1).join(' ')} 980,920 60,920`;

function buildFullSvg(size) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%"   stop-color="#081d55"/>
      <stop offset="100%" stop-color="#1059d5"/>
    </linearGradient>
    <linearGradient id="graphFill" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%"   stop-color="white" stop-opacity="0.12"/>
      <stop offset="100%" stop-color="white" stop-opacity="0.00"/>
    </linearGradient>
  </defs>

  <!-- 배경 -->
  <rect width="1024" height="1024" rx="210" ry="210" fill="url(#bg)"/>

  <!-- 수평 그리드 라인 (극도로 은은하게) -->
  <line x1="60" y1="400" x2="980" y2="400" stroke="white" stroke-width="8" opacity="0.06"/>
  <line x1="60" y1="550" x2="980" y2="550" stroke="white" stroke-width="8" opacity="0.06"/>
  <line x1="60" y1="700" x2="980" y2="700" stroke="white" stroke-width="8" opacity="0.06"/>

  <!-- 그래프 아래 채움 (아주 은은하게) -->
  <polygon points="${GRAPH_FILL}" fill="url(#graphFill)"/>

  <!-- 주식 꺾은선 그래프 (반투명 흰색) -->
  <polyline points="${GRAPH_POINTS}"
            fill="none" stroke="white" stroke-width="22"
            stroke-linecap="round" stroke-linejoin="round"
            opacity="0.22"/>

  <!-- 별 -->
  <circle cx="175" cy="195" r="14" fill="white" opacity="0.70"/>
  <circle cx="820" cy="220" r="11" fill="white" opacity="0.55"/>
  <circle cx="130" cy="430" r="8"  fill="white" opacity="0.40"/>
  <circle cx="880" cy="460" r="7"  fill="white" opacity="0.35"/>
  <circle cx="290" cy="130" r="10" fill="white" opacity="0.60"/>

  <!-- 불꽃 (골드) -->
  <path d="M400,665 L624,665 Q585,840 512,940 Q439,840 400,665 Z" fill="#FFD000"/>
  <!-- 불꽃 안쪽 하이라이트 -->
  <path d="M447,665 L577,665 Q549,800 512,878 Q475,800 447,665 Z" fill="white" opacity="0.28"/>

  <!-- 로켓 몸체 -->
  <path d="M388,665 L388,355 Q388,135 512,105 Q636,135 636,355 L636,665 Z" fill="white"/>

  <!-- 왼쪽 날개 -->
  <path d="M388,665 L280,830 L388,758 Z" fill="white"/>

  <!-- 오른쪽 날개 -->
  <path d="M636,665 L744,830 L636,758 Z" fill="white"/>

  <!-- 창문 -->
  <circle cx="512" cy="418" r="76" fill="#1059d5"/>
  <!-- 창문 광택 -->
  <circle cx="487" cy="394" r="22" fill="white" opacity="0.52"/>
</svg>`;
}

function buildFgSvg(size) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 1024 1024">
  <defs>
    <linearGradient id="graphFill" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%"   stop-color="white" stop-opacity="0.12"/>
      <stop offset="100%" stop-color="white" stop-opacity="0.00"/>
    </linearGradient>
  </defs>

  <line x1="60" y1="400" x2="980" y2="400" stroke="white" stroke-width="8" opacity="0.06"/>
  <line x1="60" y1="550" x2="980" y2="550" stroke="white" stroke-width="8" opacity="0.06"/>
  <line x1="60" y1="700" x2="980" y2="700" stroke="white" stroke-width="8" opacity="0.06"/>

  <polygon points="${GRAPH_FILL}" fill="url(#graphFill)"/>
  <polyline points="${GRAPH_POINTS}"
            fill="none" stroke="white" stroke-width="22"
            stroke-linecap="round" stroke-linejoin="round"
            opacity="0.22"/>

  <circle cx="175" cy="195" r="14" fill="white" opacity="0.70"/>
  <circle cx="820" cy="220" r="11" fill="white" opacity="0.55"/>
  <circle cx="290" cy="130" r="10" fill="white" opacity="0.60"/>

  <path d="M400,665 L624,665 Q585,840 512,940 Q439,840 400,665 Z" fill="#FFD000"/>
  <path d="M447,665 L577,665 Q549,800 512,878 Q475,800 447,665 Z" fill="white" opacity="0.28"/>

  <path d="M388,665 L388,355 Q388,135 512,105 Q636,135 636,355 L636,665 Z" fill="white"/>
  <path d="M388,665 L280,830 L388,758 Z" fill="white"/>
  <path d="M636,665 L744,830 L636,758 Z" fill="white"/>

  <circle cx="512" cy="418" r="76" fill="#1059d5"/>
  <circle cx="487" cy="394" r="22" fill="white" opacity="0.52"/>
</svg>`;
}

async function generate() {
    console.log('🚀 아이콘 생성 중 (로켓 + 주식 그래프)...\n');
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
    } else { console.error(err); }
});

// Monta os scripts que o app injeta no jogo:
//  · nucleo.js — o núcleo do Idleterino do PC (a função que roda na página do jogo, extraída do poke-preload.js);
//  · painel.js — o painel do celular, com o QR e o copia e cola do Pix embutidos.
// Uso: node scripts/gerar-assets.js
const fs = require('fs');
const path = require('path');
const raiz = path.join(__dirname, '..');
const pc = path.join(raiz, '..', 'idleterino');
const destino = path.join(raiz, 'app', 'src', 'main', 'assets');
fs.mkdirSync(destino, { recursive: true });

const pre = fs.readFileSync(path.join(pc, 'poke-preload.js'), 'utf8').replace(/\r\n/g, '\n');
const ini = pre.indexOf('func: () => {');
const fim = pre.lastIndexOf('  },\n});');
if (ini < 0 || fim < 0) throw new Error('não achei a função do núcleo no poke-preload.js');
const corpo = pre.slice(ini + 'func: () => {'.length, fim);
fs.writeFileSync(path.join(destino, 'nucleo.js'), `// GERADO de idleterino/poke-preload.js por scripts/gerar-assets.js — não editar aqui.\n(function () {${corpo}})();\n`);

const pix = fs.readFileSync(path.join(pc, 'renderer', 'apoio.js'), 'utf8');
const chave = /PIX_CHAVE = '([^']+)'/.exec(pix)[1];
const copia = /PIX_COPIA_COLA = '([^']+)'/.exec(pix)[1];
const svg = fs.readFileSync(path.join(pc, 'renderer', 'pix-qr.svg'), 'utf8').trim();
const painel = fs.readFileSync(path.join(raiz, 'web', 'painel.src.js'), 'utf8')
  .replace('__PIX_SVG__', JSON.stringify(svg)).replace('__PIX_COPIA__', JSON.stringify(copia)).replace('__PIX_CHAVE__', JSON.stringify(chave));
fs.writeFileSync(path.join(destino, 'painel.js'), painel);
console.log('nucleo.js', corpo.length, 'bytes · painel.js', painel.length, 'bytes');

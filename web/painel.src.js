// Idleterino no celular — o painel dentro do jogo (botão "I" flutuante). Lê o núcleo do Idleterino
// (window.__idleterinoMercado, o mesmo do PC) e a sessão que o próprio jogo grava. Só lê e mostra; o app (IdleterinoApp)
// cuida do modo farm, dos avisos e de copiar.
(function () {
  if (!/(^|\.)pokeidle\.io$/.test(location.hostname) || window.__idleterinoPainel) return;
  window.__idleterinoPainel = true;
  // Ponte com o app: só existe em https://pokeidle.io (trava de origem do Android) e só por MENSAGENS — o app nunca
  // devolve o código de acesso das contas para a página.
  const PORTA = window.IdleterinoApp && typeof window.IdleterinoApp.postMessage === 'function' ? window.IdleterinoApp : null;
  const APP = !!PORTA;
  let seq = 0;
  const esperando = new Map();
  if (PORTA) PORTA.onmessage = (ev) => { try { const r = JSON.parse(ev.data); const f = esperando.get(r.id); if (f) { esperando.delete(r.id); f(r.valor); } } catch {} };
  const chamar = (acao, dados) => new Promise((res) => {
    if (!PORTA) return res(null);
    const id = ++seq; esperando.set(id, res);
    PORTA.postMessage(JSON.stringify({ id, acao, dados: dados || {} }));
    setTimeout(() => { if (esperando.has(id)) { esperando.delete(id); res(null); } }, 6000);
  });
  const PIX_SVG = __PIX_SVG__;
  const PIX_COPIA = __PIX_COPIA__;
  const PIX_CHAVE = __PIX_CHAVE__;
  const LS = (k, d) => { try { const v = localStorage.getItem('idleterino:m:' + k); return v == null ? d : JSON.parse(v); } catch { return d; } };
  const LSset = (k, v) => { try { localStorage.setItem('idleterino:m:' + k, JSON.stringify(v)); } catch {} };
  const M = () => window.__idleterinoMercado || null;
  const nf = (n) => Number(n || 0).toLocaleString('pt-BR');
  const curto = (n) => { const v = Number(n) || 0, a = Math.abs(v); return a >= 1e9 ? (v / 1e9).toFixed(2).replace('.', ',') + ' bi' : a >= 1e6 ? (v / 1e6).toFixed(1).replace('.', ',') + ' mi' : a >= 1e4 ? Math.round(v / 1e3) + ' mil' : nf(v); };
  const dur = (h) => (h < 1 ? Math.max(1, Math.round(h * 60)) + ' min' : h < 48 ? (h < 10 ? h.toFixed(1).replace('.', ',') : Math.round(h)) + ' h' : h < 24 * 60 ? Math.round(h / 24) + ' dias' : (h / 720).toFixed(1).replace('.', ',') + ' meses');
  const TIPO_PT = { NORMAL: 'Normal', FIRE: 'Fogo', WATER: 'Água', GRASS: 'Planta', ELECTRIC: 'Elétrico', ICE: 'Gelo', FIGHTING: 'Lutador', POISON: 'Veneno', GROUND: 'Terra', FLYING: 'Voador', PSYCHIC: 'Psíquico', BUG: 'Inseto', ROCK: 'Pedra', GHOST: 'Fantasma', DRAGON: 'Dragão', DARK: 'Sombrio', STEEL: 'Aço', FAIRY: 'Fada' };
  const tipo = (t) => TIPO_PT[String(t).toUpperCase()] || t;
  const BONUS_PT = { vip: 'VIP', boostXp: 'XP Boost', boostPokexp: 'XP Boost Pokémon', guildBoost: 'Boost da Guild', guild: 'Ranking da guild', twitch: 'Twitch', kick: 'Kick', atrasados: 'Atrasados', sacrificio: 'Sacrifício', evento: 'Evento', raid: 'Raid Gigantamax' };
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

  function sessao() {
    const t = M() && M().treinador();
    if (!t) return null;
    try {
      const o = JSON.parse(localStorage.getItem('sessao-hunt:' + String(t.nick).toLowerCase()) || 'null');
      if (!o || !(o.ms > 0)) return null;
      const h = o.ms / 3600000;
      return { ms: o.ms, xpH: (+o.xpTreinador || 0) / h, ouroH: (+o.gold || 0) / h, abH: (+o.abates || 0) / h, capH: (+o.capturas || 0) / h, cap: +o.capturas || 0, shV: +o.shiniesVistos || 0, shC: +o.shinies || 0 };
    } catch { return null; }
  }
  function previsaoShiny(s) {
    if (!s || !(s.abH > 0)) return null;
    const x = (M() && M().shinyInfo && M().shinyInfo()) || {};
    const agora = Date.now();
    const chance = x.chance > 0 ? x.chance : 1 / 24000;
    const mult = (x.lure > agora ? 2 : 1) * (x.kick > agora ? 1.15 : 1) * (x.twitch > agora ? 1.15 : 1);
    return { ver: dur(1 / (s.abH * chance * mult)), cap: s.capH > 0 ? dur(1 / (s.capH * chance * mult)) : null, fecha: s.capH > 0 ? (s.capH / s.abH) * 100 : 0, lure: x.lure > agora };
  }

  // ---------- interface (Shadow DOM: o CSS do jogo não mexe no nosso, nem o nosso no dele) ----------
  const host = document.createElement('div');
  host.style.cssText = 'position:fixed;z-index:2147483646;left:0;top:0;width:0;height:0';
  const raiz = host.attachShadow({ mode: 'open' });
  raiz.innerHTML = `<style>
    :host{all:initial}
    *{box-sizing:border-box;font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
    .bt{position:fixed;width:48px;height:48px;border-radius:15px;border:0;color:#fff;font:900 22px/48px system-ui;text-align:center;
      background:linear-gradient(135deg,#ff5cd6,#9b7bff 55%,#33d6ff);box-shadow:0 6px 20px rgba(0,0,0,.45);touch-action:none;user-select:none}
    .pn{position:fixed;inset:0;background:rgba(8,7,16,.97);color:#f1eefc;display:none;flex-direction:column}
    .pn.on{display:flex}
    .top{display:flex;align-items:center;gap:10px;padding:12px 14px;border-bottom:1px solid rgba(255,255,255,.1)}
    .top b{font-size:18px} .top .v{color:#a9a2d4;font-size:12px} .top .x{margin-left:auto;background:none;border:0;color:#fff;font-size:26px;padding:0 6px}
    .tabs{display:flex;gap:6px;overflow-x:auto;padding:10px 12px;border-bottom:1px solid rgba(255,255,255,.08)}
    .tabs button{flex:none;border:1px solid rgba(255,255,255,.14);background:rgba(255,255,255,.05);color:#e8e4ff;border-radius:999px;padding:7px 13px;font-size:13px;font-weight:700}
    .tabs button.on{background:#f1eefc;color:#141024}
    .cp{flex:1;overflow-y:auto;padding:14px;display:grid;gap:12px;align-content:start}
    .card{background:rgba(255,255,255,.05);border:1px solid rgba(255,255,255,.1);border-radius:14px;padding:12px 14px;display:grid;gap:8px}
    .t{font-size:11px;letter-spacing:.06em;text-transform:uppercase;color:#a9a2d4;font-weight:800}
    .g{display:grid;grid-template-columns:1fr 1fr;gap:8px}
    .k{background:rgba(255,255,255,.05);border-radius:10px;padding:8px 10px}
    .k b{display:block;font-size:17px} .k span{font-size:11px;color:#a9a2d4}
    .sub{font-size:12px;color:#b7b0dd;line-height:1.45}
    .bar{height:7px;border-radius:7px;background:rgba(255,255,255,.1);overflow:hidden}.bar i{display:block;height:100%;background:linear-gradient(90deg,#ff4d6d,#a970ff,#53fc18)}
    .ln{display:flex;justify-content:space-between;gap:10px;font-size:13px;padding:6px 0;border-bottom:1px solid rgba(255,255,255,.06)}
    .ln:last-child{border-bottom:0} .ln b{font-weight:700} .ok{color:#7df0b0} .gold{color:#ffd166}
    .sw{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:14px}
    .sw input{width:44px;height:24px}
    .btn{border:0;border-radius:10px;padding:10px 14px;font-weight:800;font-size:14px;background:#f1eefc;color:#141024}
    .btn.g2{background:rgba(255,255,255,.08);color:#f1eefc;border:1px solid rgba(255,255,255,.15)}
    .qr{background:#fff;border-radius:14px;padding:10px;width:220px;height:220px;margin:0 auto}.qr svg{width:100%;height:100%}
    .tag{display:inline-block;font-size:11px;font-weight:800;padding:2px 8px;border-radius:999px;background:rgba(255,255,255,.08)}
    select,input.in{width:100%;background:#1c1733;color:#f1eefc;border:1px solid rgba(255,255,255,.18);border-radius:10px;padding:10px;font-size:14px}
    .rec{border-left:3px solid #7df0b0;padding:4px 0 4px 10px;font-size:13px;line-height:1.45}.rec b{display:block;margin-bottom:2px}
    .rec.r2{border-color:#9b7bff}.rec.r3{border-color:#ffd166}
    .iv{display:inline-block;font-size:11px;font-weight:800;padding:2px 7px;border-radius:7px;margin:2px;background:rgba(255,255,255,.08)}
    .sub2{display:flex;gap:6px;flex-wrap:wrap}.sub2 button{border:1px solid rgba(255,255,255,.14);background:rgba(255,255,255,.05);color:#e8e4ff;border-radius:9px;padding:6px 11px;font-size:12px;font-weight:700}
    .sub2 button.on{background:#9b7bff;border-color:#9b7bff;color:#fff}
    .tier{display:inline-block;min-width:22px;text-align:center;font-weight:900;border-radius:6px;padding:1px 6px;color:#141024;font-size:12px}
    .chip{display:inline-block;font-size:11px;padding:1px 7px;border-radius:999px;background:rgba(255,255,255,.08);margin:1px}
    .chip.ok{background:rgba(125,240,176,.18);color:#7df0b0}.bad{color:#ff8fa3}.meh{color:#ffd166}
    .iv.alto{background:rgba(125,240,176,.18);color:#7df0b0}.iv.baixo{background:rgba(255,77,109,.18);color:#ff8fa3}
  </style>
  <button class="bt" id="bt">I</button>
  <div class="pn" id="pn"><div class="top"><b>Idleterino</b><span class="v" id="ver"></span><button class="x" id="fechar">×</button></div>
  <div class="tabs" id="tabs"></div><div class="cp" id="cp"></div></div>`;
  const $ = (s) => raiz.querySelector(s);
  const pos = LS('pos', { x: 12, y: 160 });
  const bt = $('#bt');
  const posicionar = () => { bt.style.left = Math.min(innerWidth - 52, Math.max(4, pos.x)) + 'px'; bt.style.top = Math.min(innerHeight - 52, Math.max(4, pos.y)) + 'px'; };
  posicionar(); addEventListener('resize', posicionar);
  // Arrastar o botão (toque); toque curto abre o painel.
  let arr = null;
  bt.addEventListener('pointerdown', (e) => { arr = { x: e.clientX, y: e.clientY, px: pos.x, py: pos.y, moveu: false }; bt.setPointerCapture(e.pointerId); });
  bt.addEventListener('pointermove', (e) => { if (!arr) return; const dx = e.clientX - arr.x, dy = e.clientY - arr.y; if (Math.abs(dx) + Math.abs(dy) > 8) arr.moveu = true; if (arr.moveu) { pos.x = arr.px + dx; pos.y = arr.py + dy; posicionar(); } });
  bt.addEventListener('pointerup', () => { if (arr && !arr.moveu) abrir(); else LSset('pos', pos); arr = null; });

  let aba = LS('aba', 'inicio');
  const ABAS = [['inicio', 'Início'], ['hunt', 'Melhor hunt'], ['prox', 'Próximas'], ['evo', 'Evoluir'], ['comprar', 'Comprar'], ['pvp', 'PvP'], ['contas', 'Contas'], ['ajustes', 'Ajustes'], ['apoio', '❤ Apoiar']];
  let timer = null;
  // Atualização (igual ao PC): o app baixa sozinho; aqui aparece a faixa com "Atualizar agora".
  let att = null;
  const verAtt = async () => { att = await chamar('atualizacao'); bt.style.boxShadow = att && att.progresso === 100 ? '0 0 0 3px #53fc18, 0 6px 20px rgba(0,0,0,.45)' : ''; };
  setTimeout(verAtt, 15000); setInterval(verAtt, 10 * 60000);
  function faixaAtt() {
    if (!att || !att.versao) return '';
    if (att.progresso === 100) return `<div class="card" style="border-color:#53fc18"><div class="t">⬆ Atualização ${esc(att.versao)} pronta</div><div class="sub">Já foi baixada. O app fecha e abre de novo na versão nova; as contas continuam salvas.</div><button class="btn" id="attAgora">Atualizar agora</button></div>`;
    if (att.progresso >= 0) return `<div class="card"><div class="sub">⬇ Baixando a versão ${esc(att.versao)}… ${att.progresso}%</div></div>`;
    return `<div class="card"><div class="t">⬆ Versão ${esc(att.versao)} disponível</div><button class="btn" id="attAgora">Baixar e atualizar</button></div>`;
  }
  function abrir() { verAtt(); $('#pn').classList.add('on'); bt.style.display = 'none'; desenhar(); clearInterval(timer); timer = setInterval(() => { if (aba === 'inicio' || (aba === 'pvp' && pvpRodando)) desenhar(); }, 5000); }
  function fechar() { $('#pn').classList.remove('on'); bt.style.display = ''; clearInterval(timer); }
  $('#fechar').onclick = fechar;
  chamar('versao').then((v) => { if (v) $('#ver').textContent = 'v' + v; });

  async function desenhar() {
    $('#tabs').innerHTML = ABAS.map(([k, l]) => `<button data-a="${k}" class="${aba === k ? 'on' : ''}">${l}</button>`).join('');
    raiz.querySelectorAll('#tabs button').forEach((b) => { b.onclick = () => { aba = b.dataset.a; LSset('aba', aba); desenhar(); }; });
    const cp = $('#cp');
    const m = M();
    if (!m) { cp.innerHTML = '<div class="card"><div class="sub">Carregando o jogo…</div></div>'; return; }
    if (aba === 'inicio') { cp.innerHTML = faixaAtt() + htmlInicio(); const ag = $('#attAgora'); if (ag) ag.onclick = () => chamar('atualizarAgora'); }
    else if (aba === 'hunt') { cp.innerHTML = '<div class="card"><div class="sub">Calculando a melhor hunt…</div></div>'; cp.innerHTML = await htmlHunt(); }
    else if (aba === 'prox') { cp.innerHTML = '<div class="card"><div class="sub">Lendo as próximas hunts…</div></div>'; cp.innerHTML = await htmlProx(); }
    else if (aba === 'evo') { cp.innerHTML = '<div class="card"><div class="sub">Lendo os seus pokémon…</div></div>'; cp.innerHTML = await htmlEvo(); ligarEvo(); }
    else if (aba === 'comprar') { cp.innerHTML = htmlComprar(); ligarComprar(); }
    else if (aba === 'pvp') { if (!pvpRodando) cp.innerHTML = '<div class="card"><div class="sub">Lendo o PvP…</div></div>'; const h = await htmlPvp(); if (aba === 'pvp') { cp.innerHTML = h; ligarPvp(); } }
    else if (aba === 'ajustes') { cp.innerHTML = htmlAjustes(await chamar('farmLigado')); ligarAjustes(); }
    else if (aba === 'contas') { cp.innerHTML = htmlContas((await chamar('contas')) || []); ligarContas(); }
    else { cp.innerHTML = htmlApoio(); ligarApoio(); }
  }

  function htmlInicio() {
    const t = M().treinador();
    if (!t) return '<div class="card"><div class="sub">Faça login no jogo: o painel aparece quando a conta conectar.</div></div>';
    const pct = Math.min(100, (t.dentro / t.faixa) * 100);
    const s = sessao();
    const ps = previsaoShiny(s);
    const bx = M().bonusXp && M().bonusXp();
    let h = `<div class="card"><div class="t">Treinador</div><div><b style="font-size:18px">${esc(t.nick)}</b> · Nv ${nf(t.level)}${t.guildTag ? ` · [${esc(t.guildTag)}]` : ''}</div>
      <div class="bar"><i style="width:${pct}%"></i></div><div class="sub">${nf(t.dentro)} / ${nf(t.faixa)} xp (${pct.toFixed(1).replace('.', ',')}%) · ouro ${curto(t.gold)}</div></div>`;
    if (s) h += `<div class="card"><div class="t">Sessão de farm</div><div class="g">
      <div class="k"><b>${curto(s.xpH)}</b><span>XP/h</span></div><div class="k"><b class="gold">${curto(s.ouroH)}</b><span>ouro/h</span></div>
      <div class="k"><b>${nf(Math.round(s.abH))}</b><span>abates/h</span></div><div class="k"><b>${nf(s.cap)}</b><span>capturas · ${nf(Math.round(s.capH))}/h</span></div></div>
      <div class="sub">✨ Shinys na sessão: <b>${s.shV}</b> vistos · <b>${s.shC}</b> capturados${ps ? `<br>Previsão: ver ~1 a cada ${ps.ver} · capturar ${ps.cap ? '~1 a cada ' + ps.cap : '— (sem capturas na sessão)'}${!ps.lure && ps.cap ? ' · com Lure, metade' : ''}<br>A bola fecha em ${ps.fecha.toFixed(2).replace('.', ',')}% dos abates: o shiny só é seu se ela fechar.` : ''}</div></div>`;
    else h += '<div class="card"><div class="sub">O placar da sessão aparece quando a conta estiver caçando.</div></div>';
    if (bx && bx.partes && bx.partes.length) h += `<div class="card"><div class="t">Bônus de XP agora · ×${String(bx.tr).replace('.', ',')}</div>${bx.partes.map((p) => `<div class="ln"><span>${esc(BONUS_PT[p.id] || p.id)}</span><b>×${String(p.tr).replace('.', ',')}${p.ate ? ` · até ${new Date(p.ate).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}` : ''}</b></div>`).join('')}</div>`;
    return h;
  }

  async function htmlHunt() {
    const r = await M().huntRank();
    if (!r || !r.pronto) return '<div class="card"><div class="sub">Ainda lendo o jogo. Tente de novo em instantes.</div></div>';
    if (r.semPokemon) return '<div class="card"><div class="sub">Coloque um pokémon em campo para ver a melhor hunt.</div></div>';
    const linha = (h, k) => `<div class="ln"><span><b>${esc(h.nome)}</b> Nv ${nf(h.nivel)}<br><span class="sub">${h.seg != null ? `mata em ${String(h.seg).replace('.', ',')} s · ` : ''}~${nf(h.kph)} abates/h${h.slug && h.slug === r.atual ? ' · <span class="tag">atual</span>' : ''}</span></span><b class="${k === 'ouro' ? 'gold' : 'ok'}">${curto(k === 'ouro' ? h.goldLiq : h.xpH)}/h</b></div>`;
    return `<div class="card"><div class="t">⚡ Mais XP · ${esc(r.pokemon)}${r.aoe ? ' · AoE' : ''}${r.bike ? ' · bike ' + esc(r.bike) : ''}</div>${r.top.slice(0, 6).map((h) => linha(h, 'xp')).join('')}</div>
      <div class="card"><div class="t">💰 Mais ouro</div>${(r.topOuro || []).slice(0, 4).map((h) => linha(h, 'ouro')).join('')}</div>
      <div class="sub">Cálculo do Idleterino com os stats e golpes reais do seu pokémon contra cada selvagem, a efetividade da hunt, os seus bônus e o Dia Sazonal${r.tipoDia ? ` (hoje: ${esc(tipo(r.tipoDia))})` : ''}.</div>`;
  }

  async function htmlProx() {
    const r = await M().pve('proximas');
    if (!r || !r.pronto) return '<div class="card"><div class="sub">Ainda lendo o jogo.</div></div>';
    if (!r.grupos || !r.grupos.length) return '<div class="card"><div class="sub">Você já liberou todas as hunts.</div></div>';
    return r.grupos.map((g) => `<div class="card"><div class="t">Nv ${nf(g.nivel)}</div>${g.hunts.slice(0, 8).map((h) => `<div class="ln"><span><b>${h.doDia ? '💪 ' : ''}${esc(h.nome)}</b><br><span class="sub">${esc((h.tipos || []).map(tipo).join(' / '))}${h.ataques && h.ataques.length ? ' · fraco a ' + esc(h.ataques.map((a) => tipo(a.t)).join(', ')) : ''}</span></span><b>${h.meu ? `${esc(h.meu.nome)} ×${String(h.meu.x).replace('.', ',')}` : '—'}</b></div>`).join('')}</div>`).join('');
  }

  // ---------- Evoluir para qual? (os IVs do SEU pokémon em cada forma, nas hunts da sua região e da próxima) ----------
  let evoSel = LS('evoSel', null);
  const vir = (x) => String(x).replace('.', ',');
  async function htmlEvo() {
    const meus = await M().pve('meus');
    if (!Array.isArray(meus) || !meus.length) return '<div class="card"><div class="sub">A lista aparece quando a conta estiver logada e o jogo carregado.</div></div>';
    const chave = (m) => `${m.speciesId}|${m.id == null ? '' : m.id}`;
    let h = `<div class="card"><div class="t">Evoluir para qual?</div><div class="sub">Escolha um pokémon seu: o app usa os IVs dele e compara todas as formas (e as Megas) nas hunts da região em que você está e da próxima.</div>
      <select id="evoSel"><option value="">— escolha —</option>${meus.map((m) => `<option value="${esc(chave(m))}" ${evoSel && evoSel.k === chave(m) ? 'selected' : ''}>${m.shiny ? '✨ ' : ''}${esc(m.nome)} · Nv ${nf(m.level)}</option>`).join('')}</select></div>`;
    if (!evoSel) return h;
    const r = await M().pve('evolucoes', { speciesId: evoSel.sp, pkId: evoSel.pk, meta: [] });
    if (!r || r.erro || !r.base) return h + `<div class="card"><div class="sub">${esc((r && r.erro) || 'Não deu para calcular agora. Tente de novo em instantes.')}</div></div>`;
    const todas = [];
    for (const o of r.opcoes || []) { todas.push({ ...o.final }); for (const m of o.megas || []) todas.push({ ...m, deMega: o.final.nome }); }
    const ind = !!r.ind;
    for (const x of todas) x.farm = (x.sc || 0) * (ind && x.ataqueReal ? x.ataqueReal : x.ataque);
    const melhor = (f) => todas.reduce((b, x) => (!b || f(x) > f(b) ? x : b), null);
    const mFarm = melhor((x) => x.farm), mRes = ind ? melhor((x) => x.resist || 0) : melhor((x) => x.bst), mEnc = ind ? melhor((x) => (x.encaixe == null ? -999 : x.encaixe)) : null;
    const regioes = (r.regioes || []).join(' e ') || 'suas regiões';
    const ST = { hp: 'HP', atk: 'ATK', def: 'DEF', spAtk: 'SP.ATK', spDef: 'SP.DEF', speed: 'SPD' };
    let c = `<div class="card"><div style="font-size:18px;font-weight:900">${esc(r.base.nome)}</div><div class="sub">${esc((r.base.tipos || []).map(tipo).join(' / '))}</div>`;
    if (ind) { const iv = r.ind.ivs || {}; c += `<div>${Object.keys(ST).map((k) => `<span class="iv ${iv[k] >= 24 ? 'alto' : iv[k] <= 10 ? 'baixo' : ''}">${ST[k]} ${iv[k] == null ? '—' : iv[k]}</span>`).join('')}</div><div class="sub">Os IVs não mudam ao evoluir: os números abaixo são os stats de verdade DESTE pokémon em cada forma (no Nv ${nf(r.ind.nivelUso)}).</div>`; }
    const semEvo = !todas.length || (r.opcoes.length === 1 && !r.opcoes[0].passos.length && !r.opcoes[0].megas.length);
    if (semEvo) c += '<div class="sub">Esta espécie não evolui nem tem Mega.</div>';
    else {
      if (mFarm) {
        const s2 = todas.filter((x) => x !== mFarm).reduce((b, x) => (!b || x.farm > b.farm ? x : b), null);
        c += `<div class="rec"><b>🌾 Para farmar no PvE → ${esc(mFarm.nome)}${mFarm.deMega ? ' (Mega)' : ''}</b>Tira ×5,5 em ${mFarm.x55} e ×2,5 em ${mFarm.x25} das ${nf(r.huntsAlvo || 0)} hunts de ${esc(regioes)}, com ${mFarm.fisico ? 'ATK' : 'SP.ATK'} ${ind && mFarm.ataqueReal ? 'real ' + nf(mFarm.ataqueReal) : 'base ' + nf(mFarm.ataque)}.${s2 && s2.farm > 0 ? ` A 2ª opção (${esc(s2.nome)}) rende ~${Math.max(0, Math.round((mFarm.farm / s2.farm - 1) * 100))}% menos.` : ''}</div>`;
      }
      if (mRes) c += `<div class="rec r2"><b>🛡 ${ind ? 'Mais resistente' : 'Mais forte'} → ${esc(mRes.nome)}</b>${ind ? `resistência ${nf(mRes.resist)}` : `status ${nf(mRes.bst)}`}</div>`;
      if (mEnc) c += `<div class="rec r3"><b>🎯 Combina com os IVs → ${esc(mEnc.nome)}</b>A forma em que os IVs altos dele caem nos stats fortes dela.</div>`;
    }
    c += '</div>';
    for (const o of r.opcoes || []) {
      if (!o.passos.length && !o.megas.length) continue;
      const cam = o.passos.map((p) => `${esc(p.para)} (Nv ${nf(p.nivel)}${p.pedra ? ' · ' + esc(p.pedra) : ''})`).join(' → ');
      const linha = (x, mega) => { const y = todas.find((t) => t.pokeId === x.pokeId && !!t.deMega === mega) || x; return `<div class="ln"><span><b>${mega ? '💎 ' : ''}${esc(y.nome)}</b>${y === mFarm ? ' <span class="tag">🌾 farm</span>' : ''}<br><span class="sub">${esc((y.tipos || []).map(tipo).join(' / '))} · ${ind && y.real ? `${y.fisico ? 'ATK' : 'SP.ATK'} real ${nf(y.ataqueReal)} · resist. ${nf(y.resist)}` : `status ${nf(y.bst)} · ${y.fisico ? 'ATK' : 'SP.ATK'} ${nf(y.ataque)}`}${mega ? ' · precisa da Mega Stone' : ''}</span></span><b class="ok">${y.x55}×5,5<br><span class="sub">${y.x25}×2,5</span></b></div>`; };
      c += `<div class="card">${cam ? `<div class="sub">${esc(r.base.nome)} → ${cam}</div>` : ''}${linha(o.final, false)}${(o.megas || []).map((m) => linha(m, true)).join('')}</div>`;
    }
    c += '<div class="sub">×5,5 / ×2,5 = em quantas hunts a forma bate super efetivo. Nível e pedra vêm das regras do jogo. A escolha para o PvP (com os times do top 50) fica no PC: PvE › Evoluir.</div>';
    return h + c;
  }
  function ligarEvo() {
    const sel = $('#evoSel');
    if (sel) sel.onchange = () => { const [sp, pk] = sel.value.split('|'); evoSel = sel.value ? { k: sel.value, sp: Number(sp), pk: pk === '' ? null : Number(pk) } : null; LSset('evoSel', evoSel); desenhar(); };
  }

  // ---------- Comprar no Market para farmar (com o seu orçamento) ----------
  let busca = null;
  function htmlComprar() {
    const f = LS('compra', { valor: '', moeda: 'gold', dias: 30 });
    let h = `<div class="card"><div class="t">Comprar no Market para farmar</div>
      <div class="sub">Diga quanto quer gastar e por quanto tempo vai ficar com ele. O app procura AGORA as ofertas mais baratas dos pokémon que mais rendem nas hunts da sua região e da próxima, só no nível que a sua conta pode usar (o seu + 5).</div>
      <input class="in" id="cv" type="number" inputmode="numeric" placeholder="Quanto quer gastar?" value="${esc(f.valor)}">
      <select id="cm"><option value="gold" ${f.moeda === 'gold' ? 'selected' : ''}>em coins</option><option value="orb" ${f.moeda === 'orb' ? 'selected' : ''}>em gemas 💎</option></select>
      <select id="cd">${[[7, '1 semana'], [14, '2 semanas'], [30, '1 mês'], [90, '3 meses'], [180, '6 meses']].map(([v, l]) => `<option value="${v}" ${Number(f.dias) === v ? 'selected' : ''}>vou usar por ${l}</option>`).join('')}</select>
      <button class="btn" id="cb" ${busca && busca.carregando ? 'disabled' : ''}>${busca && busca.carregando ? 'Procurando…' : 'Procurar no Market agora'}</button></div>`;
    if (!busca) return h;
    if (busca.carregando) return h + '<div class="card"><div class="sub">Consultando o Market por esta conta (uma espécie a cada 2,5 s, uns 40 segundos). O jogo segue normal.</div></div>';
    if (busca.erro) return h + `<div class="card"><div class="sub">⚠ ${esc(busca.erro === 'mercado-aberto' ? 'Feche o Market dentro do jogo e tente de novo.' : busca.erro)}</div></div>`;
    const { valor, moeda } = busca, L = busca.L || 0, nm = moeda === 'gold' ? 'coins' : 'gemas';
    const usa = (o) => !L || o.level <= L + 5;
    const ord = (busca.lista || []).filter((o) => o.moeda === moeda && o.preco > 0 && o.preco <= valor && !o.retido && usa(o) && o.farm > 0).sort((x, y) => y.farm - x.farm || x.preco - y.preco);
    const pick = []; let gasto = 0;
    for (const o of ord) { if (pick.length >= 3) break; if (pick.some((p) => p.speciesId === o.speciesId) || gasto + o.preco > valor) continue; pick.push(o); gasto += o.preco; }
    const linha = (o) => `<div class="ln"><span><b>${esc(o.nome)} · Nv ${nf(o.level)}${o.shiny ? ' ✨' : ''}</b><br><span class="sub">${o.x55} hunts ×5,5 · ${o.x25} ×2,5 · ataque ${nf(o.ataque)}${o.aoe ? ' · AoE' : ''} · vendedor ${esc(o.vendedor || '—')}</span></span><b class="${moeda === 'gold' ? 'gold' : ''}">${nf(o.preco)}<br><span class="sub">${nm}</span></b></div>`;
    if (!ord.length) return h + `<div class="card"><div class="sub">Nenhuma oferta em ${nm} até ${nf(valor)} no seu nível (até o Nv ${nf(busca.tetoNv || L + 5)}) para os pokémon que rendem nas suas hunts. Tente um valor maior ou a outra moeda.</div></div>`;
    h += `<div class="card"><div class="t">Melhor compra · ${nf(gasto)} ${nm} (sobram ${nf(valor - gasto)})</div>${pick.map(linha).join('')}</div>`;
    const resto = ord.filter((o) => !pick.includes(o));
    if (resto.length) h += `<div class="card"><div class="t">Outras no seu orçamento (${nf(resto.length)}), da que mais rende para a que menos</div>${resto.slice(0, 30).map(linha).join('')}</div>`;
    return h + `<div class="sub">Busca das ${busca.em ? new Date(busca.em).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : 'agora'}, contando ${nf(busca.huntsAlvo || 0)} hunts de ${esc((busca.regioes || []).join(' e ') || 'suas regiões')}. Procure o pokémon no Market do jogo e confira o nível e o vendedor antes de comprar. A compra é sempre sua.</div>`;
  }
  function ligarComprar() {
    const salvar = () => LSset('compra', { valor: $('#cv').value, moeda: $('#cm').value, dias: Number($('#cd').value) || 30 });
    ['#cv', '#cm', '#cd'].forEach((q) => { const e = $(q); if (e) e.onchange = salvar; });
    const b = $('#cb');
    if (b) b.onclick = async () => {
      salvar();
      const valor = Number($('#cv').value), moeda = $('#cm').value, dias = Number($('#cd').value) || 30;
      if (!(valor > 0)) { $('#cv').focus(); return; }
      busca = { carregando: true };
      desenhar();
      const r = await M().pve('mercado', { moeda, dias, valor });
      busca = { valor, moeda, lista: (r && r.lista) || [], em: r && r.em, L: r && r.L, tetoNv: r && r.tetoNv, huntsAlvo: r && r.huntsAlvo, regioes: (r && r.regioes) || [], erro: r && r.erro };
      if (aba === 'comprar' && $('#pn').classList.contains('on')) desenhar();
    };
  }

  // ================= PvP: o meta do top 50 lido do próprio jogo, igual ao PC =================
  const PVP_DIAG = { FIRE: 0.5, WATER: 0.5, ELECTRIC: 0.5, GRASS: 0.5, ICE: 0.5, POISON: 0.5, PSYCHIC: 0.5, DARK: 0.5, STEEL: 0.5, GHOST: 2, DRAGON: 2 };
  const TIER_COR = { S: '#ff6b6b', A: '#ffb347', B: '#ffe066', C: '#8fd18f', U: '#8a90a6' };
  const pvpEff = (tb, atq, d1, d2) => { const um = (x, d) => { if (!d) return 1; if (x === d) return PVP_DIAG[x] ?? 1; return ((tb || {})[x] || {})[d] ?? 1; }; let v = um(atq, d1); if (d2 && d2 !== d1) v *= um(atq, d2); return v; };
  const pvpBest = (tb, atks, defs) => Math.max(0, ...atks.map((x) => pvpEff(tb, x, defs[0], defs[1])));
  const statSum = (st) => (st ? st.hp + st.atk + st.def + st.spa + st.spd + st.spe : 0);
  const pct = (x) => Math.round((x || 0) * 100) + '%';
  const curtoNm = (n) => String(n || '').replace(/^Mega /, 'M. ');
  const ha = (t) => { const m = Math.round((Date.now() - t) / 60000); return m < 60 ? `há ${m} min` : m < 2880 ? `há ${Math.round(m / 60)} h` : `há ${Math.round(m / 1440)} dias`; };
  let pvpTab = LS('pvpTab', 'meta');
  let pvpRodando = false, pvpErro = null, pvpMk = null, semLend = true;
  const simCache = new Map();
  const META_K = 'idleterino:m:pvpMeta';
  const metaGet = () => { try { return JSON.parse(localStorage.getItem(META_K) || 'null'); } catch { return null; } };

  function arquetipos(linhas, perfis) {
    const teams = linhas.filter((l) => perfis[l.nick] && perfis[l.nick].time && perfis[l.nick].time.length).map((l) => ({ ...l, sp: perfis[l.nick].time.map((k) => k.speciesId), wr: l.v / Math.max(1, l.v + l.d) }));
    const grupos = [];
    for (const t of teams) { const g = grupos.find((x) => t.sp.filter((id) => x.seed.includes(id)).length >= 3); if (g) g.teams.push(t); else grupos.push({ seed: t.sp, teams: [t] }); }
    const maxN = Math.max(1, ...grupos.map((g) => g.teams.length));
    const prs = teams.map((t) => t.pr); const minPr = Math.min(...prs), maxPr = Math.max(...prs);
    const out = grupos.map((g, i) => {
      const fq = {}; for (const t of g.teams) for (const id of t.sp) fq[id] = (fq[id] || 0) + 1;
      const core = Object.entries(fq).sort((a, b) => b[1] - a[1]).map(([id]) => Number(id));
      const n = g.teams.length, pr = g.teams.reduce((x, t) => x + t.pr, 0) / n, wr = g.teams.reduce((x, t) => x + t.wr, 0) / n;
      return { id: i, key: core.slice(0, 3).sort((a, b) => a - b).join('-'), core: core.slice(0, 5), n, pr: Math.round(pr), wr: Math.round(wr * 1000) / 1000, best: Math.min(...g.teams.map((t) => t.pos)), nicks: g.teams.map((t) => t.nick), rep: g.teams[0].nick, score: 0.45 * (n / maxN) + 0.35 * ((pr - minPr) / Math.max(1, maxPr - minPr)) + 0.2 * wr };
    }).sort((a, b) => b.score - a.score);
    const rk = out.filter((x) => x.n >= 2);
    rk.forEach((x, i) => { const q = i / Math.max(1, rk.length); x.tier = q < 0.2 ? 'S' : q < 0.5 ? 'A' : q < 0.8 ? 'B' : 'C'; });
    for (const x of out) if (!x.tier) x.tier = 'U';
    const porNick = {}; for (const x of out) for (const nk of x.nicks) porNick[nk] = x.id;
    return { arqs: out, porNick };
  }

  // Lê o top 50 (uma consulta a cada 5 s, ~5 min) e calcula tudo; fica guardado nesta conta do celular.
  async function coletarMeta() {
    if (pvpRodando || !M()) return;
    pvpRodando = true; pvpErro = null;
    if (aba === 'pvp') desenhar();
    try {
      const r = await M().pvpColetar(50, 5000);
      if (!r || !r.ok) { pvpErro = 'Não deu para ler o ranking agora' + (r && r.motivo ? ` (${r.motivo})` : '') + '. Tente de novo daqui a pouco.'; return; }
      const d = await M().pvpLer();
      if (!d || !d.linhas || !d.linhas.length) { pvpErro = 'O ranking veio vazio.'; return; }
      const linhas = d.linhas.slice(0, 50);
      const { arqs, porNick } = arquetipos(linhas, d.perfis);
      const entries = [];
      for (const l of linhas) { const pf = d.perfis[l.nick]; if (!pf) continue; const w = 1 + (51 - Math.min(50, l.pos)) / 50; for (const k of pf.time) entries.push({ ...k, w, arq: porNick[l.nick] }); }
      const med = (a) => (a.length ? a[Math.floor(a.length / 2)] : null);
      const num = (a) => a.filter((x) => Number.isFinite(x)).sort((x, y) => x - y);
      const medObj = (campo, ks) => Object.fromEntries(ks.map((k) => [k, med(num(entries.map((e) => Number((e[campo] || {})[k])))) || 0]));
      const ref = { level: med(num(entries.map((e) => e.level))) || 3000, potencia: med(num(entries.map((e) => Number(e.potencia)))) || 2, quality: med(num(entries.map((e) => Number(e.quality)))) || 1.25,
        ivs: medObj('ivs', ['hp', 'atk', 'def', 'spAtk', 'spDef', 'speed']), refino: medObj('refino', ['hp', 'atk', 'def', 'spAtk', 'spDef']), shiny: entries.filter((e) => e.shiny).length > entries.length / 2 };
      const cand = await M().candidatos(entries, { ref, top: 40 });
      const candidatos = cand && Array.isArray(cand.lista) ? cand.lista : [];
      const usados = {}; for (const e of entries) usados[e.nome || e.speciesId] = (usados[e.nome || e.speciesId] || 0) + 1;
      const nomes = [...new Set([...candidatos.filter((x) => !x.lend).slice(0, 20).map((x) => x.nome), ...Object.entries(usados).sort((a, b) => b[1] - a[1]).slice(0, 10).map(([n]) => n)])].filter((n) => typeof n === 'string');
      const precos = await M().precos(nomes);
      const meta = { at: Date.now(), linhas, perfis: d.perfis, info: d.info, tabelaTipos: d.tabelaTipos, arqs, porNick, ref, candidatos, precos: precos && !precos.erro ? precos : {} };
      try { localStorage.setItem(META_K, JSON.stringify(meta)); } catch { pvpErro = 'Sem espaço no celular para guardar o meta.'; }
      const uso = {}; for (const e of entries) uso[e.speciesId] = (uso[e.speciesId] || 0) + 1;
      LSset('pvpHist', [...LS('pvpHist', []), { at: meta.at, uso, arqs: Object.fromEntries(arqs.map((x) => [x.key, x.n])) }].slice(-14));
      simCache.clear();
    } catch (e) { pvpErro = String(e && e.message || e); }
    finally { pvpRodando = false; if (aba === 'pvp' && $('#pn').classList.contains('on')) desenhar(); }
  }
  // A principal lê o meta sozinha uma vez por dia (3 min depois de abrir), como no PC.
  setTimeout(async () => { const m = metaGet(); if ((!m || Date.now() - m.at > 24 * 3600000) && APP && (await chamar('souPrincipal'))) coletarMeta(); }, 3 * 60000);

  function metaEntries(meta) {
    const out = [];
    for (const l of meta.linhas) { const pf = meta.perfis[l.nick]; if (!pf) continue; const w = 1 + (51 - Math.min(50, l.pos)) / 50;
      for (const k of pf.time) { const inf = meta.info[k.speciesId] || {}; const tipos = inf.tipos || []; out.push({ ...k, pos: l.pos, nick: l.nick, w, tipos, atk: [...new Set([...tipos, k.el || k.tmElemental].filter(Boolean))], alcance: inf.alcance || 1, stats: inf.stats, nome: k.nome || inf.nome || `#${k.speciesId}` }); } }
    return out;
  }
  function pvpScore(c, entries, tb, refLevel) {
    let off = 0, def = 0, ws = 0; const forte = new Map(), fraco = new Map();
    for (const e of entries) { const o = pvpBest(tb, c.atk, e.tipos), d = pvpBest(tb, e.atk, c.tipos); off += o * e.w; def += d * e.w; ws += e.w; if (o >= 2) forte.set(e.nome, 1); if (d >= 2) fraco.set(e.nome, 1); }
    off /= ws || 1; def /= ws || 1;
    const lvl = Math.min(1.5, (c.level || 1) / Math.max(1, refLevel));
    return { score: off - def * 0.8 + lvl * 1.4 + (statSum(c.stats) / 600) * 0.5 + (c.aoe ? 0.25 : 0), forte: [...forte.keys()], fraco: [...fraco.keys()] };
  }
  function meusPvp(live, info) {
    if (!live) return [];
    return Object.values(live.meus || {}).map((m) => { const inf = info[m.speciesId] || {}; const tipos = inf.tipos || []; const z = m.el || m.tmElemental || null;
      return { id: m.id, speciesId: Number(m.speciesId), nome: inf.nome || `#${m.speciesId}`, tipos, atk: [...new Set([...tipos, z].filter(Boolean))], aoe: !!m.aoe, level: m.level, alcance: inf.alcance || 1, stats: inf.stats, pk: m }; }).sort((a, b) => b.level - a.level);
  }
  function sugerir(mine, entries, tb, refLevel) {
    const pool = mine.map((c) => ({ c, s: pvpScore(c, entries, tb, refLevel) })).sort((a, b) => b.s.score - a.s.score);
    const time = [], coberto = new Set();
    while (time.length < 5 && pool.length) {
      let best = null, bv = -1e9; const perto = time.filter((x) => x.c.alcance <= 1).length, longe = time.length - perto;
      for (const x of pool) { const v = x.s.score + x.s.forte.filter((n) => !coberto.has(n)).length * 0.08 + (x.c.alcance <= 1 ? (perto >= 3 ? -0.6 : 0) : (longe >= 3 ? -0.6 : 0)); if (v > bv) { bv = v; best = x; } }
      if (!best) break; time.push(best); for (const n of best.s.forte) coberto.add(n); pool.splice(pool.indexOf(best), 1);
    }
    return time;
  }
  const arqNome = (x, meta) => x.core.slice(0, 3).map((id) => curtoNm((meta.info[id] || {}).nome || `#${id}`)).join(' + ');
  const arqTime = (x, meta) => ((meta.perfis[x.rep] || {}).time || []).map((k) => { const inf = meta.info[k.speciesId] || {}; return { ...k, nome: k.nome || inf.nome, tipos: inf.tipos || [], atk: [...new Set([...(inf.tipos || []), k.el || k.tmElemental].filter(Boolean))] }; });
  async function simTime(time, inimigo) {
    const k = time.map((m) => `${m.speciesId}:${m.level}`).join(',') + '|' + inimigo.map((x) => `${x.speciesId}:${x.level}`).join(',');
    if (simCache.has(k)) return simCache.get(k);
    const r = await M().duelos(time.map((m) => ({ ...m.pk, speciesId: m.speciesId })), inimigo);
    if (!r || !r.m) return null;
    const mm = r.m.map((row) => row.map((x) => (x == null ? 0.5 : x)));
    const cols = inimigo.map((_, j) => Math.max(...mm.map((row) => row[j]))); const all = mm.flat();
    const est = 0.6 * (cols.reduce((a, b) => a + b, 0) / cols.length) + 0.4 * (all.reduce((a, b) => a + b, 0) / all.length);
    simCache.set(k, est); return est;
  }
  const precoTxt = (meta, nome) => { const rows = (meta.precos || {})[nome] || []; const r = rows.find((x) => String(x.nome).toLowerCase() === String(nome).toLowerCase()); if (!r) return '<span class="sub">sem oferta lida</span>'; return [r.minOrb ? `${nf(r.minOrb)} 💎` : '', r.minGold ? `<span class="gold">${curto(r.minGold)} coins</span>` : ''].filter(Boolean).join('<br>'); };

  async function htmlPvp() {
    const meta = metaGet();
    const sub = `<div class="sub2">${[['meta', 'Meta'], ['time', 'Meu time'], ['mk', 'Comprar p/ PvP']].map(([k, l]) => `<button data-pt="${k}" class="${pvpTab === k ? 'on' : ''}">${l}</button>`).join('')}</div>`;
    const est = pvpRodando ? (M().pvpEstado() || {}) : null;
    const pode = !meta || Date.now() - meta.at > 30 * 60000;
    let h = `<div class="card"><div class="t">PvP · top 50 do Ranqueado</div><div class="sub">${pvpRodando ? `Lendo o top 50… ${est && est.total ? `${est.feitos}/${est.total}` : ''} (uns 5 minutos, uma consulta a cada 5 s; o jogo segue normal)` : meta ? `Meta lido ${ha(meta.at)} por esta conta. A conta principal relê sozinha uma vez por dia.` : 'O meta ainda não foi lido nesta conta.'}</div>
      ${pvpErro ? `<div class="sub bad">⚠ ${esc(pvpErro)}</div>` : ''}${pvpRodando ? '' : `<button class="btn ${pode ? '' : 'g2'}" id="pvpLer" ${pode ? '' : 'disabled'}>${pode ? '↻ Ler o meta agora' : 'Dá para reler 30 min depois da última leitura'}</button>`}${sub}</div>`;
    if (!meta || pvpRodando) return h;
    const ids = [...new Set(Object.values(meta.perfis || {}).flatMap((pf) => (pf.time || []).map((k) => k.speciesId)))];
    const live = await M().pvpLer(ids).catch(() => null);
    const ok = live && !live.erro;
    if (ok && live.info) meta.info = { ...(meta.info || {}), ...live.info };
    const tb = (ok && live.tabelaTipos) || meta.tabelaTipos || {};
    const entries = metaEntries(meta);
    const teams = meta.linhas.filter((l) => meta.perfis[l.nick]);
    const lv = entries.map((e) => e.level).sort((a, b) => a - b); const refLevel = lv[Math.floor(lv.length / 2)] || 1;
    const mine = meusPvp(ok ? live : null, meta.info);
    const arqs = meta.arqs || [];
    const top = arqs.filter((x) => x.tier !== 'U').slice(0, 8);
    const hist = LS('pvpHist', []); const velho = hist.length > 1 ? hist[Math.max(0, hist.length - 8)] : null;

    if (pvpTab === 'meta') {
      if (ok && live.euPvp && live.euPvp.pontos != null) { const e = live.euPvp; h += `<div class="card"><div class="t">Seu PvP agora</div><div class="g"><div class="k"><b>${nf(e.pontos)}</b><span>PR${e.posicao ? ` · #${nf(e.posicao)}` : ''}</span></div><div class="k"><b>${nf(e.vitorias)}V / ${nf(e.derrotas)}D</b><span>${pct(e.vitorias / Math.max(1, e.vitorias + e.derrotas))} de vitórias</span></div></div></div>`; }
      h += `<div class="card"><div class="t">Tier list dos arquétipos · ${teams.length} times</div>`;
      for (const t of ['S', 'A', 'B', 'C']) for (const x of arqs.filter((y) => y.tier === t).slice(0, 8)) {
        const was = velho && velho.arqs ? velho.arqs[x.key] : null; const tr = was == null ? '' : x.n > was ? ` <span class="ok">▲${x.n - was}</span>` : x.n < was ? ` <span class="bad">▼${was - x.n}</span>` : '';
        h += `<div class="ln"><span><span class="tier" style="background:${TIER_COR[t]}">${t}</span> <b>${esc(arqNome(x, meta))}</b>${tr}<br><span class="sub">${x.n} no top · PR ${nf(x.pr)} · ${pct(x.wr)} vit. · melhor #${x.best}</span></span></div>`;
      }
      h += '<div class="sub">Arquétipo = times do top que dividem 3+ pokémon. Tier pelo uso, PR médio e vitórias. ▲▼ = mudança na última semana.</div></div>';
      const bySp = new Map(); for (const e of entries) { const c = bySp.get(e.speciesId) || { e, n: 0, lv: 0 }; c.n++; c.lv += e.level; bySp.set(e.speciesId, c); }
      h += `<div class="card"><div class="t">Pokémon mais usados</div>${[...bySp.values()].sort((a, b) => b.n - a.n).slice(0, 12).map((u) => { const tem = mine.find((m) => m.speciesId === u.e.speciesId);
        return `<div class="ln"><span><b>${esc(u.e.nome)}</b> <span class="sub">${esc(u.e.tipos.map(tipo).join(' / '))}</span><br><span class="sub">Nv médio ${nf(Math.round(u.lv / u.n))} · </span>${tem ? `<span class="chip ok">você tem · Nv ${nf(tem.level)}</span>` : '<span class="chip">você não tem</span>'}</span><b>${u.n}/${teams.length}</b></div>`; }).join('')}</div>`;
      const pune = Object.keys(TIPO_PT).map((t) => [t, entries.reduce((x, e) => x + (pvpEff(tb, t, e.tipos[0], e.tipos[1]) >= 2 ? e.w : 0), 0)]).sort((a, b) => b[1] - a[1]).slice(0, 5);
      h += `<div class="card"><div class="t">Ataques que mais punem o meta</div><div>${pune.map(([t]) => `<span class="chip">${tipo(t)}</span>`).join('')}</div></div>`;
      h += `<div class="card"><div class="t">Top 50</div>${meta.linhas.map((l) => { const pf = meta.perfis[l.nick]; const x = arqs.find((y) => y.id === (meta.porNick || {})[l.nick]);
        return `<div class="ln"><span><b>#${l.pos} ${esc(l.nick)}</b>${x && x.tier !== 'U' ? ` <span class="tier" style="background:${TIER_COR[x.tier]}">${x.tier}</span>` : ''}<br><span class="sub">${(pf ? pf.time : []).map((k) => `${esc(curtoNm(k.nome))} ${nf(k.level)}`).join(' · ')}</span></span><b>${nf(l.pr)}<br><span class="sub">${pct(l.v / Math.max(1, l.v + l.d))}</span></b></div>`; }).join('')}</div>`;
      return h;
    }

    if (pvpTab === 'time') {
      if (!mine.length) return h + '<div class="card"><div class="sub">Não consegui ler os pokémon desta conta ainda. Espere o jogo carregar e abra de novo.</div></div>';
      const sug = sugerir(mine, entries, tb, refLevel);
      const time = sug.map((x) => x.c);
      h += `<div class="card"><div class="t">Melhor time com o que você tem</div>${sug.map((x) => `<div class="ln"><span><b>${esc(x.c.nome)}</b> Nv ${nf(x.c.level)}${x.c.aoe ? ' · AoE' : ''}<br><span class="sub">${esc(x.c.tipos.map(tipo).join(' / '))} · ${x.c.alcance > 1 ? 'longe' : 'perto'} · bate forte em ${x.s.forte.length} do meta · apanha forte de ${x.s.fraco.length}</span></span></div>`).join('')}${mine.length < 5 ? `<div class="sub">Esta conta tem só ${mine.length} espécie(s): o time completo tem 5.</div>` : ''}<div class="sub">O time de verdade você monta no jogo (Editar time).</div></div>`;
      if (top.length) {
        const sims = await Promise.all(top.map((x) => simTime(time, arqTime(x, meta)).catch(() => null)));
        h += `<div class="card"><div class="t">Esse time contra os arquétipos do topo</div>${top.map((x, i) => { const e = sims[i]; return `<div class="ln"><span><span class="tier" style="background:${TIER_COR[x.tier]}">${x.tier}</span> <b>${esc(arqNome(x, meta))}</b><br><span class="sub">${x.n} no top · PR ${nf(x.pr)}</span></span><b class="${e == null ? '' : e >= 0.55 ? 'ok' : e >= 0.45 ? 'meh' : 'bad'}">${e == null ? '—' : pct(e)}</b></div>`; }).join('')}<div class="sub">Força = duelos 1×1 do próprio jogo (nível, IV, qualidade, potência, golpe, tipos e Z-Move) de cada um seu contra cada um do time mais bem colocado do arquétipo. Na luta real também contam posição e alcance.</div></div>`;
      }
      const cand = (meta.candidatos || []).filter((x) => !semLend || !x.lend);
      if (cand.length && top.length) {
        const fraco = time.map((m) => ({ m, s: pvpScore(m, entries, tb, refLevel).score })).sort((a, b) => a.s - b.s)[0];
        const pick = []; const cob = (l) => top.reduce((x, a2) => x + Math.max(0, ...l.map((y) => (y.porArq || {})[a2.id] ?? y.win)), 0) / top.length;
        while (pick.length < 5) { let b = null, bv = -1; for (const x of cand.slice(0, 40)) { if (pick.includes(x)) continue; const tps = new Set(pick.flatMap((q) => q.tipos)); const v = cob([...pick, x]) + x.win * 0.3 - (x.tipos.some((t) => tps.has(t)) ? 0.03 : 0); if (v > bv) { bv = v; b = x; } } if (!b) break; pick.push(b); }
        const linhaC = (x) => { const tem = mine.some((m) => m.speciesId === x.speciesId); return `<div class="ln"><span><b>${esc(x.nome)}</b>${x.lend ? ' · lendário' : ''} ${tem ? '<span class="chip ok">você tem</span>' : '<span class="chip">💡 você não tem</span>'}<br><span class="sub">${esc((x.tipos || []).map(tipo).join(' / '))} · vence ${pct(x.win)} dos duelos · ${top.filter((a2) => ((x.porArq || {})[a2.id] ?? 0) >= 0.5).length}/${top.length} arquétipos</span></span><b style="text-align:right">${precoTxt(meta, x.nome)}</b></div>`; };
        if (fraco) h += `<div class="card"><div class="t">Upgrade</div><div class="sub">O mais fraco do seu time contra o meta hoje: <b>${esc(fraco.m.nome)}</b> (Nv ${nf(fraco.m.level)}). Troque por um destes:</div>${cand.filter((x) => !time.some((m) => m.speciesId === x.speciesId)).slice(0, 4).map(linhaC).join('')}</div>`;
        h += `<div class="card"><div class="t">Time anti-meta (sem limite)</div><label class="sw"><span>Sem lendários</span><input type="checkbox" id="semLend" ${semLend ? 'checked' : ''}></label><div class="sub">No nível típico do top (Nv ~${nf(meta.ref ? meta.ref.level : refLevel)}), o caminho para montar do zero ou comprar no Market.</div>${pick.map(linhaC).join('')}</div>`;
        h += `<div class="card"><div class="t">Contra cada arquétipo: quem mais vence</div>${top.map((a2) => { const vis = new Set(); const best = [...cand].filter((x) => x.porArq && x.porArq[a2.id] != null).sort((x, y) => y.porArq[a2.id] - x.porArq[a2.id]).filter((x) => !vis.has(x.nome) && vis.add(x.nome)).slice(0, 4);
          return `<div class="ln"><span><b>${esc(arqNome(a2, meta))}</b><br>${best.map((x) => `<span class="chip ${mine.some((m) => m.speciesId === x.speciesId) ? 'ok' : ''}">${esc(curtoNm(x.nome))} ${pct(x.porArq[a2.id])}</span>`).join('')}</span></div>`; }).join('')}<div class="sub">Verde = você já tem.</div></div>`;
      }
      return h;
    }

    // Comprar para o PvP: as ofertas reais dos pokémon mais fortes contra o meta (sem trava de nível no PvP).
    const f = LS('compraPvp', { valor: '', moeda: 'orb' });
    h += `<div class="card"><div class="t">Comprar no Market para o PvP</div><div class="sub">No PvP não há trava de nível. O app procura AGORA as ofertas dos pokémon mais fortes contra o meta e avalia cada um à venda (nível, IV, shiny, AoE, Z-Move) no duelo do jogo contra o top.</div>
      <input class="in" id="pv" type="number" inputmode="numeric" placeholder="Quanto quer gastar?" value="${esc(f.valor)}">
      <select id="pm"><option value="orb" ${f.moeda === 'orb' ? 'selected' : ''}>em gemas 💎</option><option value="gold" ${f.moeda === 'gold' ? 'selected' : ''}>em coins</option></select>
      <button class="btn" id="pb" ${pvpMk && pvpMk.carregando ? 'disabled' : ''}>${pvpMk && pvpMk.carregando ? 'Procurando… (uns 40 s)' : 'Procurar no Market agora'}</button></div>`;
    if (!pvpMk || pvpMk.carregando) return h;
    if (pvpMk.erro) return h + `<div class="card"><div class="sub bad">⚠ ${esc(pvpMk.erro === 'mercado-aberto' ? 'Feche o Market dentro do jogo e tente de novo.' : pvpMk.erro)}</div></div>`;
    const { valor, moeda, lista } = pvpMk; const nm = moeda === 'gold' ? 'coins' : 'gemas';
    const ord = lista.filter((o) => o.moeda === moeda && o.preco > 0 && o.preco <= valor && !o.retido && o.win >= 0.03).sort((x, y) => y.win - x.win || x.preco - y.preco);
    const pick = []; let gasto = 0;
    for (const o of ord) { if (pick.length >= 5) break; if (pick.some((q) => q.speciesId === o.speciesId) || gasto + o.preco > valor) continue; pick.push(o); gasto += o.preco; }
    const linha = (o) => `<div class="ln"><span><b>${esc(o.nome)} · Nv ${nf(o.level)}${o.shiny ? ' ✨' : ''}</b><br><span class="sub">vence ${pct(o.win)} dos duelos contra o top${o.aoe ? ' · AoE' : ''}${o.el ? ' · Z ' + esc(tipo(o.el)) : ''} · vendedor ${esc(o.vendedor || '—')}</span></span><b class="${moeda === 'gold' ? 'gold' : ''}">${nf(o.preco)}<br><span class="sub">${nm}</span></b></div>`;
    h += pick.length ? `<div class="card"><div class="t">Melhor compra · ${nf(gasto)} ${nm} (sobram ${nf(valor - gasto)})</div>${pick.map(linha).join('')}</div>` : `<div class="card"><div class="sub">Com ${nf(valor)} ${nm}, nada à venda agora vence duelos contra o top (eles são Nv ~${nf(meta.ref ? meta.ref.level : refLevel)}).</div></div>`;
    const resto = lista.filter((o) => o.moeda === moeda && !pick.includes(o)).sort((x, y) => y.win - x.win).slice(0, 8);
    if (resto.length) h += `<div class="card"><div class="t">Outras ofertas fortes (algumas acima do valor)</div>${resto.map(linha).join('')}</div>`;
    return h + '<div class="sub">Procure pelo nome no Market do jogo e confira o nível e o vendedor antes de comprar. A compra é sempre sua.</div>';
  }
  function ligarPvp() {
    raiz.querySelectorAll('[data-pt]').forEach((b) => { b.onclick = () => { pvpTab = b.dataset.pt; LSset('pvpTab', pvpTab); desenhar(); }; });
    const l = $('#pvpLer'); if (l) l.onclick = () => coletarMeta();
    const sl = $('#semLend'); if (sl) sl.onchange = () => { semLend = sl.checked; desenhar(); };
    const pb = $('#pb');
    if (pb) pb.onclick = async () => {
      const valor = Number($('#pv').value), moeda = $('#pm').value;
      LSset('compraPvp', { valor: $('#pv').value, moeda });
      if (!(valor > 0)) { $('#pv').focus(); return; }
      const meta = metaGet(); if (!meta || !meta.candidatos) { pvpMk = { erro: 'Leia o meta primeiro.' }; desenhar(); return; }
      pvpMk = { carregando: true }; desenhar();
      const entries = []; for (const ln of meta.linhas) { const pf = meta.perfis[ln.nick]; if (!pf) continue; const w = 1 + (51 - Math.min(50, ln.pos)) / 50; for (const k of pf.time) entries.push({ ...k, w }); }
      const r = await M().ofertas(meta.candidatos.filter((x) => !x.lend).slice(0, 15).map((x) => x.speciesId), moeda === 'gold' ? 'gold' : 'orb', entries).catch(() => null);
      pvpMk = !r ? { erro: 'Não consegui consultar o Market agora.' } : { valor, moeda, lista: r.lista || [], erro: r.erro };
      if (aba === 'pvp' && $('#pn').classList.contains('on')) desenhar();
    };
  }

  function htmlContas(l) {
    const t = M() && M().treinador();
    return `<div class="card"><div class="t">Suas contas no celular</div>${l.length ? l.map((c) => `<div class="ln"><span><b>${esc(c.nick)}</b>${c.ativa ? ' <span class="tag">⭐ principal</span>' : ' <span class="tag">ALT</span>'}</span><span>${c.ativa || (t && t.nick === c.nick) ? '' : `<button class="btn g2" data-usar="${esc(c.nick)}">Tornar principal</button>`} <button class="btn g2" data-tirar="${esc(c.nick)}">×</button></span></div>`).join('') : '<div class="sub">Nenhuma conta guardada ainda.</div>'}
      <div class="sub">A principal fica na aba ⭐ Principal; as outras (até 3) farmam juntas na aba 👥 ALTs, no modo ULTRA (leve). Cada conta conectada aqui sai do PC, igual quando você abre no navegador.</div></div>
      <div class="card"><div class="t">Trazer as contas do PC</div><div class="sub">No PC: Idleterino > Ajustes > "Levar contas para o celular". Aponte a câmera do celular para o QR, toque no link e digite o PIN de 8 números. As contas entram já logadas, sem senha.</div></div>
      <div class="card"><button class="btn g2" id="nova">Entrar em outra conta (login do jogo)</button></div>`;
  }
  function ligarContas() {
    raiz.querySelectorAll('[data-usar]').forEach((b) => { b.onclick = () => { chamar('trocarConta', { nick: b.dataset.usar }); fechar(); }; });
    raiz.querySelectorAll('[data-tirar]').forEach((b) => { b.onclick = async () => { await chamar('removerConta', { nick: b.dataset.tirar }); desenhar(); }; });
    const n = $('#nova'); if (n) n.onclick = () => { chamar('novaConta'); fechar(); };
  }

  function htmlAjustes(farm) {
    const av = LS('avisos', { shiny: true, nivel: false, conexao: true });
    return `<div class="card"><div class="sub">Com o app aberto, tudo segue farmando mesmo quando você vai para outro app (por isso a notificação fixa). Para parar tudo, feche o Idleterino: tire ele dos recentes ou toque em "Fechar o Idleterino" na notificação.</div>
        <div class="sw"><span><b>Farmar com a tela desligada</b><br><span class="sub">Segue conectado com o celular bloqueado. Gasta mais bateria.</span></span><input type="checkbox" id="farm" ${farm ? 'checked' : ''}></div></div>
      <div class="card"><div class="t">Avisos no celular</div>
        <div class="sw"><span>✨ Shiny visto ou capturado</span><input type="checkbox" data-av="shiny" ${av.shiny ? 'checked' : ''}></div>
        <div class="sw"><span>⬆ Subiu de nível</span><input type="checkbox" data-av="nivel" ${av.nivel ? 'checked' : ''}></div>
        <div class="sw"><span>⚠ Conexão caiu</span><input type="checkbox" data-av="conexao" ${av.conexao ? 'checked' : ''}></div></div>
      <div class="card"><button class="btn g2" id="att">Procurar atualização</button><div class="sub">O app também confere sozinho quando abre.</div></div>`;
  }
  function ligarAjustes() {
    const f = $('#farm'); if (f) f.onchange = () => chamar('farm', { ligar: f.checked });
    raiz.querySelectorAll('[data-av]').forEach((c) => { c.onchange = () => { const av = LS('avisos', { shiny: true, nivel: false, conexao: true }); av[c.dataset.av] = c.checked; LSset('avisos', av); }; });
    const a = $('#att'); if (a) a.onclick = () => chamar('verificarAtualizacao');
  }

  function htmlApoio() {
    return `<div class="card" style="text-align:center"><div style="font-size:20px;font-weight:900">❤ Apoie o dev</div>
      <div class="sub">O Idleterino é feito por um jogador, nas horas vagas, e é de graça para todo mundo. Se ele te ajuda e tocou o seu coração, qualquer valor no Pix ajuda a manter o app vivo e cheio de novidades. Se não der, usar e indicar já é um baita apoio. 💜</div>
      <div class="qr">${PIX_SVG}</div>
      <button class="btn" id="cc">Copiar Pix copia e cola</button><button class="btn g2" id="ck">Copiar a chave Pix</button>
      <div class="sub">Abra o app do banco > Pix > Copia e cola. O valor é você quem escolhe.</div></div>`;
  }
  function ligarApoio() {
    const copia = (v, msg) => { try { if (APP) chamar('copiar', { texto: v }); else navigator.clipboard.writeText(v); } catch {} const s = raiz.querySelector('.card .sub:last-child'); if (s) s.textContent = msg; };
    $('#cc').onclick = () => copia(PIX_COPIA, 'Copiado! Cole no Pix copia e cola do seu banco.');
    $('#ck').onclick = () => copia(PIX_CHAVE, 'Chave Pix copiada.');
  }

  // ---------- o login desta tela entra na lista de contas (só quando muda) ----------
  let ultimaSessao = null;
  setInterval(() => { try { const s = localStorage.getItem('sessao'); if (APP && s && s.indexOf('"token"') >= 0 && s !== ultimaSessao) { ultimaSessao = s; chamar('lembrarSessao', { sessao: s }); } } catch {} }, 20000);

  // ---------- avisos (rodam com o painel fechado também) ----------
  let ultimo = null;
  setInterval(() => {
    const m = M(); if (!m || !APP) return;
    const av = LS('avisos', { shiny: true, nivel: false, conexao: true });
    const t = m.treinador();
    const s = sessao();
    const agora = { nivel: t ? t.level : null, shV: s ? s.shV : null, shC: s ? s.shC : null, conectado: !!(t && Date.now() - (t.at || 0) < 3 * 60 * 1000) };
    if (ultimo) {
      if (av.shiny && agora.shC != null && ultimo.shC != null && agora.shC > ultimo.shC) chamar('aviso', { titulo: '✨ Shiny capturado!', texto: 'Um shiny entrou na sua coleção. Corre ver!' });
      else if (av.shiny && agora.shV != null && ultimo.shV != null && agora.shV > ultimo.shV) chamar('aviso', { titulo: '✨ Shiny visto!', texto: 'Apareceu um shiny no arremesso. Torça para a bola fechar!' });
      if (av.nivel && agora.nivel && ultimo.nivel && agora.nivel > ultimo.nivel) chamar('aviso', { titulo: '⬆ Subiu de nível', texto: `Agora Nv ${nf(agora.nivel)}.` });
      if (av.conexao && ultimo.conectado && !agora.conectado) chamar('aviso', { titulo: '⚠ O jogo desconectou', texto: 'Abra o Idleterino para reconectar.' });
    }
    ultimo = agora;
  }, 15000);

  const montar = () => { if (document.body && !host.isConnected) document.body.appendChild(host); };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', montar); else montar();
  setInterval(montar, 5000);
})();

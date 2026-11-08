/* Preview-only interactions. No product logic, API, persistence or app navigation. */
(() => {
  'use strict';
  const icons = {
    home:'<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/>',
    palette:'<circle cx="12" cy="12" r="9"/><circle cx="7" cy="11" r="1"/><circle cx="11" cy="6.5" r="1"/><circle cx="17" cy="9" r="1"/><path d="M12 21c2-1 0-4 3-5s5-1 6-4"/>',
    layers:'<path d="m12 3 9 5-9 5-9-5z"/><path d="m3 12 9 5 9-5m-18 5 9 5 9-5"/>',
    moon:'<path d="M20.5 14.5A8.5 8.5 0 0 1 9.5 3.5 8.5 8.5 0 1 0 20.5 14.5z"/>',
    sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M2 12h2m16 0h2M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42"/>',
    user:'<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    settings:'<circle cx="12" cy="12" r="3"/><path d="M19 13.5v-3l2-1.2-2-3.5-2.2.6-2.6-1.5-.5-2.4h-4l-.5 2.4-2.6 1.5-2.2-.6-2 3.5 2 1.2v3l-2 1.2 2 3.5 2.2-.6 2.6 1.5.5 2.4h4l.5-2.4 2.6-1.5 2.2.6 2-3.5z"/>',
    calendar:'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/>',
    wallet:'<rect x="3" y="5" width="18" height="15" rx="3"/><path d="M16 11h5v4h-5a2 2 0 0 1 0-4zM3 9h12"/>',
    list:'<path d="M9 6h12M9 12h12M9 18h12"/><circle cx="4" cy="6" r="1"/><circle cx="4" cy="12" r="1"/><circle cx="4" cy="18" r="1"/>',
    arrowUp:'<path d="m6 14 6-6 6 6M12 8v12"/>',
    arrowDown:'<path d="m6 10 6 6 6-6M12 4v12"/>',
    plus:'<path d="M12 5v14M5 12h14"/>',
    chevron:'<path d="m9 18 6-6-6-6"/>',
    check:'<path d="m4 12 5 5L20 6"/>',
    x:'<path d="M18 6 6 18M6 6l12 12"/>',
    info:'<circle cx="12" cy="12" r="10"/><path d="M12 11v5m0-9v.5"/>',
    alert:'<path d="m10 3-9 17h22L14 3a2.2 2.2 0 0 0-4 0zM12 9v4m0 4h.01"/>',
    search:'<circle cx="10.5" cy="10.5" r="7"/><path d="m16 16 5 5"/>',
    bell:'<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9m-8 13a2 2 0 0 0 4 0"/>',
    trash:'<path d="M4 7h16M10 4h4m-8 3 1 14h10l1-14M10 10v7m4-7v7"/>',
    edit:'<path d="m5 16-1 4 4-1 11-11-3-3zM14 7l3 3"/>',
    close:'<path d="M18 6 6 18M6 6l12 12"/>',
    shield:'<path d="m12 2 8 4v6c0 6-4 9-8 10-4-1-8-4-8-10V6zM9 12l2 2 4-4"/>',
    credit:'<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/>',
    chart:'<path d="M4 20V10m5 10V5m5 15v-8m5 8V3"/>',
    menu:'<path d="M4 6h16M4 12h16M4 18h16"/>',
    more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>'
  };
  function drawIcons() {
    document.querySelectorAll('[data-icon]').forEach(el => {
      const key = el.getAttribute('data-icon');
      const path = icons[key] || icons.info;
      const svg = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`;
      el.innerHTML = svg;
    });
  }

  function luminance(hex) {
    const normalized = hex.replace('#', '');
    const channels = [0, 2, 4].map(i => parseInt(normalized.slice(i, i+2), 16) / 255)
      .map(x => x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4);
    return channels[0]*0.2126 + channels[1]*0.7152 + channels[2]*0.0722;
  }
  function renderTokens() {
    const props = getComputedStyle(document.documentElement);
    const value = key => props.getPropertyValue('--color-' + key.replace(/[A-Z]/g, m => '-' + m.toLowerCase())).trim();
    document.querySelectorAll('[data-swatch-key]').forEach(el => { el.textContent = value(el.dataset.swatchKey); });
    document.querySelectorAll('[data-contrast-pair]').forEach(el => {
      const [fore, back] = el.dataset.contrastPair.split('/');
      const fg = value(fore), bg = value(back);
      if (!/^#[0-9a-f]{6}$/i.test(fg) || !/^#[0-9a-f]{6}$/i.test(bg)) return;
      const a = luminance(fg), b = luminance(bg);
      const ratio = (Math.max(a,b)+0.05)/(Math.min(a,b)+0.05);
      const required = ['borderStrong','focusRing'].includes(fore) ? 3 : 4.5;
      el.textContent = `${ratio.toFixed(2)}:1 ${ratio >= required ? '✓' : '✕'}`;
      el.classList.add('contrast-result');
      el.setAttribute('aria-label', `Contrast ratio ${ratio.toFixed(2)} to 1`);
    });
  }

  const themeKey = 'design-preview-theme';
  function currentTheme() {
    try { const t = localStorage.getItem(themeKey); if (t === 'light' || t === 'dark') return t; } catch (_) {}
    try { if (window.matchMedia('(prefers-color-scheme: dark)').matches) return 'dark'; } catch (_) {}
    return 'light';
  }
  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    document.querySelectorAll('[data-theme-toggle]').forEach(btn => {
      btn.setAttribute('aria-label', `Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`);
      btn.setAttribute('aria-pressed', String(theme === 'dark'));
      const label = btn.querySelector('[data-theme-label]');
      if (label) label.textContent = theme === 'dark' ? 'Dark theme' : 'Light theme';
      const symbol = btn.querySelector('[data-icon]');
      if (symbol) symbol.setAttribute('data-icon', theme === 'dark' ? 'moon' : 'sun');
    });
    drawIcons();
    renderTokens();
  }
  applyTheme(currentTheme());
  document.addEventListener('click', e => {
    const btn = e.target.closest('[data-theme-toggle]');
    if (!btn) return;
    const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    try { localStorage.setItem(themeKey, next); } catch (_) {}
    applyTheme(next);
  });
  let lastTrigger = null;
  const openSheets = () => [...document.querySelectorAll('[data-sheet]:not([hidden])')];
  function closeSheet(sheet) {
    if (!sheet) return;
    sheet.hidden = true;
    if (lastTrigger && document.contains(lastTrigger)) lastTrigger.focus();
    lastTrigger = null;
  }
  function openSheet(name, trigger) {
    const scope = trigger.closest('.phone') || document;
    const sheet = [...scope.querySelectorAll('[data-sheet]')].find(s => s.dataset.sheet === name) ||
      [...document.querySelectorAll('[data-sheet]')].find(s => s.dataset.sheet === name);
    if (!sheet) return;
    openSheets().forEach(closeSheet);
    lastTrigger = trigger;
    sheet.hidden = false;
    const dialog = sheet.querySelector('[role="dialog"]');
    if (dialog) dialog.focus();
  }
  document.addEventListener('click', e => {
    const opener = e.target.closest('[data-sheet-open]');
    if (opener) { openSheet(opener.dataset.sheetOpen, opener); return; }
    const closer = e.target.closest('[data-sheet-close]');
    if (closer) { closeSheet(closer.closest('[data-sheet]')); return; }
    const toastBtn = e.target.closest('[data-toast-text]');
    if (toastBtn) {
      const scope = toastBtn.closest('.phone') || document;
      const host = scope.querySelector('.toast-host');
      if (host) {
        const message = document.createElement('div');
        message.className = 'toast';
        message.setAttribute('role', 'status');
        message.textContent = toastBtn.dataset.toastText;
        host.replaceChildren(message);
        window.setTimeout(() => { if (host.contains(message)) message.remove(); }, 2800);
      }
    }
  });
  document.addEventListener('keydown', e => {
    const sheet = openSheets().at(-1);
    if (!sheet) return;
    if (e.key === 'Escape') { e.preventDefault(); closeSheet(sheet); return; }
    if (e.key !== 'Tab') return;
    const dialog = sheet.querySelector('[role="dialog"]');
    const focusable = [...dialog.querySelectorAll('a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])')].filter(el => !el.hidden);
    if (!focusable.length) { e.preventDefault(); dialog.focus(); return; }
    const first = focusable[0], last = focusable.at(-1);
    if (e.shiftKey && (document.activeElement === first || document.activeElement === dialog)) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && (document.activeElement === last || document.activeElement === dialog)) { e.preventDefault(); first.focus(); }
  });
})();

// Local presentation state only; never persists financial or category data.
(() => {
 const $=s=>document.querySelector(s);
 const toast=text=>{const host=document.querySelector('body > .toast-host');host.textContent='';const el=document.createElement('div');el.className='toast';el.textContent=text;host.append(el);setTimeout(()=>el.remove(),3500)};
 document.addEventListener('click',e=>{const b=e.target.closest('[data-demo-action]');if(b)toast('Превью: '+b.dataset.demoAction+'. Данные не изменены.');const chip=e.target.closest('[data-chip]');if(chip){chip.parentElement.querySelectorAll('[data-chip]').forEach(x=>{x.classList.toggle('tonal',x===chip);x.classList.toggle('outline',x!==chip);x.setAttribute('aria-pressed',String(x===chip))})}const nav=e.target.closest('[data-nav-demo]');if(nav)toast('Образец навигации: '+nav.textContent.trim());});
 const listState=$('#list-state');
 function renderList(){if(!listState)return;const v=listState.value,feedback=$('#list-feedback');$('#category-list').hidden=['loading','error','empty'].includes(v);$('#create-link').hidden=['loading','refresh','error','stale','readonly'].includes(v);feedback.hidden=v==='ready'||v==='readonly';feedback.className=['loading','error','empty'].includes(v)?'empty-state':'';const msg=$('#list-error')?.value||'Не удалось подключиться к серверу';feedback.innerHTML=v==='empty'?'Активных категорий пока нет':v==='loading'?'<span class="spinner" aria-label="Загрузка"></span>':v==='refresh'?'<span class="spinner" aria-label="Обновление"></span>':v==='error'||v==='stale'?'<div class="notice error">'+msg+'</div><button class="btn outline" id="retry">Повторить</button>':'';$('#retry')?.addEventListener('click',()=>{listState.value='refresh';renderList()});document.querySelectorAll('.category-row').forEach(a=>{a.setAttribute('aria-disabled',String(v==='readonly'));a.tabIndex=v==='readonly'?-1:0})}
 listState?.addEventListener('change',renderList);$('#list-error')?.addEventListener('change',renderList);$('#sync-state')?.addEventListener('change',e=>{const p=$('#sync-status');p.hidden=!e.target.value;p.textContent=e.target.value});document.addEventListener('click',e=>{if(e.target.closest('.category-row')&&listState?.value==='readonly')e.preventDefault()});const content=$('#category-content');let startY=0;content?.addEventListener('touchstart',e=>startY=e.touches[0].clientY,{passive:true});content?.addEventListener('touchend',e=>{if(e.changedTouches[0].clientY-startY>90){listState.value='refresh';renderList()}},{passive:true});
 const editor=$('.editor');let kind='expense',originalName='Продукты';
 const initialColors=['#C62828','#AD1457','#8E24AA','#673AB7','#3949AB','#1565C0','#0077A3','#C2410C'];
 function renderInitial(element, name) {
   const label = name.trim() || 'Новая категория';
   const key = label.toLowerCase();
   let hash = 0;
   for (let i = 0; i < key.length; i++) hash = (Math.imul(31, hash) + key.charCodeAt(i)) | 0;
   element.textContent = Array.from(label)[0].toUpperCase();
   element.style.backgroundColor = initialColors[(hash % initialColors.length + initialColors.length) % initialColors.length];
 }
 document.querySelectorAll('[data-category-initial]').forEach(el=>renderInitial(el,el.dataset.categoryInitial));
 let originalIcon=editor?.dataset.creating==='true'?'initial':'groceries', selectedIcon=originalIcon;
 function renderEditorIcon() {
   const name=$('#category-name')?.value;
   const current=$('.icon-select .category-art');
   if(name===undefined || !current) return;
   document.querySelectorAll('[data-pick]').forEach(el=>el.setAttribute('aria-pressed',String(el.dataset.pick===selectedIcon)));
   document.querySelectorAll('[data-pick="initial"] .category-initial').forEach(el=>renderInitial(el,name));
   const automatic=selectedIcon==='initial'||selectedIcon==='unknown';
   const art=document.createElement(automatic?'span':'img');
   art.className=automatic?'category-art category-initial':'category-art';
   if(automatic){
     art.setAttribute('aria-hidden','true');
     renderInitial(art,name);
   } else {
     art.src='assets/ic_category_'+selectedIcon+'.png';
     art.alt='';
   }
   current.replaceWith(art);
   $('#chosen-icon-label').textContent=$('[data-pick="'+selectedIcon+'"]')?.dataset.label||'Буква категории';
 }
 function updateSave(){if(!editor)return;const creating=editor.dataset.creating==='true';const mode=$('#editor-state').value;if(!$('#category-name'))return;const name=$('#category-name').value.trim();$('#save-category').disabled=mode==='saving'||!name||(!creating&&name===originalName&&selectedIcon===originalIcon);renderEditorIcon()}
 document.addEventListener('click',e=>{const p=e.target.closest('[data-pick]');if(p){document.querySelectorAll('[data-pick]').forEach(x=>x.setAttribute('aria-pressed',String(x===p)));if(editor){selectedIcon=p.dataset.pick;updateSave()}else toast('Выбрана иконка: '+p.dataset.label)}const k=e.target.closest('[data-kind]');if(k&&!k.disabled){kind=k.dataset.kind;document.querySelectorAll('[data-kind]').forEach(x=>{const active=x===k;x.setAttribute('aria-pressed',String(active));x.classList.toggle('tonal',active);x.classList.toggle('outline',!active);x.textContent=(active?'✓ ':'')+(x.dataset.kind==='expense'?'Расход':'Доход')})}});
 $('#category-name')?.addEventListener('input',updateSave);
 $('#error-state')?.addEventListener('change',e=>{const el=$('#editor-error');if(!el){if($('#editor-state')?.value==='initerror')editor.textContent=e.target.value||'Не удалось выполнить действие с категорией';return}el.hidden=!e.target.value;el.textContent='⚠ '+e.target.value});
 if(editor){const initial=editor.innerHTML;$('#editor-state').addEventListener('change',e=>{const mode=e.target.value;const creating=editor.dataset.creating==='true';editor.innerHTML=initial;originalIcon=creating?'initial':mode==='other'?'unknown':mode==='income'?'salary':'groceries';selectedIcon=originalIcon;kind=mode==='income'?'income':'expense';$('.appbar a').removeAttribute('aria-disabled');if(mode==='initerror'){editor.innerHTML='<p class="error-text">'+($('#error-state').value||'Не удалось выполнить действие с категорией')+'</p>';return}if(mode==='loading'){editor.innerHTML='<div class="empty-state"><span class="spinner" aria-label="Загрузка"></span></div>';return}if(mode==='forbidden'){editor.innerHTML='<p class="error-text">Управлять категориями может только активный OWNER</p>';return}originalName=mode==='other'?'Другое':mode==='income'?'Зарплата':'Продукты';$('#category-name').value=creating?'':originalName;$('#category-name').addEventListener('input',updateSave);$('#category-name').disabled=mode==='other'||mode==='saving';if(mode==='income'){document.querySelectorAll('[data-kind]').forEach(x=>{const active=x.dataset.kind==='income';x.textContent=(active?'✓ ':'')+(active?'Доход':'Расход');x.setAttribute('aria-pressed',String(active));x.classList.toggle('tonal',active);x.classList.toggle('outline',!active)});$('#delete-category').hidden=true}if(mode==='other')$('#delete-category').disabled=true;if(mode==='saving'){editor.querySelectorAll('button,input').forEach(x=>x.disabled=true);$('#save-category').innerHTML='<span class="spinner"></span><span>Сохранение…</span>';$('.appbar a').setAttribute('aria-disabled','true')}else $('.appbar a').removeAttribute('aria-disabled');updateSave()});if(new URLSearchParams(location.search).get('kind')==='income'){$('#editor-state').value='income';$('#editor-state').dispatchEvent(new Event('change'))}}
 document.addEventListener('click',e=>{if(e.target.closest('.appbar a[aria-disabled=true]'))e.preventDefault()});
 updateSave();
 // Localize shared starter theme labels.
 const localize=()=>{document.querySelectorAll('[data-theme-label]').forEach(el=>el.textContent=document.documentElement.dataset.theme==='dark'?'Тёмная тема':'Светлая тема');document.querySelectorAll('[data-theme-toggle]').forEach(el=>el.setAttribute('aria-label','Переключить тему'))};new MutationObserver(localize).observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});localize();
})();

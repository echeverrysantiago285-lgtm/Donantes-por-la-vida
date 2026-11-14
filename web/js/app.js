/* ============================================================
   LOGICA DE LA PAGINA (Donantes por la Vida)
   Orden del archivo: 1 Utilidades · 2 Navegacion · 3 Imagenes y videos
   · 4 Inicio (redes) · 5 Mitos · 6 Autoevaluacion · 7 Sitios y reserva
   · 8 Administrador
   ============================================================ */
const $ = id => document.getElementById(id);
const FORM = { 'Content-Type': 'application/x-www-form-urlencoded' };
const post = (url, datos) => fetch(url, { method: 'POST', headers: FORM, body: new URLSearchParams(datos) });

/* ---------- 2. Navegacion ---------- */
function ir(pantalla) {
  document.querySelectorAll('.v').forEach(s => s.classList.toggle('on', s.id === pantalla));
  document.querySelectorAll('nav button').forEach(b => b.classList.toggle('on', b.id === 'n-' + pantalla));
  $('menu').classList.remove('on'); scrollTo(0, 0);
  if (pantalla === 'jornadas') cargarJornadas();
}

/* ---------- 3. Imagenes y videos (se configuran en js/config.js) ---------- */
function pintarMedios() {
  document.querySelectorAll('.medio[data-medio]').forEach(caja => {
    const m = MEDIOS[caja.dataset.medio] || {};
    if (!m.src) { caja.textContent = 'Aquí va tu ' + (m.tipo === 'img' ? 'imagen' : 'video') + ' (' + caja.dataset.medio + ')'; return; }
    caja.textContent = '';
    let el;
    if (m.tipo === 'video') { el = document.createElement('video'); el.src = m.src; el.controls = true; el.playsInline = true; el.preload = 'metadata'; }
    else if (m.tipo === 'youtube') { el = document.createElement('iframe'); el.src = 'https://www.youtube.com/embed/' + encodeURIComponent(m.src); el.allowFullscreen = true; }
    else { el = document.createElement('img'); el.src = m.src; el.alt = caja.dataset.medio; }
    caja.appendChild(el);
  });
}

/* ---------- 4. Contacto y redes ---------- */
function pintarContacto() {
  const nombres = { instagram: 'IG', facebook: 'f', x: 'X', youtube: '▶' };
  Object.keys(REDES).filter(k => REDES[k]).forEach(k => {
    const a = document.createElement('a'); a.href = REDES[k]; a.target = '_blank'; a.rel = 'noopener'; a.textContent = nombres[k]; a.title = k; $('redes').appendChild(a);
  });
  [['telefono', 'Tel.'], ['correo', 'Correo'], ['direccion', 'Dirección']].forEach(([k, t]) => {
    if (CONTACTO[k]) { const d = document.createElement('div'); d.textContent = t + ': ' + CONTACTO[k]; $('contacto').appendChild(d); }
  });
}

/* ---------- 5. Mitos (toca para ver la realidad) ---------- */
const MITOS = [
  ['¿Donar sangre duele mucho?', 'Se siente como un pinchazo al introducir la aguja y puede haber molestias leves.'],
  ['¿Puedo contagiarme de alguna enfermedad al donar?', 'No. En centros autorizados se utilizan materiales estériles y desechables para cada persona.'],
  ['¿Solo debo donar cuando un familiar lo necesita?', 'No. La donación voluntaria y altruista mantiene reservas disponibles para pacientes que necesitan transfusiones.']];
function pintarMitos() {
  MITOS.forEach(([pregunta, real]) => {
    const d = document.createElement('div'); d.className = 'card mito';
    const h = document.createElement('h3'); h.textContent = pregunta;
    const p = document.createElement('div'); p.className = 'real'; p.textContent = 'Realidad: ' + real;
    const b = document.createElement('button'); b.className = 'btn bl'; b.style.marginTop = '10px'; b.textContent = 'Ver la realidad';
    b.onclick = () => { d.classList.toggle('on'); b.textContent = d.classList.contains('on') ? 'Ocultar' : 'Ver la realidad'; };
    d.append(h, b, p); $('listaMitos').appendChild(d);
  });
}

/* ---------- 6. Autoevaluacion ---------- */
let P = [], R = [], i = 0;
async function iniciarEval() {
  try { P = await (await fetch('/api/preguntas')).json(); } catch (e) { alert('No se pudo cargar la autoevaluación. ¿Está encendido el servidor?'); return; }
  R = new Array(P.length).fill(-1); i = 0; ir('eval'); pintaPregunta();
}
function pintaPregunta() {
  $('qn').textContent = 'Pregunta ' + (i + 1) + ' de ' + P.length; $('qb').style.width = ((i + 1) / P.length * 100) + '%'; $('qt').textContent = P[i].t; $('qo').innerHTML = '';
  P[i].o.forEach((texto, k) => { const b = document.createElement('button'); b.className = 'opc' + (R[i] === k ? ' sel' : ''); b.textContent = texto; b.onclick = () => { R[i] = k; pintaPregunta(); }; $('qo').appendChild(b); });
  $('sig').textContent = i === P.length - 1 ? 'Ver resultado' : 'Siguiente'; $('sig').disabled = R[i] < 0;
}
function atras() { if (i > 0) { i--; pintaPregunta(); } else ir('inicio'); }
async function sigue() {
  if (i < P.length - 1) { i++; pintaPregunta(); return; }
  const r = await (await post('/api/evaluar', { r: R.join(',') })).json();
  if (r.error) { alert(r.error); return; }
  $('rt').textContent = r.titulo; $('rm').textContent = r.mensaje;
  $('rl').innerHTML = ''; r.motivos.forEach(m => { const li = document.createElement('li'); li.textContent = m; $('rl').appendChild(li); });
  ir('resultado');
}

/* ---------- 7. Sitios de donacion y reserva ---------- */
let J = [];
async function cargarJornadas() {
  try { J = await (await fetch('/api/jornadas')).json(); } catch (e) { $('jl').textContent = 'No se pudieron cargar los sitios.'; return; }
  $('jl').innerHTML = '';
  J.forEach(j => {
    const d = document.createElement('div'); d.className = 'card'; d.style.margin = '12px 0';
    const t = document.createElement('h3'); t.textContent = j.titulo;
    const f = document.createElement('div'); f.className = 'sub'; f.textContent = j.fecha + ' · ' + j.hora;
    const l = document.createElement('div'); l.className = 'sub'; l.textContent = j.lugar;
    const mapa = document.createElement('a'); mapa.textContent = 'Ver en el mapa'; mapa.target = '_blank'; mapa.rel = 'noopener';
    mapa.href = 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(j.lugar); mapa.style.cssText = 'color:#D71920;font-weight:bold;font-size:13px';
    const b = document.createElement('button'); b.className = 'btn'; b.style.marginTop = '10px'; b.textContent = 'Reservar'; b.onclick = () => reservar(j.id);
    d.append(t, f, l, mapa, document.createElement('br'), b); $('jl').appendChild(d);
  });
}
function reservar(id) {
  $('sj').innerHTML = ''; J.forEach(j => { const o = document.createElement('option'); o.value = j.id; o.textContent = j.titulo + ' - ' + j.fecha; $('sj').appendChild(o); });
  $('sj').value = id; $('se').style.display = 'none'; ir('reservar');
}
async function enviar() {
  const n = $('sn').value.trim(), c = $('sc').value.trim(), e = $('se');
  if (!n || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(c)) { e.textContent = !n ? 'Escribe tu nombre.' : 'Escribe un correo válido.'; e.style.display = 'block'; return; }
  const r = await (await post('/api/solicitud', { jornada: $('sj').value, nombre: n, correo: c, recordatorio: $('sr').checked ? '1' : '0' })).json();
  if (!r.ok) { e.textContent = r.error; e.style.display = 'block'; return; }
  $('gm').textContent = r.recordatorio ? 'Programamos un recordatorio para tu correo (simulado en este prototipo).' : 'No activaste recordatorios.';
  $('sn').value = ''; $('sc').value = ''; $('sr').checked = false; ir('gracias');
}

/* ---------- 8. Administrador (menu ☰) ---------- */
let ADM = false;
async function estadoAdmin() { try { ADM = (await (await fetch('/api/admin/estado')).json()).admin; } catch (e) { ADM = false; } }
async function alternarMenu() {
  await estadoAdmin(); const m = $('menu'); m.innerHTML = '';
  const add = (t, f) => { const b = document.createElement('button'); b.textContent = t; b.onclick = f; m.appendChild(b); };
  add('Inicio', () => ir('inicio'));
  if (ADM) { add('Panel de administración', () => { ir('admin'); cargarAdmin(); }); add('Cerrar sesión', adminSalir); }
  else add('Acceso administrador', () => { $('ae').style.display = 'none'; ir('alogin'); });
  m.classList.toggle('on');
}
async function adminEntrar() {
  const r = await (await post('/api/admin/login', { usuario: $('au').value.trim(), clave: $('ap').value })).json();
  if (!r.ok) { $('ae').textContent = r.error; $('ae').style.display = 'block'; return; }
  $('ap').value = ''; await estadoAdmin(); ir('admin'); cargarAdmin();
}
async function adminSalir() { await post('/api/admin/logout', {}); await estadoAdmin(); ir('inicio'); }
async function cargarAdmin() {
  const js = await (await fetch('/api/jornadas')).json(); $('aj').innerHTML = '';
  js.forEach(j => {
    const f = document.createElement('div'); f.className = 'fila'; const s = document.createElement('span'); s.textContent = j.titulo + ' · ' + j.fecha + ' · ' + j.lugar;
    const b = document.createElement('button'); b.className = 'btn bl'; b.style.padding = '6px 12px'; b.textContent = 'Eliminar'; b.onclick = () => borrarJ(j.id); f.append(s, b); $('aj').appendChild(f);
  });
  const r = await fetch('/api/admin/solicitudes'); if (r.status === 401) { await estadoAdmin(); ir('alogin'); return; }
  const ss = await r.json(); $('as').textContent = ss.length ? '' : 'Aún no hay solicitudes.';
  ss.forEach(x => { const d = document.createElement('div'); d.className = 'sub'; d.style.margin = '8px 0'; d.textContent = x.nombre + ' · ' + x.correo + ' · ' + x.jornada + ' · recordatorio: ' + x.recordatorio + ' (' + x.fecha + ')'; $('as').appendChild(d); });
}
async function agregarJ() {
  const r = await post('/api/admin/jornada/agregar', { titulo: $('at').value, fecha: $('af').value, hora: $('ah').value, lugar: $('al').value });
  if (r.status === 401) { await estadoAdmin(); ir('alogin'); return; }
  const j = await r.json(), e = $('ag'); if (!j.ok) { e.textContent = j.error; e.style.display = 'block'; return; }
  e.style.display = 'none'; ['at', 'af', 'ah', 'al'].forEach(k => $(k).value = ''); cargarAdmin();
}
async function borrarJ(id) { if (confirm('¿Eliminar esta jornada?')) { await post('/api/admin/jornada/eliminar', { id }); cargarAdmin(); } }

/* ---------- Inicio de la pagina ---------- */
pintarMedios(); pintarContacto(); pintarMitos(); ir('inicio');

// Writes sales/products into the OneDrive workbook via Microsoft Graph (device-code sign-in).
const fs = require('fs'), path = require('path');
const CLIENT = process.env.MS_CLIENT_ID;
const FILE = process.env.ONEDRIVE_FILE || '/Global_Trio_Business.xlsx';
const TOKFILE = process.env.MS_TOKEN_FILE || path.join(path.dirname(process.env.DATA_FILE || path.join(__dirname, 'x')), 'ms.json');
const AUTH = 'https://login.microsoftonline.com/common/oauth2/v2.0';
const SCOPE = 'Files.ReadWrite offline_access';
let tok = {}; try { tok = JSON.parse(fs.readFileSync(TOKFILE, 'utf8')); } catch (e) {}
const state = { error: '', last: '', pending: null };
const saveTok = () => fs.writeFileSync(TOKFILE, JSON.stringify(tok));
const post = async (url, b) => (await fetch(url, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(b).toString() })).json();

async function startLogin() {
  if (!CLIENT) throw new Error('MS_CLIENT_ID is not set on the server');
  const d = await post(AUTH + '/devicecode', { client_id: CLIENT, scope: SCOPE });
  if (d.error) throw new Error(d.error_description || d.error);
  state.pending = { code: d.user_code, url: d.verification_uri };
  (async () => {
    const end = Date.now() + d.expires_in * 1000;
    while (Date.now() < end) {
      await new Promise(r => setTimeout(r, (d.interval || 5) * 1000));
      const t = await post(AUTH + '/token', { client_id: CLIENT, grant_type: 'urn:ietf:params:oauth:grant-type:device_code', device_code: d.device_code });
      if (t.access_token) { tok = { access: t.access_token, refresh: t.refresh_token, exp: Date.now() + t.expires_in * 1000 - 60000 }; saveTok(); state.pending = null; state.error = ''; return; }
      if (t.error && t.error !== 'authorization_pending' && t.error !== 'slow_down') { state.error = t.error_description || t.error; state.pending = null; return; }
    }
    state.pending = null; state.error = 'Sign-in code expired - try again';
  })();
  return { code: d.user_code, url: d.verification_uri };
}
async function token() {
  if (!tok.refresh) throw new Error('OneDrive not linked');
  if (tok.access && Date.now() < tok.exp) return tok.access;
  const t = await post(AUTH + '/token', { client_id: CLIENT, grant_type: 'refresh_token', refresh_token: tok.refresh, scope: SCOPE });
  if (!t.access_token) throw new Error(t.error_description || t.error || 'token refresh failed');
  tok = { access: t.access_token, refresh: t.refresh_token || tok.refresh, exp: Date.now() + t.expires_in * 1000 - 60000 }; saveTok();
  return tok.access;
}
async function g(method, rel, body) {
  const url = 'https://graph.microsoft.com/v1.0/me/drive/root:' + encodeURI(FILE) + ':/workbook' + rel;
  const r = await fetch(url, { method, headers: { Authorization: 'Bearer ' + await token(), 'content-type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error((j.error && j.error.message) || 'Graph ' + r.status);
  return j;
}
const rng = (sheet, a) => `/worksheets('${encodeURIComponent(sheet)}')/range(address='${a}')`;
// first-item row and size of each product section on the month tabs
const SEC = { 'Avroy Shlein Products|Sprays': [13, 8], 'Avroy Shlein Products|Roll-Ons': [23, 8], 'Avroy Shlein Products|Perfumes': [33, 8],
  'Inuka Products|Sprays': [46, 8], 'Inuka Products|Roll-Ons': [56, 8], 'Inuka Products|Perfumes': [66, 8], 'Shipping|N/A (Shipping)': [78, 10] };
const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];
const serial = s => Math.round(Date.UTC(+s.slice(0, 4), +s.slice(5, 7) - 1, +s.slice(8, 10)) / 864e5) + 25569;

async function freeRow(sheet, r1, r2, match) {
  const j = await g('GET', rng(sheet, `B${r1}:B${r2}`) + '?$select=values');
  const v = j.values.map(x => x[0]);
  if (match !== undefined) { const i = v.indexOf(match); if (i >= 0) return r1 + i; }
  const i = v.findIndex(x => x === '' || x === null);
  if (i < 0) throw new Error(`No free rows on ${sheet} (${r1}-${r2})`);
  return r1 + i;
}
const clear = (sheet, a) => g('POST', rng(sheet, a) + '/clear', { applyTo: 'Contents' });

async function pushSale(s, loc) {
  if (!SEC[s.category + '|' + s.sub]) return loc || null; // custom categories are not in the workbook layout
  const m = parseInt(s.date.slice(5, 7)) - 1, start = 7 + m * 151, end = start + 149, sheet = 'Customer Details';
  if (loc && (loc.row < start || loc.row > end || s.deleted)) { await clear(sheet, `A${loc.row}:H${loc.row}`); loc = null; }
  if (s.deleted) return null;
  const row = loc ? loc.row : await freeRow(sheet, start, end);
  await g('PATCH', rng(sheet, `A${row}:H${row}`), { values: [[serial(s.date), s.customer, s.phone ? "'" + s.phone : '', s.category, s.sub, s.product, s.qty, s.status]] });
  return { sheet, row };
}
async function pushProduct(p, loc) {
  const sec = SEC[p.category + '|' + p.sub]; if (!sec || MONTHS.indexOf(p.month) < 0) return loc || null;
  const [first, len] = sec;
  if (loc && (loc.sheet !== p.month || p.deleted)) { await clear(loc.sheet, `B${loc.row}:E${loc.row}`); loc = null; }
  if (p.deleted) return null;
  const row = loc ? loc.row : await freeRow(p.month, first, first + len - 1, p.name);
  await g('PATCH', rng(p.month, `B${row}:E${row}`), { values: [[p.name, p.qty, p.buy, p.sell]] });
  return { sheet: p.month, row };
}
let chain = Promise.resolve();
function enqueue(db, save, products, sales) {
  if (!tok.refresh) return 0;
  const jobs = [...products.map(r => [r, pushProduct]), ...sales.map(r => [r, pushSale])];
  chain = chain.then(async () => {
    for (const [rec, fn] of jobs) {
      try { const loc = await fn(rec, db.xl[rec.id]); if (loc) db.xl[rec.id] = loc; else delete db.xl[rec.id]; state.error = ''; state.last = new Date().toISOString().slice(0, 16).replace('T', ' ') + ' UTC'; }
      catch (e) { state.error = e.message; }
    }
    save();
  });
  return jobs.length;
}
const status = () => ({ configured: !!CLIENT, linked: !!tok.refresh, pending: state.pending, error: state.error, last: state.last });
module.exports = { startLogin, enqueue, status };

// Global Trio sync server. Run: API_KEY=secret PORT=3000 node server.js
// Optional Excel link: MS_CLIENT_ID=... ONEDRIVE_FILE=/Global_Trio_Business.xlsx
const http = require('http'), fs = require('fs'), path = require('path');
const excel = require('./excel');
const PORT = process.env.PORT || 3000;
const API_KEY = process.env.API_KEY || 'change-me';
const FILE = process.env.DATA_FILE || path.join(__dirname, 'data.json');
let db = { products: {}, sales: {}, cats: {}, exps: {}, slog: {}, alog: {}, notes: {}, prof: {}, xl: {}, epoch: Date.now() };
try { db = JSON.parse(fs.readFileSync(FILE, 'utf8')); } catch (e) {}
db.xl = db.xl || {}; db.cats = db.cats || {}; db.exps = db.exps || {}; db.slog = db.slog || {}; db.alog = db.alog || {}; db.notes = db.notes || {}; db.prof = db.prof || {}; db.epoch = db.epoch || Date.now();
const save = () => fs.writeFileSync(FILE, JSON.stringify(db));
const cors = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'content-type,x-api-key', 'Access-Control-Allow-Methods': 'GET,POST,OPTIONS' };
const send = (res, code, obj) => { res.writeHead(code, { 'Content-Type': 'application/json', ...cors }); res.end(JSON.stringify(obj)); };

function merge(table, incoming) {
  const accepted = [];
  for (const r of incoming || []) {
    if (!r || !r.id) continue;
    const cur = table[r.id];
    if (!cur || (r.updatedAt || 0) >= (cur.updatedAt || 0)) { delete r.dirty; r.serverAt = Date.now(); table[r.id] = r; accepted.push(r); }
  }
  return accepted;
}
http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return send(res, 204, {});
  if (req.url === '/health') return send(res, 200, { ok: true });
  if (req.headers['x-api-key'] !== API_KEY) return send(res, 401, { error: 'bad key' });
  try {
    if (req.url === '/api/sync' && req.method === 'POST') {
      let body = '';
      req.on('data', c => { body += c; if (body.length > 5e7) req.destroy(); });
      return req.on('end', () => {
        try {
          const p = JSON.parse(body || '{}');
          const ap = merge(db.products, p.products), as = merge(db.sales, p.sales); merge(db.cats, p.cats); merge(db.exps, p.exps); merge(db.slog, p.slog); merge(db.alog, p.alog); merge(db.notes, p.notes); merge(db.prof, p.prof);
          save(); excel.enqueue(db, save, ap, as);
          const since = p.since || 0, pick = t => Object.values(t).filter(r => (r.serverAt || 0) > since);
          send(res, 200, { products: pick(db.products), sales: pick(db.sales), cats: pick(db.cats), exps: pick(db.exps), slog: pick(db.slog), alog: pick(db.alog), notes: pick(db.notes), prof: pick(db.prof), epoch: db.epoch, serverTime: Date.now() });
        } catch (e) { send(res, 400, { error: String(e) }); }
      });
    }
    if (req.url === '/ms/login' && req.method === 'POST') return send(res, 200, await excel.startLogin());
    if (req.url === '/ms/status') return send(res, 200, excel.status());
    if (req.url === '/ms/pushall' && req.method === 'POST') {
      const todo = t => Object.values(t).filter(r => !r.deleted && !db.xl[r.id]);
      return send(res, 200, { queued: excel.enqueue(db, save, todo(db.products), todo(db.sales)) });
    }
    send(res, 404, { error: 'not found' });
  } catch (e) { send(res, 500, { error: e.message }); }
}).listen(PORT, () => console.log('Global Trio server on :' + PORT));

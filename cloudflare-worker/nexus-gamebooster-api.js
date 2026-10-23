const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "access-control-allow-origin": "*", "access-control-allow-methods": "GET,POST,PATCH,DELETE,OPTIONS", "access-control-allow-headers": "Content-Type,Authorization,X-Device-Id" } });
const now = () => Math.floor(Date.now() / 1000);
const id = () => crypto.randomUUID();
const enc = new TextEncoder();
async function sha256(value) { const b = await crypto.subtle.digest("SHA-256", enc.encode(value)); return [...new Uint8Array(b)].map(x => x.toString(16).padStart(2,"0")).join(""); }
function randomKey() { const bytes = crypto.getRandomValues(new Uint8Array(24)); return "NGB-" + [...bytes].map(b => b.toString(16).padStart(2,"0")).join("").toUpperCase(); }
function cleanText(v, max=200) { return String(v ?? "").replace(/[\u0000-\u001F\u007F]/g, " ").trim().slice(0,max); }
function validDns(v) { return /^(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)$/.test(v) || /^[a-fA-F0-9:]{3,39}$/.test(v); }
function validWg(v) { return typeof v === "string" && v.length > 0 && v.length <= 128000 && /^\s*\[Interface\]/mi.test(v) && /^\s*\[Peer\]/mi.test(v); }
async function body(req) { try { return await req.json(); } catch { throw new Error("بدنه درخواست JSON معتبر نیست"); } }
function admin(req, env) { const auth = req.headers.get("Authorization") || ""; return !!env.ADMIN_TOKEN && auth === `Bearer ${env.ADMIN_TOKEN}`; }
async function audit(env, action, actor, type, recordId, detail="") { await env.DB.prepare("INSERT INTO audit_log (id,at,action,actor,record_type,record_id,detail) VALUES (?,?,?,?,?,?,?)").bind(id(),now(),action,actor,type,recordId,cleanText(detail,500)).run(); }
async function requireAdmin(req, env) { if (!admin(req, env)) return json({ error: "unauthorized", message: "دسترسی ادمین معتبر نیست" }, 401); return null; }
export default {
 async fetch(req, env) {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: { "access-control-allow-origin":"*", "access-control-allow-methods":"GET,POST,PATCH,DELETE,OPTIONS", "access-control-allow-headers":"Content-Type,Authorization,X-Device-Id" } });
  const u = new URL(req.url), p = u.pathname.replace(/\/+$/, "") || "/";
  try {
   if (p === "/api/health" && req.method === "GET") return json({ ok:true, app:env.APP_NAME || "Nexus Game Booster", time:now() });

   // App endpoints
   if (p === "/api/v1/license/activate" && req.method === "POST") {
    const b = await body(req), key = cleanText(b.key,100).toUpperCase(), device = cleanText(b.deviceId || req.headers.get("X-Device-Id"),200);
    if (!key || device.length < 8) return json({ valid:false, code:"BAD_REQUEST", message:"کلید و شناسه دستگاه لازم است" },400);
    const kh = await sha256(key), dh = await sha256(device), row = await env.DB.prepare("SELECT * FROM license_keys WHERE key_hash=?").bind(kh).first();
    if (!row) return json({ valid:false, code:"NOT_FOUND", message:"کلید معتبر نیست" },403);
    if (!row.active || row.revoked_at) return json({ valid:false, code:"REVOKED", message:"این کلید غیرفعال شده است" },403);
    if (row.expires_at <= now()) return json({ valid:false, code:"EXPIRED", message:"اعتبار کلید به پایان رسیده است" },403);
    const existing = await env.DB.prepare("SELECT id FROM activations WHERE license_id=? AND device_hash=?").bind(row.id,dh).first();
    if (!existing) {
     // Single conditional INSERT: D1/SQLite serializes writes, preventing two concurrent
     // first activations from both consuming the last slot.
     const inserted = await env.DB.prepare("INSERT OR IGNORE INTO activations(id,license_id,device_hash,created_at,last_seen_at) SELECT ?,?,?,?,? WHERE (SELECT COUNT(*) FROM activations WHERE license_id=?) < (SELECT max_activations FROM license_keys WHERE id=?)")
       .bind(id(),row.id,dh,now(),now(),row.id,row.id).run();
     const raced = await env.DB.prepare("SELECT id FROM activations WHERE license_id=? AND device_hash=?").bind(row.id,dh).first();
     if (!raced) return json({ valid:false, code:"CAPACITY_REACHED", message:"ظرفیت دستگاه‌های این کلید تکمیل شده است" },403);
     await env.DB.prepare("UPDATE activations SET last_seen_at=? WHERE license_id=? AND device_hash=?").bind(now(),row.id,dh).run();
    } else await env.DB.prepare("UPDATE activations SET last_seen_at=? WHERE license_id=? AND device_hash=?").bind(now(),row.id,dh).run();
    const token = crypto.randomUUID() + crypto.randomUUID();
    // The activation token is intentionally short-lived; clients should re-activate when it expires.
    await env.DB.prepare("INSERT INTO app_settings(key,value,updated_at) VALUES(?,?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_at=excluded.updated_at").bind(`session:${await sha256(token)}`, JSON.stringify({licenseId:row.id,deviceHash:dh,expiresAt:now()+86400}),now()).run();
    return json({ valid:true, token, createdAt:row.created_at, expiresAt:row.expires_at, sessionExpiresAt:now()+86400, message:"کلید تأیید شد" });
   }
   if (p === "/api/v1/sync" && req.method === "GET") {
    const token = (req.headers.get("Authorization")||"").replace(/^Bearer\s+/i,"");
    if (!token) return json({ error:"unauthorized", message:"ابتدا کلید را فعال کنید" },401);
    const session = await env.DB.prepare("SELECT value FROM app_settings WHERE key=?").bind(`session:${await sha256(token)}`).first();
    if (!session) return json({ error:"unauthorized", message:"نشست معتبر نیست؛ دوباره کلید را وارد کنید" },401);
    const s = JSON.parse(session.value);
    const requestDevice = cleanText(req.headers.get("X-Device-Id"),200);
    if (!requestDevice || await sha256(requestDevice) !== s.deviceHash) return json({error:"device_mismatch",message:"این نشست به دستگاه دیگری تعلق دارد"},403);
    if (s.expiresAt <= now()) { await env.DB.prepare("DELETE FROM app_settings WHERE key=?").bind(`session:${await sha256(token)}`).run(); return json({error:"session_expired",message:"نشست منقضی شده؛ دوباره وارد شوید"},401); }
    const license = await env.DB.prepare("SELECT active,created_at,expires_at,revoked_at FROM license_keys WHERE id=?").bind(s.licenseId).first();
    if (!license || !license.active || license.revoked_at || license.expires_at <= now()) return json({error:"license_invalid",message:"کلید غیرفعال یا منقضی شده است"},403);
    const deviceStillAuthorized = await env.DB.prepare("SELECT id FROM activations WHERE license_id=? AND device_hash=?").bind(s.licenseId,s.deviceHash).first();
    if (!deviceStillAuthorized) return json({error:"device_revoked",message:"دسترسی این دستگاه توسط مدیر لغو شده است"},403);
    const dns = await env.DB.prepare("SELECT id,name,address,description,category,updated_at FROM dns_servers WHERE active=1 ORDER BY name").all();
    const wg = await env.DB.prepare("SELECT id,name,description,country,config_text,updated_at FROM wireguard_configs WHERE active=1 ORDER BY name").all();
    const settings = await env.DB.prepare("SELECT key,value FROM app_settings WHERE key IN ('sync_version','ping_page_message')").all();
    return json({ ok:true, serverTime:now(), licenseCreatedAt:license.created_at || null, licenseExpiresAt:license.expires_at, version:Object.fromEntries(settings.results.map(x=>[x.key,x.value])), dns:dns.results, wireguard:wg.results });
   }

   // Admin endpoints: all protected by ADMIN_TOKEN stored as a Worker secret.
   if (p.startsWith("/api/admin/")) { const denied = await requireAdmin(req,env); if (denied) return denied; }
   if (p === "/api/admin/keys" && req.method === "GET") {
    const r = await env.DB.prepare("SELECT id,key_hint,label,created_at,expires_at,max_activations,active,revoked_at,(SELECT COUNT(*) FROM activations a WHERE a.license_id=license_keys.id) AS used FROM license_keys ORDER BY created_at DESC LIMIT 500").all(); return json({items:r.results});
   }
   if (p === "/api/admin/keys" && req.method === "POST") {
    const b = await body(req), days = Math.max(1,Math.min(3650,Number(b.days)||30)), max = Math.max(1,Math.min(10000,Number(b.maxActivations)||1)), label=cleanText(b.label,100), key=randomKey(), keyHash=await sha256(key), rid=id(), t=now();
    await env.DB.prepare("INSERT INTO license_keys(id,key_hash,key_hint,label,created_at,expires_at,max_activations,active) VALUES(?,?,?,?,?,?,?,1)").bind(rid,keyHash,key.slice(-8),label,t,t+days*86400,max).run();
    await audit(env,"create","admin","license",rid,`days=${days};maxActivations=${max}`);
    return json({item:{id:rid,key,label,createdAt:t,expiresAt:t+days*86400,maxActivations:max,active:true,used:0}},201);
   }
   const keyMatch=p.match(/^\/api\/admin\/keys\/([\w-]+)$/);
   if (keyMatch && req.method === "PATCH") { const b=await body(req), rid=keyMatch[1], active=b.active===false?0:1; await env.DB.prepare("UPDATE license_keys SET active=?,revoked_at=? WHERE id=?").bind(active,active?null:now(),rid).run(); await audit(env,active?"enable":"revoke","admin","license",rid); return json({ok:true}); }
   if (keyMatch && req.method === "DELETE") { const rid=keyMatch[1]; const row=await env.DB.prepare("SELECT id,label FROM license_keys WHERE id=?").bind(rid).first(); if(!row)return json({error:"not_found",message:"کلید پیدا نشد"},404); await env.DB.prepare("DELETE FROM activations WHERE license_id=?").bind(rid).run(); await env.DB.prepare("DELETE FROM license_keys WHERE id=?").bind(rid).run(); await audit(env,"delete","admin","license",rid,cleanText(row.label||"",100)); return json({ok:true}); }
   const keyDevicesMatch=p.match(/^\/api\/admin\/keys\/([\w-]+)\/devices$/);
   if (keyDevicesMatch && req.method === "GET") {
    const r=await env.DB.prepare("SELECT id,device_hash,created_at,last_seen_at FROM activations WHERE license_id=? ORDER BY created_at DESC").bind(keyDevicesMatch[1]).all();
    return json({items:r.results.map(x=>({id:x.id,deviceHint:x.device_hash.slice(0,10),created_at:x.created_at,last_seen_at:x.last_seen_at}))});
   }
   const deviceDeleteMatch=p.match(/^\/api\/admin\/keys\/([\w-]+)\/devices\/([\w-]+)$/);
   if (deviceDeleteMatch && req.method === "DELETE") {
    const r=await env.DB.prepare("DELETE FROM activations WHERE id=? AND license_id=?").bind(deviceDeleteMatch[2],deviceDeleteMatch[1]).run();
    if (!r.meta?.changes) return json({error:"not_found",message:"دستگاه پیدا نشد"},404);
    await audit(env,"revoke_device","admin","activation",deviceDeleteMatch[2]);
    return json({ok:true});
   }
   if (p === "/api/admin/dns" && req.method === "GET") { const r=await env.DB.prepare("SELECT * FROM dns_servers ORDER BY updated_at DESC").all(); return json({items:r.results}); }
   if (p === "/api/admin/dns" && req.method === "POST") { const b=await body(req), name=cleanText(b.name,80), address=cleanText(b.address,100); if(!name||!validDns(address)) return json({error:"invalid_dns",message:"نام و آدرس IPv4/IPv6 معتبر وارد کنید"},400); const rid=id(); await env.DB.prepare("INSERT INTO dns_servers(id,name,address,description,category,active,updated_at) VALUES(?,?,?,?,?,?,?)").bind(rid,name,address,cleanText(b.description,300),cleanText(b.category,40)||"عمومی",b.active===false?0:1,now()).run(); await audit(env,"create","admin","dns",rid,name); return json({ok:true,id:rid},201); }
   const dnsMatch=p.match(/^\/api\/admin\/dns\/([\w-]+)$/);
   if(dnsMatch && req.method==="PATCH"){const b=await body(req),rid=dnsMatch[1]; const old=await env.DB.prepare("SELECT * FROM dns_servers WHERE id=?").bind(rid).first(); if(!old)return json({error:"not_found"},404); const name=cleanText(b.name??old.name,80),address=cleanText(b.address??old.address,100);if(!validDns(address)||!name)return json({error:"invalid_dns"},400);await env.DB.prepare("UPDATE dns_servers SET name=?,address=?,description=?,category=?,active=?,updated_at=? WHERE id=?").bind(name,address,cleanText(b.description??old.description,300),cleanText(b.category??old.category,40),b.active===undefined?old.active:(b.active?1:0),now(),rid).run();await audit(env,"update","admin","dns",rid,name);return json({ok:true});}
   if(dnsMatch && req.method==="DELETE"){await env.DB.prepare("DELETE FROM dns_servers WHERE id=?").bind(dnsMatch[1]).run();await audit(env,"delete","admin","dns",dnsMatch[1]);return json({ok:true});}
   if(p==="/api/admin/wireguard" && req.method==="GET"){const r=await env.DB.prepare("SELECT id,name,description,country,active,updated_at FROM wireguard_configs ORDER BY updated_at DESC").all();return json({items:r.results});}
   if(p==="/api/admin/wireguard" && req.method==="POST"){const b=await body(req),name=cleanText(b.name,80),config=String(b.configText||"");if(!name||!validWg(config))return json({error:"invalid_config",message:"کانفیگ باید شامل [Interface] و [Peer] باشد"},400);const rid=id();await env.DB.prepare("INSERT INTO wireguard_configs(id,name,description,country,config_text,active,updated_at) VALUES(?,?,?,?,?,?,?)").bind(rid,name,cleanText(b.description,300),cleanText(b.country,80),config,b.active===false?0:1,now()).run();await audit(env,"create","admin","wireguard",rid,name);return json({ok:true,id:rid},201);}
   const wgMatch=p.match(/^\/api\/admin\/wireguard\/([\w-]+)$/);
   if(wgMatch && req.method==="PATCH"){const b=await body(req),rid=wgMatch[1],old=await env.DB.prepare("SELECT * FROM wireguard_configs WHERE id=?").bind(rid).first();if(!old)return json({error:"not_found"},404);const config=b.configText===undefined?old.config_text:String(b.configText);if(!validWg(config))return json({error:"invalid_config"},400);await env.DB.prepare("UPDATE wireguard_configs SET name=?,description=?,country=?,config_text=?,active=?,updated_at=? WHERE id=?").bind(cleanText(b.name??old.name,80),cleanText(b.description??old.description,300),cleanText(b.country??old.country,80),config,b.active===undefined?old.active:(b.active?1:0),now(),rid).run();await audit(env,"update","admin","wireguard",rid,cleanText(b.name??old.name,80));return json({ok:true});}
   if(wgMatch && req.method==="DELETE"){await env.DB.prepare("DELETE FROM wireguard_configs WHERE id=?").bind(wgMatch[1]).run();await audit(env,"delete","admin","wireguard",wgMatch[1]);return json({ok:true});}
   if(p==="/api/admin/audit" && req.method==="GET"){const r=await env.DB.prepare("SELECT at,action,actor,record_type,record_id,detail FROM audit_log ORDER BY at DESC LIMIT 300").all();return json({items:r.results});}
   if(p==="/api/admin/audit" && req.method==="DELETE"){await env.DB.prepare("DELETE FROM audit_log").run();return json({ok:true});}
   if(p==="/api/admin/sync-version" && req.method==="POST"){const version=String(now());await env.DB.prepare("INSERT INTO app_settings(key,value,updated_at) VALUES('sync_version',?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_at=excluded.updated_at").bind(version,now()).run();await audit(env,"publish","admin","settings","sync_version",version);return json({ok:true,version});}
   return json({error:"not_found",message:"مسیر API پیدا نشد"},404);
  } catch(e) { return json({error:"server_error",message:"خطای سرور رخ داد"},500); }
 }
};

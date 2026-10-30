const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "access-control-allow-origin": "*", "access-control-allow-methods": "GET,POST,PATCH,DELETE,OPTIONS", "access-control-allow-headers": "Content-Type,Authorization,X-Device-Id" } });
const now = () => Math.floor(Date.now() / 1000);
const id = () => crypto.randomUUID();
const enc = new TextEncoder();
async function sha256(value) { const b = await crypto.subtle.digest("SHA-256", enc.encode(value)); return [...new Uint8Array(b)].map(x => x.toString(16).padStart(2,"0")).join(""); }
function randomKey() {
  // 12 characters total: BGT_ + 8 random uppercase letters/digits (excluding ambiguous characters).
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  let suffix = "";
  for (const b of bytes) suffix += alphabet[b % alphabet.length];
  return "BGT_" + suffix;
}
function accountCode(){const a="23456789ABCDEFGHJKLMNPQRSTUVWXYZ";const b=crypto.getRandomValues(new Uint8Array(5));return "User-"+[...b].map(x=>a[x%a.length]).join("");}
function cleanText(v, max=200) { return String(v ?? "").replace(/[\u0000-\u001F\u007F]/g, " ").trim().slice(0,max); }
function validDns(v) { return /^(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)$/.test(v) || /^[a-fA-F0-9:]{3,39}$/.test(v); }
function validWg(v) { return typeof v === "string" && v.length > 0 && v.length <= 128000 && /^\s*\[Interface\]/mi.test(v) && /^\s*\[Peer\]/mi.test(v); }
async function body(req) { try { return await req.json(); } catch { throw new Error("بدنه درخواست JSON معتبر نیست"); } }
function admin(req, env) { const auth = req.headers.get("Authorization") || ""; return !!env.ADMIN_TOKEN && auth === `Bearer ${env.ADMIN_TOKEN}`; }
async function audit(env, action, actor, type, recordId, detail="") { await env.DB.prepare("INSERT INTO audit_log (id,at,action,actor,record_type,record_id,detail) VALUES (?,?,?,?,?,?,?)").bind(id(),now(),action,actor,type,recordId,cleanText(detail,500)).run(); }
async function requireAdmin(req, env) { if (!admin(req, env)) return json({ error: "unauthorized", message: "دسترسی ادمین معتبر نیست" }, 401); return null; }
async function appSession(req, env) {
 const token=(req.headers.get("Authorization")||"").replace(/^Bearer\s+/i,"");
 if(!token) return {error:json({error:"unauthorized",message:"ابتدا کلید را فعال کنید"},401)};
 const sessionRow=await env.DB.prepare("SELECT value FROM app_settings WHERE key=?").bind(`session:${await sha256(token)}`).first();
 if(!sessionRow) return {error:json({error:"unauthorized",message:"نشست معتبر نیست؛ دوباره کلید را وارد کنید"},401)};
 const session=JSON.parse(sessionRow.value), device=cleanText(req.headers.get("X-Device-Id"),200);
 if(!device || await sha256(device)!==session.deviceHash) return {error:json({error:"device_mismatch",message:"این نشست به دستگاه دیگری تعلق دارد"},403)};
 if(session.expiresAt<=now()) return {error:json({error:"session_expired",message:"نشست منقضی شده؛ دوباره وارد شوید"},401)};
 const license=await env.DB.prepare("SELECT id,key_hint,label,key_type,active,expires_at,revoked_at FROM license_keys WHERE id=?").bind(session.licenseId).first();
 if(!license||!license.active||license.revoked_at||license.expires_at<=now()) return {error:json({error:"license_invalid",code:"EXPIRED",keyType:license?.key_type||"public",message:license?.key_type==="private"?"اعتبار کلید شخصی شما به پایان رسیده است.":(license?.key_type==="test"?"مهلت کلید آزمایشی شما به پایان رسیده است.":"اعتبار کلید عمومی شما به پایان رسیده است.")},403)};
 const activation=await env.DB.prepare("SELECT id FROM activations WHERE license_id=? AND device_hash=?").bind(session.licenseId,session.deviceHash).first();
 if(!activation) return {error:json({error:"device_revoked",message:"دسترسی این دستگاه توسط مدیر لغو شده است"},403)};
 await env.DB.prepare("UPDATE activations SET last_seen_at=? WHERE license_id=? AND device_hash=?").bind(now(),session.licenseId,session.deviceHash).run();
 return {session,license,deviceHash:session.deviceHash};
}
async function ticketKeyMatches(env, key, licenseId) {
 const normalized=cleanText(key,100).toUpperCase();
 if(!normalized) return false;
 const row=await env.DB.prepare("SELECT id,active,expires_at,revoked_at FROM license_keys WHERE key_hash=?").bind(await sha256(normalized)).first();
 return !!row && row.id===licenseId && !!row.active && !row.revoked_at && row.expires_at>now();
}
async function ticketMessages(env, ticketId) {
 const r=await env.DB.prepare("SELECT id,sender,message,created_at FROM support_ticket_messages WHERE ticket_id=? ORDER BY created_at ASC").bind(ticketId).all();
 return r.results;
}

export default {
 async fetch(req, env) {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: { "access-control-allow-origin":"*", "access-control-allow-methods":"GET,POST,PATCH,DELETE,OPTIONS", "access-control-allow-headers":"Content-Type,Authorization,X-Device-Id" } });
  const u = new URL(req.url), p = u.pathname.replace(/\/+$/, "") || "/";
  try {
   if (p === "/api/health" && req.method === "GET") return json({ ok:true, app:env.APP_NAME || "Nexus Game Booster", time:now() });

   // One account code per installation; server binds it to the immutable device hash.
   if (p === "/api/v1/account/issue" && req.method === "POST") {
    const b=await body(req), device=cleanText(b.deviceId || req.headers.get("X-Device-Id"),200);
    if(device.length<8)return json({ok:false,message:"شناسه نصب معتبر نیست"},400);
    const dh=await sha256(device);
    let account=await env.DB.prepare("SELECT account_code FROM customer_accounts WHERE device_hash=?").bind(dh).first();
    if(!account){let code; for(let i=0;i<8;i++){code=accountCode();try{await env.DB.prepare("INSERT INTO customer_accounts(account_code,device_hash,created_at) VALUES(?,?,?)").bind(code,dh,now()).run();break;}catch(e){if(i===7)throw e;}} account={account_code:code};}
    return json({ok:true,accountCode:account.account_code});
   }
   if (p === "/api/v1/account/me" && req.method === "GET") {
    const device=cleanText(req.headers.get("X-Device-Id"),200); if(device.length<8)return json({ok:false},400);
    const row=await env.DB.prepare("SELECT account_code FROM customer_accounts WHERE device_hash=?").bind(await sha256(device)).first();
    return json({ok:!!row,accountCode:row?.account_code||null});
   }
   // App endpoints
   if (p === "/api/v1/license/activate" && req.method === "POST") {
    const b = await body(req), key = cleanText(b.key,100).toUpperCase(), device = cleanText(b.deviceId || req.headers.get("X-Device-Id"),200);
    if (!key || device.length < 8) return json({ valid:false, code:"BAD_REQUEST", message:"کلید و شناسه دستگاه لازم است" },400);
    const kh = await sha256(key), dh = await sha256(device), row = await env.DB.prepare("SELECT * FROM license_keys WHERE key_hash=?").bind(kh).first();
    if (!row) return json({ valid:false, code:"NOT_FOUND", message:"کلید معتبر نیست" },403);
    if (!row.active || row.revoked_at) return json({ valid:false, code:"REVOKED", message:"این کلید غیرفعال شده است" },403);
    if (row.expires_at <= now()) return json({ valid:false, code:"EXPIRED", keyType:row.key_type||"public", message:(row.key_type==="private"?"اعتبار کلید شخصی شما به پایان رسیده است. برای تمدید با پشتیبانی یا مدیر برنامه تماس بگیرید.":(row.key_type==="test"?"مهلت کلید آزمایشی شما به پایان رسیده است. برای ادامه استفاده، کلید معتبر دریافت کنید.":"اعتبار کلید عمومی شما به پایان رسیده است. برای تمدید با مدیر برنامه تماس بگیرید.")) },403);
    const account=await env.DB.prepare("SELECT account_code FROM customer_accounts WHERE device_hash=?").bind(dh).first();
    if(!account)return json({valid:false,code:"ACCOUNT_REQUIRED",message:"ابتدا شماره‌حساب اختصاصی خود را صادر کنید."},403);
    if(row.key_type==="private" && row.account_code!==account.account_code)return json({valid:false,code:"ACCOUNT_MISMATCH",message:"این کلید متعلق به حساب شما نیست. کلید مخصوص شماره‌حساب خود را از مدیر دریافت کنید."},403);
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
   // User support tickets: every entry point rechecks the typed key against the active app session.
   if (p === "/api/v1/tickets/verify" && req.method === "POST") {
    const b=await body(req), a=await appSession(req,env); if(a.error)return a.error;
    if(!await ticketKeyMatches(env,b.key,a.session.licenseId)) return json({valid:false,code:"KEY_SESSION_MISMATCH",message:"این کلید با کلید فعال همین برنامه مطابقت ندارد؛ کلید فعلی برنامه را وارد کنید."},403);
    return json({valid:true,keyHint:a.license.key_hint,message:"کلید با نشست فعال برنامه مطابقت دارد."});
   }
   if (p === "/api/v1/tickets" && req.method === "POST") {
    const b=await body(req), a=await appSession(req,env); if(a.error)return a.error;
    if(!await ticketKeyMatches(env,b.key,a.session.licenseId)) return json({error:"KEY_SESSION_MISMATCH",message:"این کلید با کلید فعال همین برنامه مطابقت ندارد."},403);
    const subject=cleanText(b.subject,120), message=cleanText(b.message,4000);
    if(subject.length<3||message.length<5) return json({error:"INVALID_TICKET",message:"عنوان و متن تیکت را کامل وارد کنید."},400);
    const tid=id(), t=now();
    await env.DB.prepare("INSERT INTO support_tickets(id,license_id,device_hash,key_hint,subject,status,created_at,updated_at) VALUES(?,?,?,?,?,'open',?,?)").bind(tid,a.session.licenseId,a.deviceHash,a.license.key_hint,subject,t,t).run();
    await env.DB.prepare("INSERT INTO support_ticket_messages(id,ticket_id,sender,message,created_at) VALUES(?,?, 'user', ?, ?)").bind(id(),tid,message,t).run();
    await audit(env,"ticket_create","license:"+a.license.key_hint,"ticket",tid,subject);
    return json({ok:true,item:{id:tid,subject,status:"open",created_at:t,updated_at:t,messages:[{sender:"user",message,created_at:t}]}},201);
   }
   if (p === "/api/v1/tickets/list" && req.method === "POST") {
    const b=await body(req), a=await appSession(req,env); if(a.error)return a.error;
    if(!await ticketKeyMatches(env,b.key,a.session.licenseId)) return json({error:"KEY_SESSION_MISMATCH",message:"این کلید با کلید فعال همین برنامه مطابقت ندارد."},403);
    const r=await env.DB.prepare("SELECT id,subject,status,created_at,updated_at FROM support_tickets WHERE license_id=? ORDER BY updated_at DESC LIMIT 100").bind(a.session.licenseId).all();
    const items=[]; for(const x of r.results) items.push({...x,messages:await ticketMessages(env,x.id)});
    return json({items});
   }
   const userTicketMessageList=p.match(/^\/api\/v1\/tickets\/([\w-]+)\/messages$/);
   if(userTicketMessageList && req.method==="POST") {
    const b=await body(req), a=await appSession(req,env); if(a.error)return a.error;
    if(!await ticketKeyMatches(env,b.key,a.session.licenseId)) return json({error:"KEY_SESSION_MISMATCH",message:"این کلید با کلید فعال همین برنامه مطابقت ندارد."},403);
    const ticket=await env.DB.prepare("SELECT id FROM support_tickets WHERE id=? AND license_id=?").bind(userTicketMessageList[1],a.session.licenseId).first();
    if(!ticket)return json({error:"NOT_FOUND",message:"این تیکت متعلق به این کلید و دستگاه نیست."},404);
    const r=await ticketMessages(env,ticket.id); return json({items:r});
   }
   const userTicketReply=p.match(/^\/api\/v1\/tickets\/([\w-]+)\/reply$/);
   if(userTicketReply && req.method==="POST") {
    const b=await body(req), a=await appSession(req,env); if(a.error)return a.error;
    if(!await ticketKeyMatches(env,b.key,a.session.licenseId)) return json({error:"KEY_SESSION_MISMATCH",message:"این کلید با کلید فعال همین برنامه مطابقت ندارد."},403);
    const ticket=await env.DB.prepare("SELECT id,status FROM support_tickets WHERE id=? AND license_id=?").bind(userTicketReply[1],a.session.licenseId).first();
    if(!ticket)return json({error:"NOT_FOUND",message:"تیکت پیدا نشد."},404);
    if(ticket.status==="rejected"||ticket.status==="closed") return json({error:"TICKET_CLOSED",message:"این تیکت بسته یا رد شده و امکان پاسخ جدید ندارد."},409);
    const message=cleanText(b.message,4000); if(message.length<2)return json({error:"INVALID_MESSAGE",message:"متن پاسخ را وارد کنید."},400);
    const t=now(); await env.DB.prepare("INSERT INTO support_ticket_messages(id,ticket_id,sender,message,created_at) VALUES(?,?, 'user', ?, ?)").bind(id(),ticket.id,message,t).run();
    await env.DB.prepare("UPDATE support_tickets SET status='open',updated_at=? WHERE id=?").bind(t,ticket.id).run();
    await audit(env,"ticket_user_reply","license:"+a.license.key_hint,"ticket",ticket.id);
    return json({ok:true});
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
    const license = await env.DB.prepare("SELECT active,created_at,expires_at,revoked_at,key_type FROM license_keys WHERE id=?").bind(s.licenseId).first();
    if (!license || !license.active || license.revoked_at || license.expires_at <= now()) return json({error:"license_invalid",code:"EXPIRED",keyType:license?.key_type||"public",message:license?.key_type==="private"?"اعتبار کلید شخصی شما به پایان رسیده است. برای تمدید با مدیر برنامه تماس بگیرید.":(license?.key_type==="test"?"مهلت کلید آزمایشی شما به پایان رسیده است. برای ادامه استفاده، کلید معتبر دریافت کنید.":"اعتبار کلید عمومی شما به پایان رسیده است. برای تمدید با مدیر برنامه تماس بگیرید.")},403);
    const deviceStillAuthorized = await env.DB.prepare("SELECT id FROM activations WHERE license_id=? AND device_hash=?").bind(s.licenseId,s.deviceHash).first();
    if (!deviceStillAuthorized) return json({error:"device_revoked",message:"دسترسی این دستگاه توسط مدیر لغو شده است"},403);
    await env.DB.prepare("UPDATE activations SET last_seen_at=? WHERE license_id=? AND device_hash=?").bind(now(),s.licenseId,s.deviceHash).run();
    const dns = await env.DB.prepare("SELECT id,name,address,description,category,updated_at FROM dns_servers WHERE active=1 ORDER BY name").all();
    const wg = await env.DB.prepare("SELECT id,name,description,country,config_text,updated_at FROM wireguard_configs WHERE active=1 ORDER BY name").all();
    const settings = await env.DB.prepare("SELECT key,value FROM app_settings WHERE key IN ('sync_version','ping_page_message')").all();
    return json({ ok:true, serverTime:now(), licenseCreatedAt:license.created_at || null, licenseExpiresAt:license.expires_at, version:Object.fromEntries(settings.results.map(x=>[x.key,x.value])), dns:dns.results, wireguard:wg.results });
   }

   // Admin endpoints: all protected by ADMIN_TOKEN stored as a Worker secret.
   if (p.startsWith("/api/admin/")) { const denied = await requireAdmin(req,env); if (denied) return denied; }
   if (p === "/api/admin/keys" && req.method === "GET") {
    const r = await env.DB.prepare("SELECT id,key_plain,key_hint,label,key_type,account_code,created_at,expires_at,max_activations,active,revoked_at,(SELECT COUNT(*) FROM activations a WHERE a.license_id=license_keys.id) AS used FROM license_keys ORDER BY created_at DESC LIMIT 500").all(); return json({items:r.results});
   }
   if (p === "/api/admin/keys" && req.method === "POST") {
    const b = await body(req), type=b.type==="private"?"private":(b.type==="test"?"test":"public"), accountCode=type==="private"?cleanText(b.accountCode,20):null;
    if(type==="private" && !/^User-[A-Z0-9]{5}$/.test(accountCode||""))return json({error:"invalid_account",message:"شماره‌حساب معتبر وارد کنید."},400);
    if(type==="private" && !await env.DB.prepare("SELECT 1 FROM customer_accounts WHERE account_code=?").bind(accountCode).first())return json({error:"account_not_found",message:"این شماره‌حساب در API ثبت نشده است."},404);
    const hours=Number(b.hours)>0?Math.max(1,Math.min(87600,Math.floor(Number(b.hours)))):null, days=hours?null:Math.max(1,Math.min(3650,Math.floor(Number(b.days)||30))),duration=hours?hours*3600:days*86400,max=type==="private"?1:Math.max(1,Math.min(10000,Number(b.maxActivations)||1)),label=cleanText(b.label,100), suffix=randomKey().slice(4), key=type==="private"?`${accountCode}_${suffix.slice(0,5)}`:randomKey(), keyHash=await sha256(key),rid=id(),t=now();
    await env.DB.prepare("INSERT INTO license_keys(id,key_hash,key_hint,key_plain,label,key_type,account_code,created_at,expires_at,max_activations,active) VALUES(?,?,?,?,?,?,?,?,?,?,1)").bind(rid,keyHash,key.slice(-8),key,label,type,accountCode,t,t+duration,max).run();
    await audit(env,"create","admin","license",rid,`${hours ? `hours=${hours}` : `days=${days}`};maxActivations=${max}`);
    return json({item:{id:rid,key,label,keyType:type,accountCode,createdAt:t,expiresAt:t+duration,maxActivations:max,active:true,used:0,durationHours:hours,durationDays:days}},201);
   }
   const keyMatch=p.match(/^\/api\/admin\/keys\/([\w-]+)$/);
   if (keyMatch && req.method === "PATCH") { const b=await body(req),rid=keyMatch[1],old=await env.DB.prepare("SELECT * FROM license_keys WHERE id=?").bind(rid).first();if(!old)return json({error:"not_found"},404);const active=b.active===undefined?old.active:(b.active?1:0),delta=Number(b.expiryDeltaDays||0)*86400,expires=b.expiresAt?Math.floor(Number(b.expiresAt)):Math.max(now()+60,Number(old.expires_at)+delta),max=b.maxActivations===undefined?old.max_activations:Math.max(1,Math.min(10000,Math.floor(Number(b.maxActivations)||1)));if(max<Number(old.max_activations)){const used=await env.DB.prepare("SELECT COUNT(*) AS n FROM activations WHERE license_id=?").bind(rid).first();if(max<Number(used.n))return json({error:"capacity_below_used",message:"تعداد مجاز نمی‌تواند از تعداد دستگاه‌های فعال کمتر شود."},400);}await env.DB.prepare("UPDATE license_keys SET active=?,revoked_at=?,expires_at=?,max_activations=? WHERE id=?").bind(active,active?null:now(),expires,max,rid).run();await audit(env,"update","admin","license",rid);return json({ok:true,expiresAt:expires,maxActivations:max}); }
   if (keyMatch && req.method === "DELETE") { const rid=keyMatch[1]; const row=await env.DB.prepare("SELECT id,label FROM license_keys WHERE id=?").bind(rid).first(); if(!row)return json({error:"not_found",message:"کلید پیدا نشد"},404); await env.DB.prepare("DELETE FROM activations WHERE license_id=?").bind(rid).run(); await env.DB.prepare("DELETE FROM license_keys WHERE id=?").bind(rid).run(); await audit(env,"delete","admin","license",rid,cleanText(row.label||"",100)); return json({ok:true}); }
   const renewMatch=p.match(/^\/api\/admin\/keys\/([\w-]+)\/renew$/);
   if(renewMatch && req.method==="POST") {
    const b=await body(req), rid=renewMatch[1], row=await env.DB.prepare("SELECT id,expires_at FROM license_keys WHERE id=?").bind(rid).first();
    if(!row)return json({error:"not_found",message:"کلید پیدا نشد"},404);
    const hours=Number(b.hours)>0?Math.max(1,Math.min(87600,Math.floor(Number(b.hours)))):null;
    const days=hours?null:Math.max(1,Math.min(3650,Math.floor(Number(b.days)||30)));
    const extension=hours?hours*3600:days*86400, base=Math.max(now(),Number(row.expires_at)||0), expires=base+extension;
    await env.DB.prepare("UPDATE license_keys SET expires_at=?,active=1,revoked_at=NULL WHERE id=?").bind(expires,rid).run();
    await audit(env,"renew","admin","license",rid,hours?`hours=${hours}`:`days=${days}`);
    return json({ok:true,expiresAt:expires,addedHours:hours,addedDays:days});
   }
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
   if(p==="/api/admin/public-key-usage" && req.method==="GET"){
    const r=await env.DB.prepare("SELECT l.id,l.key_plain,l.label,l.expires_at,l.max_activations,l.active,COUNT(a.id) AS used,SUM(CASE WHEN a.last_seen_at>=? THEN 1 ELSE 0 END) AS online,GROUP_CONCAT(c.account_code || ':' || CASE WHEN a.last_seen_at>=? THEN 'online' ELSE 'offline' END) AS accounts FROM license_keys l LEFT JOIN activations a ON a.license_id=l.id LEFT JOIN customer_accounts c ON c.device_hash=a.device_hash WHERE l.key_type='public' GROUP BY l.id ORDER BY l.created_at DESC").bind(now()-300,now()-300).all();
    return json({items:r.results.map(x=>({...x,online:Number(x.online||0),offline:Math.max(0,Number(x.used||0)-Number(x.online||0)),accounts:(x.accounts||"").split(",").filter(Boolean).map(v=>{const [account,status]=v.split(":");return {account,status};})}))});
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
   if(p==="/api/admin/tickets" && req.method==="GET") {
    const r=await env.DB.prepare("SELECT t.id,t.license_id,t.key_hint,t.subject,t.status,t.created_at,t.updated_at,COALESCE(l.label,'') AS key_label FROM support_tickets t LEFT JOIN license_keys l ON l.id=t.license_id ORDER BY CASE t.status WHEN 'open' THEN 0 WHEN 'answered' THEN 1 ELSE 2 END,t.updated_at DESC LIMIT 500").all();
    return json({items:r.results});
   }
   const adminTicketMessages=p.match(/^\/api\/admin\/tickets\/([\w-]+)\/messages$/);
   if(adminTicketMessages && req.method==="GET") {
    const ticket=await env.DB.prepare("SELECT id FROM support_tickets WHERE id=?").bind(adminTicketMessages[1]).first();
    if(!ticket)return json({error:"not_found",message:"تیکت پیدا نشد"},404);
    return json({items:await ticketMessages(env,ticket.id)});
   }
   const adminTicketReply=p.match(/^\/api\/admin\/tickets\/([\w-]+)\/reply$/);
   if(adminTicketReply && req.method==="POST") {
    const b=await body(req), ticket=await env.DB.prepare("SELECT id,status FROM support_tickets WHERE id=?").bind(adminTicketReply[1]).first();
    if(!ticket)return json({error:"not_found",message:"تیکت پیدا نشد"},404);
    if(ticket.status==="rejected"||ticket.status==="closed")return json({error:"ticket_closed",message:"ابتدا وضعیت تیکت را دوباره باز کنید."},409);
    const message=cleanText(b.message,4000);if(message.length<2)return json({error:"invalid_message",message:"متن پاسخ را وارد کنید."},400);
    const t=now();await env.DB.prepare("INSERT INTO support_ticket_messages(id,ticket_id,sender,message,created_at) VALUES(?,?, 'admin', ?, ?)").bind(id(),ticket.id,message,t).run();
    await env.DB.prepare("UPDATE support_tickets SET status='answered',updated_at=? WHERE id=?").bind(t,ticket.id).run();
    await audit(env,"ticket_admin_reply","admin","ticket",ticket.id);return json({ok:true});
   }
   const adminTicketStatus=p.match(/^\/api\/admin\/tickets\/([\w-]+)\/status$/);
   if(adminTicketStatus && req.method==="PATCH") {
    const b=await body(req), status=String(b.status||"");if(!["open","answered","rejected","closed"].includes(status))return json({error:"invalid_status",message:"وضعیت نامعتبر است."},400);
    const r=await env.DB.prepare("UPDATE support_tickets SET status=?,updated_at=? WHERE id=?").bind(status,now(),adminTicketStatus[1]).run();
    if(!r.meta?.changes)return json({error:"not_found",message:"تیکت پیدا نشد"},404);
    await audit(env,"ticket_status","admin","ticket",adminTicketStatus[1],status);return json({ok:true});
   }
   if(p==="/api/admin/audit" && req.method==="GET"){const r=await env.DB.prepare("SELECT at,action,actor,record_type,record_id,detail FROM audit_log ORDER BY at DESC LIMIT 300").all();return json({items:r.results});}
   if(p==="/api/admin/audit" && req.method==="DELETE"){await env.DB.prepare("DELETE FROM audit_log").run();return json({ok:true});}
   if(p==="/api/admin/sync-version" && req.method==="POST"){const version=String(now());await env.DB.prepare("INSERT INTO app_settings(key,value,updated_at) VALUES('sync_version',?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value,updated_at=excluded.updated_at").bind(version,now()).run();await audit(env,"publish","admin","settings","sync_version",version);return json({ok:true,version});}
   return json({error:"not_found",message:"مسیر API پیدا نشد"},404);
  } catch(e) { return json({error:"server_error",message:"خطای سرور رخ داد"},500); }
 }
};

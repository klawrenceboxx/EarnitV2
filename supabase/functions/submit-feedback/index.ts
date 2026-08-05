import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
const encoder = new TextEncoder();
const email = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const reference = () => {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  return "FB-" + Array.from(bytes, (value) => alphabet[value % alphabet.length]).join("");
};
const hash = async (value: string) =>
  Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(value))))
    .map((byte) => byte.toString(16).padStart(2, "0")).join("");

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "authorization, apikey, content-type, idempotency-key",
      "Access-Control-Max-Age": "86400",
    }});
  }
  if (request.method !== "POST") return json(405, { success: false, code: "method_not_allowed" });
  if (!request.headers.get("content-type")?.toLowerCase().startsWith("application/json")) {
    return json(415, { success: false, code: "content_type" });
  }
  const contentLength = Number(request.headers.get("content-length") || 0);
  if (contentLength > 6_000_000) return json(413, { success: false, code: "payload_too_large" });

  let body: Record<string, any>;
  try { body = await request.json(); } catch { return json(400, { success: false, code: "invalid_json" }); }
  const idempotencyKey = request.headers.get("idempotency-key") || body.idempotency_key;
  if (!uuid.test(idempotencyKey || "")) return json(400, { success: false, code: "idempotency_key" });
  if (!["BUG", "SUGGESTION", "OTHER"].includes(body.category)) return json(400, { success: false, code: "category" });
  if (typeof body.message !== "string" || body.message.trim().length < 1 || body.message.length > 2000) {
    return json(400, { success: false, code: "message" });
  }
  if (body.contact_email != null && (typeof body.contact_email !== "string" || !email.test(body.contact_email) || body.contact_email.length > 254)) {
    return json(400, { success: false, code: "email" });
  }
  if (!["SETTINGS", "SHAKE", "CRASH_FOLLOW_UP"].includes(body.entry_source) ||
      body.diagnostics_schema_version !== 1 || !uuid.test(body.installation_id || "") ||
      !body.diagnostics || body.status != null) {
    return json(400, { success: false, code: "payload" });
  }
  if (!Number.isFinite(Number(body.client_created_at))) return json(400, { success: false, code: "client_created_at" });
  if (body.screenshot && (body.screenshot.mime_type !== "image/jpeg" ||
      typeof body.screenshot.base64 !== "string" || body.screenshot.base64.length > 5_600_000)) {
    return json(400, { success: false, code: "screenshot" });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const salt = Deno.env.get("FEEDBACK_RATE_LIMIT_SALT");
  if (!url || !serviceKey || !salt) return json(503, { success: false, code: "unavailable" });
  const supabase = createClient(url, serviceKey, { auth: { persistSession: false } });

  const { data: existing } = await supabase.from("feedback")
    .select("public_reference_id").eq("idempotency_key", idempotencyKey).maybeSingle();
  if (existing) return json(200, { success: true, referenceId: existing.public_reference_id });

  const forwarded = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || "unknown";
  const rateKey = await hash(`${salt}:${body.installation_id}:${forwarded}`);
  const { data: allowed, error: rateError } = await supabase.rpc("consume_feedback_rate_limit", { p_rate_key: rateKey, p_limit: 10 });
  if (rateError) return json(503, { success: false, code: "unavailable" });
  if (!allowed) return json(429, { success: false, code: "rate_limited" });

  let screenshotPath: string | null = null;
  if (body.screenshot) {
    try {
      const binary = Uint8Array.from(atob(body.screenshot.base64), (character) => character.charCodeAt(0));
      if (binary.length > 4_194_304 || binary.length < 4 ||
          binary[0] !== 0xff || binary[1] !== 0xd8 || binary[binary.length - 2] !== 0xff || binary[binary.length - 1] !== 0xd9) {
        return json(400, { success: false, code: "screenshot" });
      }
      screenshotPath = `${new Date().toISOString().slice(0, 10)}/${crypto.randomUUID()}.jpg`;
      const { error } = await supabase.storage.from("feedback-screenshots")
        .upload(screenshotPath, binary, { contentType: "image/jpeg", upsert: false });
      if (error) return json(503, { success: false, code: "upload_failed" });
    } catch { return json(400, { success: false, code: "screenshot" }); }
  }

  const supplied = body.diagnostics;
  const crash = supplied.crash && body.entry_source === "CRASH_FOLLOW_UP" ? {
    exception_class: String(supplied.crash.exception_class || "").slice(0, 160),
    sanitized_stack_trace: String(supplied.crash.sanitized_stack_trace || "").slice(0, 8_000),
    last_known_route: String(supplied.crash.last_known_route || "").slice(0, 120),
    session_id: String(supplied.crash.session_id || "").slice(0, 80),
  } : undefined;
  const diagnostics = {
    diagnostics_schema_version: 1,
    app_version: String(supplied.app_version || "").slice(0, 80),
    build_number: Number(supplied.build_number) || 0,
    android_version: String(supplied.android_version || "").slice(0, 80),
    device_manufacturer: String(supplied.device_manufacturer || "").slice(0, 120),
    device_model: String(supplied.device_model || "").slice(0, 120),
    current_screen: String(supplied.current_screen || "").slice(0, 120),
    entry_source: body.entry_source,
    active_rule_count: Math.max(0, Number(supplied.active_rule_count) || 0),
    rule_type_counts: Object.fromEntries(
      ["EarnRewardTime", "CompleteToUnlock", "ScheduledBlock"].map((key) =>
        [key, Math.max(0, Number(supplied.rule_type_counts?.[key]) || 0)]
      )
    ),
    strict_mode_enabled: Boolean(supplied.strict_mode_enabled),
    usage_access_granted: Boolean(supplied.usage_access_granted),
    accessibility_service_enabled: Boolean(supplied.accessibility_service_enabled),
    online: Boolean(supplied.online),
    installation_id: body.installation_id,
    locale: String(supplied.locale || "").slice(0, 40),
    session_id: String(supplied.session_id || "").slice(0, 80),
    process_uptime_millis: Math.max(0, Number(supplied.process_uptime_millis) || 0),
    ...(crash ? { crash } : {}),
  };
  const publicReference = reference();
  const row = {
    public_reference_id: publicReference,
    category: body.category,
    message: body.message.trim(),
    contact_email: body.contact_email?.trim() || null,
    screenshot_path: screenshotPath,
    entry_source: body.entry_source,
    installation_id: body.installation_id,
    idempotency_key: idempotencyKey,
    diagnostics_schema_version: 1,
    diagnostics_json: diagnostics,
    app_version: diagnostics.app_version,
    build_number: diagnostics.build_number,
    android_version: diagnostics.android_version,
    device_manufacturer: diagnostics.device_manufacturer,
    device_model: diagnostics.device_model,
    current_screen: diagnostics.current_screen,
    strict_mode_enabled: Boolean(diagnostics.strict_mode_enabled),
    active_rule_count: diagnostics.active_rule_count,
    usage_access_granted: Boolean(diagnostics.usage_access_granted),
    accessibility_service_enabled: Boolean(diagnostics.accessibility_service_enabled),
    client_created_at: new Date(Number(body.client_created_at)).toISOString(),
  };
  const { error: insertError } = await supabase.from("feedback").insert(row);
  if (insertError) {
    if (screenshotPath) await supabase.storage.from("feedback-screenshots").remove([screenshotPath]);
    const { data: raced } = await supabase.from("feedback")
      .select("public_reference_id").eq("idempotency_key", idempotencyKey).maybeSingle();
    if (raced) return json(200, { success: true, referenceId: raced.public_reference_id });
    return json(503, { success: false, code: "submit_failed" });
  }
  return json(200, { success: true, referenceId: publicReference });
});

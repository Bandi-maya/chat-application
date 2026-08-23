import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const jsonHeaders = {
  "Content-Type": "application/json",
  "Cache-Control": "no-store",
};

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") {
    return new Response(JSON.stringify({ error: "method_not_allowed" }), {
      status: 405,
      headers: jsonHeaders,
    });
  }

  const authorization = request.headers.get("Authorization") ?? "";
  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  if (!supabaseUrl || !anonKey) {
    return new Response(JSON.stringify({ error: "server_not_configured" }), {
      status: 503,
      headers: jsonHeaders,
    });
  }

  const client = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data, error } = await client.auth.getUser();
  const user = data.user;
  if (error || !user) {
    return new Response(JSON.stringify({ error: "unauthorized" }), {
      status: 401,
      headers: jsonHeaders,
    });
  }

  const stunUrls = (Deno.env.get("STUN_URLS") ??
    "stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302,stun:stun.cloudflare.com:3478")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
  const turnSecret = Deno.env.get("TURN_SHARED_SECRET") ?? "";
  const turnUrls = (Deno.env.get("TURN_URLS") ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);

  // Direct STUN is a valid ICE configuration. A production relay is added
  // automatically once TURN_URLS and TURN_SHARED_SECRET are configured.
  if (!turnSecret || turnUrls.length === 0) {
    return new Response(
      JSON.stringify({
        relay_configured: false,
        ice_servers: [{ urls: stunUrls }],
      }),
      { status: 200, headers: jsonHeaders },
    );
  }

  const lifetimeSeconds = 3600;
  const expiresAt = Math.floor(Date.now() / 1000) + lifetimeSeconds;
  const username = `${expiresAt}:${user.id}`;
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(turnSecret),
    { name: "HMAC", hash: "SHA-1" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    encoder.encode(username),
  );
  const credential = btoa(String.fromCharCode(...new Uint8Array(signature)));

  return new Response(
    JSON.stringify({
      relay_configured: true,
      expires_at: expiresAt,
      ice_servers: [
        { urls: stunUrls },
        { urls: turnUrls, username, credential },
      ],
    }),
    { status: 200, headers: jsonHeaders },
  );
});

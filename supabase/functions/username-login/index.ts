import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") {
    return new Response(JSON.stringify({ error: "method_not_allowed" }), {
      status: 405,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    const payload = await request.json().catch(() => ({}));
    const identifier = String(payload?.identifier ?? "").trim();
    const password = String(payload?.password ?? "");
    if (identifier.length < 3 || password.length < 6 || password.length > 128) {
      return invalidCredentials();
    }

    const url = Deno.env.get("SUPABASE_URL")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const admin = createClient(url, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    let email = identifier.toLowerCase();
    if (!identifier.includes("@")) {
      const normalized = identifier.replace(/^@+/, "").toLowerCase();
      const { data: profile } = await admin
        .from("profiles")
        .select("id")
        .ilike("username", normalized)
        .maybeSingle();
      if (!profile?.id) return invalidCredentials();

      const { data: authData } = await admin.auth.admin.getUserById(profile.id);
      email = authData?.user?.email?.toLowerCase() ?? "";
      if (!email) return invalidCredentials();
    }

    const authClient = createClient(url, anonKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data, error } = await authClient.auth.signInWithPassword({ email, password });
    if (error || !data.session || !data.user) return invalidCredentials();

    return new Response(
      JSON.stringify({
        access_token: data.session.access_token,
        refresh_token: data.session.refresh_token,
        expires_at: data.session.expires_at,
        user_id: data.user.id,
      }),
      {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json", "Cache-Control": "no-store" },
      },
    );
  } catch (_) {
    return invalidCredentials();
  }
});

function invalidCredentials(): Response {
  return new Response(JSON.stringify({ error: "invalid_credentials" }), {
    status: 401,
    headers: { ...corsHeaders, "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

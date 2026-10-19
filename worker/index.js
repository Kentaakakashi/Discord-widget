const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
  "Cache-Control": "no-store"
};

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Content-Type": "application/json; charset=utf-8" }
  });
}

function b64url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function utf8b64url(value) {
  return b64url(new TextEncoder().encode(value));
}

function pemToBytes(pem) {
  const body = pem.replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "").replace(/\s/g, "");
  const raw = atob(body);
  return Uint8Array.from(raw, c => c.charCodeAt(0));
}

async function mintFirebaseToken(serviceAccount, uid) {
  const now = Math.floor(Date.now() / 1000);
  const header = utf8b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = utf8b64url(JSON.stringify({
    iss: serviceAccount.client_email,
    sub: serviceAccount.client_email,
    aud: "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit",
    iat: now,
    exp: now + 3600,
    uid
  }));
  const unsigned = header + "." + claims;
  const key = await crypto.subtle.importKey(
    "pkcs8", pemToBytes(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)
  );
  return unsigned + "." + b64url(new Uint8Array(signature));
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    const authorization = request.headers.get("Authorization") || "";
    const match = authorization.match(/^Bearer\s+([^\s]+)$/i);
    if (!match) return json({ error: "missing_discord_access_token" }, 401);
    if (!env.FIREBASE_SERVICE_ACCOUNT_JSON) return json({ error: "bridge_not_configured" }, 503);

    try {
      // Validate the access token with Discord itself. Never trust an ID from the Android client.
      const discordResponse = await fetch("https://discord.com/api/v10/users/@me", {
        headers: { Authorization: "Bearer " + match[1] }
      });
      if (!discordResponse.ok) return json({ error: "discord_token_rejected" }, 401);
      const user = await discordResponse.json();
      if (typeof user.id !== "string" || !/^\d{17,20}$/.test(user.id)) {
        return json({ error: "invalid_discord_identity" }, 401);
      }

      const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
      if (!serviceAccount.client_email || !serviceAccount.private_key) {
        return json({ error: "invalid_firebase_service_account" }, 503);
      }
      const firebaseCustomToken = await mintFirebaseToken(serviceAccount, "discord_" + user.id);
      return json({
        firebaseCustomToken,
        account: {
          id: user.id,
          username: user.username || "Discord user",
          globalName: user.global_name || null
        }
      });
    } catch (error) {
      // Never log OAuth tokens, service-account material, or generated Firebase tokens.
      console.error("Discord/Firebase exchange failed:", String(error && error.message || "unknown"));
      return json({ error: "identity_exchange_failed" }, 502);
    }
  }
};

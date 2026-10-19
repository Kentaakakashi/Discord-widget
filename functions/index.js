const { onRequest } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

initializeApp();

const db = getFirestore();

function setCors(res) {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type");
}

exports.exchangeDiscordIdentity = onRequest(
  { region: "asia-south1", cors: false, maxInstances: 10 },
  async (req, res) => {
    setCors(res);
    if (req.method === "OPTIONS") return res.status(204).send("");
    if (req.method !== "POST") return res.status(405).json({ error: "method_not_allowed" });

    const authorization = req.get("Authorization") || "";
    const match = authorization.match(/^Bearer\s+(.+)$/i);
    if (!match) return res.status(401).json({ error: "missing_discord_access_token" });

    try {
      // Ask Discord who owns this token. Never trust a Discord ID supplied by
      // the Android client, and never use a username as an account key.
      const discordResponse = await fetch("https://discord.com/api/v10/users/@me", {
        headers: { Authorization: `Bearer ${match[1]}` },
        signal: AbortSignal.timeout(10000)
      });
      if (!discordResponse.ok) {
        return res.status(401).json({ error: "discord_token_rejected" });
      }

      const discordUser = await discordResponse.json();
      if (typeof discordUser.id !== "string" || !/^\d{17,20}$/.test(discordUser.id)) {
        return res.status(401).json({ error: "invalid_discord_identity" });
      }

      // Stable mapping: Discord snowflake -> deterministic Firebase UID.
      // Only this trusted backend writes the mapping document.
      const uid = `discord_${discordUser.id}`;
      const mappingRef = db.collection("discordIdentityMap").doc(discordUser.id);
      await db.runTransaction(async (tx) => {
        const existing = await tx.get(mappingRef);
        if (existing.exists && existing.data().uid !== uid) {
          throw new Error("identity_mapping_conflict");
        }
        tx.set(mappingRef, {
          uid,
          provider: "discord",
          updatedAt: FieldValue.serverTimestamp()
        }, { merge: true });
      });

      const customToken = await getAuth().createCustomToken(uid, {
        provider: "discord",
        discordId: discordUser.id
      });

      return res.status(200).json({
        firebaseCustomToken: customToken,
        account: {
          id: discordUser.id,
          username: discordUser.username || "Discord user",
          globalName: discordUser.global_name || null,
          avatar: discordUser.avatar || null
        }
      });
    } catch (error) {
      // Do not log access tokens, custom tokens, or raw Discord responses.
      console.error("Discord identity exchange failed:", error.message);
      if (error.message === "identity_mapping_conflict") {
        return res.status(409).json({ error: "identity_mapping_conflict" });
      }
      return res.status(502).json({ error: "identity_exchange_failed" });
    }
  }
);

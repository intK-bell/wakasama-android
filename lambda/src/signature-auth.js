import crypto from "node:crypto";

const MAX_SKEW_SECONDS = Number(process.env.SIGNATURE_MAX_SKEW_SECONDS || "300");

function sha256Hex(raw) {
  return crypto.createHash("sha256").update(raw, "utf8").digest("hex");
}

export function buildCanonical({ deviceId, timestamp, nonce, rawBody }) {
  const bodyHash = sha256Hex(rawBody || "");
  return [deviceId, String(timestamp), nonce, bodyHash].join("\n");
}

export function verifySignature({ publicKeyPem, canonical, signatureBase64 }) {
  const signatureBuffer = Buffer.from(String(signatureBase64 || ""), "base64");
  if (!publicKeyPem || !signatureBuffer.length) return false;

  return crypto.verify(
    "sha256",
    Buffer.from(canonical, "utf8"),
    publicKeyPem,
    signatureBuffer
  );
}

export function validateSignatureHeaders({ deviceId, timestamp, nonce }) {
  if (!deviceId || typeof deviceId !== "string") return "missing device id";
  if (!nonce || typeof nonce !== "string") return "missing nonce";
  if (!timestamp || !/^\d+$/.test(String(timestamp))) return "invalid timestamp";

  const ts = Number(timestamp);
  const nowSec = Math.floor(Date.now() / 1000);
  if (Math.abs(nowSec - ts) > MAX_SKEW_SECONDS) {
    return "timestamp out of allowed range";
  }
  return null;
}

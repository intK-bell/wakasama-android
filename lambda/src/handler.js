import "./env.js";
import { sendAnswerMail } from "./mailer.js";
import { buildMailText, validatePayload } from "./validate.js";
import { getDevicePublicKey, reserveNonce, upsertDevicePublicKey } from "./security-store.js";
import { buildCanonical, validateSignatureHeaders, verifySignature } from "./signature-auth.js";

function response(statusCode, body) {
  return {
    statusCode,
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  };
}

function parseRawBody(event) {
  if (!event?.body) return "";
  if (event.isBase64Encoded) {
    try {
      return Buffer.from(event.body, "base64").toString("utf8");
    } catch {
      return "";
    }
  }
  return typeof event.body === "string" ? event.body : JSON.stringify(event.body);
}

function parseBodyFromRaw(rawBody) {
  if (!rawBody) return null;
  try {
    return JSON.parse(rawBody);
  } catch {
    return null;
  }
}

function getHeader(event, name) {
  const headers = event?.headers || {};
  const expected = name.toLowerCase();
  for (const key of Object.keys(headers)) {
    if (key.toLowerCase() === expected) {
      return headers[key];
    }
  }
  return "";
}

function isLegacyAuthorized(event) {
  const appToken = String(getHeader(event, "x-app-token") || "").trim();
  const current = String(process.env.APP_TOKEN_CURRENT || "").trim();
  const next = String(process.env.APP_TOKEN_NEXT || "").trim();

  if (!appToken) return false;
  if (!current && !next) return false;

  return appToken === current || appToken === next;
}

function pathOf(event) {
  return String(
    event?.rawPath ||
      event?.requestContext?.http?.path ||
      event?.path ||
      ""
  ).toLowerCase();
}

async function isSignatureAuthorized(event, rawBody) {
  const deviceId = String(getHeader(event, "x-device-id") || "").trim();
  const timestamp = String(getHeader(event, "x-timestamp") || "").trim();
  const nonce = String(getHeader(event, "x-nonce") || "").trim();
  const signature = String(getHeader(event, "x-signature") || "").trim();

  const headerErr = validateSignatureHeaders({ deviceId, timestamp, nonce });
  if (headerErr) {
    return { ok: false, message: headerErr };
  }

  const publicKeyPem = await getDevicePublicKey(deviceId);
  if (!publicKeyPem) {
    return { ok: false, message: "unknown device key" };
  }

  const nonceReserved = await reserveNonce(
    deviceId,
    nonce,
    Math.floor(Date.now() / 1000) + 10 * 60
  );
  if (!nonceReserved) {
    return { ok: false, message: "replayed nonce" };
  }

  const canonical = buildCanonical({ deviceId, timestamp, nonce, rawBody });
  const verified = verifySignature({
    publicKeyPem,
    canonical,
    signatureBase64: signature
  });
  if (!verified) {
    return { ok: false, message: "invalid signature" };
  }

  return { ok: true, deviceId };
}

function validateRegistrationPayload(payload) {
  if (!payload || typeof payload !== "object") return "invalid payload";
  if (typeof payload.deviceId !== "string" || payload.deviceId.trim() === "") {
    return "deviceId is required";
  }
  if (typeof payload.publicKeyPem !== "string" || payload.publicKeyPem.trim() === "") {
    return "publicKeyPem is required";
  }
  if (!payload.publicKeyPem.includes("BEGIN PUBLIC KEY")) {
    return "publicKeyPem is invalid";
  }
  return null;
}

export const handler = async (event) => {
  const path = pathOf(event);
  const rawBody = parseRawBody(event);
  const payload = parseBodyFromRaw(rawBody);
  const looksLikeRegisterPayload =
    payload &&
    typeof payload === "object" &&
    typeof payload.publicKeyPem === "string" &&
    typeof payload.deviceId === "string" &&
    !Array.isArray(payload.questions);

  if (path.endsWith("/register-device-key") || looksLikeRegisterPayload) {
    if (!isLegacyAuthorized(event)) {
      return response(401, { ok: false, message: "unauthorized" });
    }
    if (!payload) {
      return response(400, { ok: false, message: "invalid JSON" });
    }
    const err = validateRegistrationPayload(payload);
    if (err) {
      return response(400, { ok: false, message: err });
    }

    try {
      await upsertDevicePublicKey(
        payload.deviceId.trim(),
        payload.publicKeyPem.trim(),
        payload.keyAlgorithm || "ECDSA_P256_SHA256"
      );
      return response(200, { ok: true, message: "registered" });
    } catch (e) {
      console.error("register device key failed", e);
      return response(500, { ok: false, message: "register failed" });
    }
  }

  const signatureAuth = await isSignatureAuthorized(event, rawBody);
  const legacyAuth = isLegacyAuthorized(event);
  if (!signatureAuth.ok && !legacyAuth) {
    return response(401, { ok: false, message: signatureAuth.message || "unauthorized" });
  }

  if (!payload) {
    return response(400, { ok: false, message: "invalid JSON" });
  }

  if (signatureAuth.ok && signatureAuth.deviceId !== payload.deviceId) {
    return response(401, { ok: false, message: "device id mismatch" });
  }

  const err = validatePayload(payload);
  if (err) {
    return response(400, { ok: false, message: err });
  }

  try {
    const subject = `[LauncherLock] Answers from ${payload.deviceId}`;
    const textBody = buildMailText(payload);
    await sendAnswerMail({ subject, textBody, to: payload.to });

    return response(200, { ok: true, message: "sent" });
  } catch (e) {
    console.error("mail send failed", e);
    return response(500, { ok: false, message: "mail send failed" });
  }
};

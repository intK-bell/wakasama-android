import "./env.js";
import { sendAnswerMail } from "./mailer.js";
import { buildMailText, validatePayload } from "./validate.js";
import {
  createDevicePublicKey,
  getDevicePublicKey,
  reserveIdempotencyKey,
  reserveNonce
} from "./security-store.js";
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

async function isRegistrationSignatureAuthorized(event, rawBody, payload) {
  const deviceId = String(getHeader(event, "x-device-id") || "").trim();
  const timestamp = String(getHeader(event, "x-timestamp") || "").trim();
  const nonce = String(getHeader(event, "x-nonce") || "").trim();
  const signature = String(getHeader(event, "x-signature") || "").trim();

  const headerErr = validateSignatureHeaders({ deviceId, timestamp, nonce });
  if (headerErr) {
    return { ok: false, message: headerErr };
  }
  if (deviceId !== String(payload.deviceId || "").trim()) {
    return { ok: false, message: "device id mismatch" };
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
    publicKeyPem: payload.publicKeyPem,
    canonical,
    signatureBase64: signature
  });
  if (!verified) {
    return { ok: false, message: "invalid signature" };
  }

  return { ok: true };
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

const IDEMPOTENCY_TTL_DAYS = Number(process.env.IDEMPOTENCY_TTL_DAYS || "90");
const IDEMPOTENCY_TTL_SECONDS = IDEMPOTENCY_TTL_DAYS * 24 * 60 * 60;

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
    if (!payload) {
      return response(400, { ok: false, message: "invalid JSON" });
    }
    const err = validateRegistrationPayload(payload);
    if (err) {
      return response(400, { ok: false, message: err });
    }
    const deviceId = payload.deviceId.trim();
    const publicKeyPem = payload.publicKeyPem.trim();
    const existingPublicKey = await getDevicePublicKey(deviceId);
    if (existingPublicKey && existingPublicKey.trim() !== publicKeyPem) {
      return response(409, { ok: false, message: "device key already registered" });
    }

    const auth = await isRegistrationSignatureAuthorized(
      event,
      rawBody,
      { ...payload, deviceId, publicKeyPem }
    );
    if (!auth.ok) {
      return response(401, { ok: false, message: auth.message || "unauthorized" });
    }

    if (existingPublicKey && existingPublicKey.trim() === publicKeyPem) {
      return response(200, { ok: true, message: "already registered" });
    }

    try {
      const created = await createDevicePublicKey(
        deviceId,
        publicKeyPem,
        payload.keyAlgorithm || "ECDSA_P256_SHA256"
      );
      if (!created) {
        const latest = await getDevicePublicKey(deviceId);
        if (latest && latest.trim() === publicKeyPem) {
          return response(200, { ok: true, message: "already registered" });
        }
        return response(409, { ok: false, message: "device key already registered" });
      }
      return response(200, { ok: true, message: "registered" });
    } catch (e) {
      console.error("register device key failed", e);
      return response(500, { ok: false, message: "register failed" });
    }
  }

  const signatureAuth = await isSignatureAuthorized(event, rawBody);
  if (!signatureAuth.ok) {
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

  const idempotencyKey = String(payload.idempotencyKey || "").trim();
  if (idempotencyKey) {
    const ttlSeconds = Math.floor(Date.now() / 1000) + IDEMPOTENCY_TTL_SECONDS;
    const isFirstRequest = await reserveIdempotencyKey(
      signatureAuth.deviceId,
      idempotencyKey,
      ttlSeconds
    );
    if (!isFirstRequest) {
      return response(200, { ok: true, message: "duplicate ignored", deduplicated: true });
    }
  }

  try {
    const subject = "若様よりご報告でござる";
    const textBody = buildMailText(payload);
    await sendAnswerMail({ subject, textBody, to: payload.to });

    return response(200, { ok: true, message: "sent" });
  } catch (e) {
    console.error("mail send failed", e);
    return response(500, { ok: false, message: "mail send failed" });
  }
};

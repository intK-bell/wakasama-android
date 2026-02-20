import "./env.js";
import { sendAnswerMail } from "./mailer.js";
import { buildMailText, validatePayload } from "./validate.js";

function response(statusCode, body) {
  return {
    statusCode,
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  };
}

function parseBody(event) {
  if (!event?.body) return null;
  try {
    return typeof event.body === "string" ? JSON.parse(event.body) : event.body;
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

function isAuthorized(event) {
  const appToken = String(getHeader(event, "x-app-token") || "").trim();
  const current = String(process.env.APP_TOKEN_CURRENT || "").trim();
  const next = String(process.env.APP_TOKEN_NEXT || "").trim();

  if (!appToken) return false;
  if (!current && !next) return false;

  return appToken === current || appToken === next;
}

export const handler = async (event) => {
  if (!isAuthorized(event)) {
    return response(401, { ok: false, message: "unauthorized" });
  }

  const payload = parseBody(event);
  if (!payload) {
    return response(400, { ok: false, message: "invalid JSON" });
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

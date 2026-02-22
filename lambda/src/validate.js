export function validatePayload(payload) {
  if (!payload || typeof payload !== "object") {
    return "invalid payload";
  }

  if (typeof payload.deviceId !== "string" || payload.deviceId.trim() === "") {
    return "deviceId is required";
  }

  if (typeof payload.answeredAt !== "string" || payload.answeredAt.trim() === "") {
    return "answeredAt is required";
  }
  if (
    payload.idempotencyKey !== undefined &&
    (typeof payload.idempotencyKey !== "string" || payload.idempotencyKey.trim() === "")
  ) {
    return "idempotencyKey is invalid";
  }

  if (!Array.isArray(payload.questions) || payload.questions.length === 0) {
    return "questions must be a non-empty array";
  }

  if (typeof payload.to !== "string" || payload.to.trim() === "") {
    return "to is required";
  }
  if (!isValidEmail(payload.to)) {
    return "to is invalid";
  }

  for (const item of payload.questions) {
    if (!item || typeof item !== "object") return "question item is invalid";
    if (typeof item.q !== "string" || item.q.trim() === "") return "question text is required";
    if (typeof item.a !== "string" || item.a.trim() === "") return "answer text is required";
  }

  return null;
}

function isValidEmail(value) {
  const email = String(value || "").trim();
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

export function buildMailText(payload) {
  const lines = [];
  payload.questions.forEach((qa, idx) => {
    lines.push(`質問${idx + 1}：${qa.q}`);
    lines.push(`　回答：${qa.a}`);
  });

  lines.push("");
  lines.push("発信元");
  lines.push("若様の宿題");
  lines.push("support team");
  lines.push("email：aokigyoumukikaku@gmail.com");

  return lines.join("\n");
}

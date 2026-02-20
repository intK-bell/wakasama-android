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

  if (!Array.isArray(payload.questions) || payload.questions.length === 0) {
    return "questions must be a non-empty array";
  }

  if (typeof payload.to !== "string" || payload.to.trim() === "") {
    return "to is required";
  }

  for (const item of payload.questions) {
    if (!item || typeof item !== "object") return "question item is invalid";
    if (typeof item.q !== "string" || item.q.trim() === "") return "question text is required";
    if (typeof item.a !== "string" || item.a.trim() === "") return "answer text is required";
  }

  return null;
}

export function buildMailText(payload) {
  const lines = [];
  lines.push(`deviceId: ${payload.deviceId}`);
  lines.push(`answeredAt: ${payload.answeredAt}`);
  lines.push("");
  lines.push("questions:");

  payload.questions.forEach((qa, idx) => {
    lines.push(`${idx + 1}. Q: ${qa.q}`);
    lines.push(`   A: ${qa.a}`);
  });

  return lines.join("\n");
}

import "./env.js";
import { SESv2Client, SendEmailCommand } from "@aws-sdk/client-sesv2";

const awsRegion = process.env.AWS_REGION || process.env.AWS_DEFAULT_REGION || "";
if (!awsRegion) {
  throw new Error("AWS_REGION (or AWS_DEFAULT_REGION) is required");
}

const ses = new SESv2Client({ region: awsRegion });

export async function sendAnswerMail({ subject, textBody, to }) {
  const from = process.env.MAIL_FROM;
  const destination = String(to || "").trim();

  if (!from || !destination) {
    throw new Error("MAIL_FROM and destination(to) are required");
  }

  const command = new SendEmailCommand({
    FromEmailAddress: from,
    Destination: {
      ToAddresses: [destination]
    },
    Content: {
      Simple: {
        Subject: { Data: subject, Charset: "UTF-8" },
        Body: {
          Text: { Data: textBody, Charset: "UTF-8" }
        }
      }
    }
  });

  return ses.send(command);
}

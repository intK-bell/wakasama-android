import { SESv2Client, SendEmailCommand } from "@aws-sdk/client-sesv2";

const ses = new SESv2Client({ region: process.env.AWS_REGION || "ap-northeast-1" });

export async function sendAnswerMail({ subject, textBody, to }) {
  const from = process.env.MAIL_FROM;
  const destination = to || process.env.MAIL_TO;

  if (!from || !destination) {
    throw new Error("MAIL_FROM and destination(to or MAIL_TO) are required");
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

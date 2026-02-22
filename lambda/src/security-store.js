import { DynamoDBClient } from "@aws-sdk/client-dynamodb";
import { DynamoDBDocumentClient, GetCommand, PutCommand } from "@aws-sdk/lib-dynamodb";

const region = process.env.AWS_REGION || process.env.AWS_DEFAULT_REGION || "";
const tableName = String(process.env.SECURITY_TABLE || "").trim();

if (!region) {
  throw new Error("AWS_REGION (or AWS_DEFAULT_REGION) is required");
}
if (!tableName) {
  throw new Error("SECURITY_TABLE is required");
}

const ddb = DynamoDBDocumentClient.from(
  new DynamoDBClient({ region }),
  { marshallOptions: { removeUndefinedValues: true } }
);

function deviceKeyPk(deviceId) {
  return `DEVICE#${deviceId}`;
}

function noncePk(deviceId) {
  return `NONCE#${deviceId}`;
}

function idempotencyPk(deviceId) {
  return `IDEMPOTENCY#${deviceId}`;
}

export async function upsertDevicePublicKey(deviceId, publicKeyPem, keyAlgorithm = "ECDSA_P256_SHA256") {
  const now = Date.now();
  await ddb.send(
    new PutCommand({
      TableName: tableName,
      Item: {
        pk: deviceKeyPk(deviceId),
        sk: "KEY",
        deviceId,
        publicKeyPem,
        keyAlgorithm,
        updatedAt: now
      }
    })
  );
}

export async function getDevicePublicKey(deviceId) {
  const res = await ddb.send(
    new GetCommand({
      TableName: tableName,
      Key: {
        pk: deviceKeyPk(deviceId),
        sk: "KEY"
      }
    })
  );
  return res.Item?.publicKeyPem || "";
}

export async function reserveNonce(deviceId, nonce, ttlSeconds) {
  try {
    await ddb.send(
      new PutCommand({
        TableName: tableName,
        Item: {
          pk: noncePk(deviceId),
          sk: nonce,
          deviceId,
          expiresAt: ttlSeconds
        },
        ConditionExpression: "attribute_not_exists(pk) AND attribute_not_exists(sk)"
      })
    );
    return true;
  } catch (e) {
    if (e?.name === "ConditionalCheckFailedException") {
      return false;
    }
    throw e;
  }
}

export async function reserveIdempotencyKey(deviceId, idempotencyKey, ttlSeconds) {
  try {
    await ddb.send(
      new PutCommand({
        TableName: tableName,
        Item: {
          pk: idempotencyPk(deviceId),
          sk: idempotencyKey,
          deviceId,
          expiresAt: ttlSeconds
        },
        ConditionExpression: "attribute_not_exists(pk) AND attribute_not_exists(sk)"
      })
    );
    return true;
  } catch (e) {
    if (e?.name === "ConditionalCheckFailedException") {
      return false;
    }
    throw e;
  }
}

import api from "../api/client";
import {
  getDeviceId,
  getDeviceToken,
} from "../storage/deviceStorage";

export const sendCommandResult = async ({
  reference,
  status,
  message,
  response,
  simSlot,
}) => {
  const deviceId = await getDeviceId();
  const secretKey = await getDeviceToken();

  if (!deviceId || !secretKey) {
    throw new Error("Device not paired");
  }

  const res = await api.post("/gateway/result", {
    deviceId,
    secretKey,
    reference,
    status,
    message: message || response || "",
    response: response || message || "",
    simSlot:
      simSlot === undefined || simSlot === null
        ? undefined
        : Number(simSlot),
  });

  return res.data;
};
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
  try {
    const deviceId = await getDeviceId();
    const secretKey = await getDeviceToken();

    if (!deviceId || !secretKey) {
      throw new Error("Device not paired");
    }

    if (!reference) {
      throw new Error("Command reference is missing for result reporting");
    }

    const payload = {
      deviceId,
      secretKey,
      reference,
      status: status || "UNKNOWN",
      message: message || response || "",
      response: response || message || "",
      simSlot:
        simSlot === undefined || simSlot === null
          ? undefined
          : Number(simSlot),
    };

    const res = await api.post("/gateway/result", payload);

    return res.data;
  } catch (error) {
    console.log(
      `Error reporting command result [${reference}]:`,
      error?.response?.data?.message || error?.message || "Unknown error"
    );
    throw error;
  }
};
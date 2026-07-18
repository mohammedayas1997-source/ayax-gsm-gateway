import { io } from "socket.io-client";
import { getDeviceId, getDeviceToken } from "../storage/deviceStorage";
import { sendCommandResult } from "../services/commandService";
import { sendSms } from "../services/smsService";
import { sendUssd } from "../services/ussdService";
import { addToQueue } from "../services/queueService";
import { addLog } from "../services/logService";
import { startAlarm, stopAlarm } from "../services/deviceManagerService";
import { lockDevice } from "../services/devicePolicyService";

let socket = null;

const handleCommand = async (command) => {
  if (!command?.reference) {
    console.log("Invalid command: missing reference");
    return;
  }

  try {
    addLog({
      type: command.type || "UNKNOWN",
      reference: command.reference,
      status: "RECEIVED",
      message: "Command received",
    });

    if (command.type === "LOCK_DEVICE") {
      await lockDevice();

      await sendCommandResult({
        reference: command.reference,
        status: "SUCCESSFUL",
        message: "Device locked successfully",
      });

      addLog({
        type: "LOCK_DEVICE",
        reference: command.reference,
        status: "SUCCESSFUL",
        message: "Device locked remotely",
      });

      return;
    }

    if (command.type === "START_ALARM") {
      await startAlarm();

      await sendCommandResult({
        reference: command.reference,
        status: "SUCCESSFUL",
        message: "Alarm started successfully",
      });

      addLog({
        type: "START_ALARM",
        reference: command.reference,
        status: "SUCCESSFUL",
        message: "Alarm started remotely",
      });

      return;
    }

    if (command.type === "STOP_ALARM") {
      await stopAlarm();

      await sendCommandResult({
        reference: command.reference,
        status: "SUCCESSFUL",
        message: "Alarm stopped successfully",
      });

      addLog({
        type: "STOP_ALARM",
        reference: command.reference,
        status: "SUCCESSFUL",
        message: "Alarm stopped remotely",
      });

      return;
    }

    await sendCommandResult({
      reference: command.reference,
      status: "PROCESSING",
      message: "Command received by Android Gateway",
    });

    if (command.type === "SEND_SMS") {
      await sendSms({
        phoneNumber: command.phoneNumber,
        message: command.message,
        simSlot: command.simSlot,
        reference: command.reference,
      });

      await sendCommandResult({
        reference: command.reference,
        status: "SUCCESSFUL",
        message: "SMS sent successfully",
      });

      addLog({
        type: "SEND_SMS",
        reference: command.reference,
        status: "SUCCESSFUL",
        message: "SMS sent successfully",
      });

      return;
    }

if (command.type === "USSD") {
  const result = await sendUssd({
    ussdCode: command.ussdCode,
    reference: command.reference,
    simSlot: Number(command.simSlot ?? 0),
  });

  const ussdResponse =
    result?.response ||
    result?.message ||
    "";

  if (!ussdResponse) {
    throw new Error("Empty USSD response");
  }

  await sendCommandResult({
    reference: command.reference,
    status: "SUCCESSFUL",
    message: ussdResponse,
    response: ussdResponse,
    simSlot: Number(command.simSlot ?? 0),
  });

  addLog({
    type: "USSD",
    reference: command.reference,
    status: "SUCCESSFUL",
    message: ussdResponse,
  });

  return;
}
    await sendCommandResult({
      reference: command.reference,
      status: "FAILED",
      message: `Unsupported command type: ${command.type}`,
    });
  } catch (error) {
    addLog({
      type: command.type || "UNKNOWN",
      reference: command.reference,
      status: "FAILED",
      message: error.message || "Command failed",
    });

    await sendCommandResult({
      reference: command.reference,
      status: "FAILED",
      message: error.message || "Command failed",
    });
  }
};

const queuedCommandHandler = (command) => {
  addToQueue(command, handleCommand);
};

export const connectGatewaySocket = async () => {
  const deviceId = await getDeviceId();
  const secretKey = await getDeviceToken();

  if (!deviceId || !secretKey) return null;

  if (socket?.connected) return socket;

  socket = io("https://ayax-api-marketplace.onrender.com", {
    transports: ["websocket"],
    auth: {
      deviceId,
      secretKey,
    },
    reconnection: true,
    reconnectionAttempts: Infinity,
    reconnectionDelay: 3000,
  });

  socket.on("connect", () => {
    console.log("Gateway socket connected:", socket.id);

    socket.emit("join", deviceId);

    socket.emit("gateway-device-online", {
      deviceId,
      secretKey,
    });
  });

  socket.off("gateway-command", queuedCommandHandler);
  socket.on("gateway-command", queuedCommandHandler);

  socket.on("disconnect", () => {
    console.log("Gateway socket disconnected");
  });

  socket.on("connect_error", (error) => {
    console.log("Gateway socket error:", error.message);
  });

  return socket;
};

export const getGatewaySocket = () => socket;

export const disconnectGatewaySocket = () => {
  if (socket) {
    socket.off("gateway-command", queuedCommandHandler);
    socket.disconnect();
    socket = null;
  }
};
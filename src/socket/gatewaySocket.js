import { io } from "socket.io-client";
import {
  getDeviceId,
  getDeviceToken,
} from "../storage/deviceStorage";
import { sendCommandResult } from "../services/commandService";
import { sendSms } from "../services/smsService";
import { sendUssd } from "../services/ussdService";
import { addToQueue } from "../services/queueService";
import { addLog } from "../services/logService";
import {
  startAlarm,
  stopAlarm,
} from "../services/deviceManagerService";
import { lockDevice } from "../services/devicePolicyService";

const SOCKET_URL = "https://ayax-api-marketplace.onrender.com";

let socket = null;

const saveLog = ({
  type,
  reference,
  status,
  message,
}) => {
  try {
    addLog({
      type,
      reference,
      status,
      message,
    });
  } catch (error) {
    console.log("Unable to save local log:", error?.message);
  }
};

const reportResult = async ({
  reference,
  status,
  message,
  response,
  simSlot,
}) => {
  try {
    return await sendCommandResult({
      reference,
      status,
      message,
      response,
      simSlot,
    });
  } catch (error) {
    console.log(
      `Failed to report command ${reference}:`,
      error?.response?.data?.message ||
        error?.message ||
        "Unknown reporting error"
    );

    throw error;
  }
};

const handleLockCommand = async (command) => {
  await lockDevice();

  await reportResult({
    reference: command.reference,
    status: "SUCCESSFUL",
    message: "Device locked successfully",
  });

  saveLog({
    type: "LOCK_DEVICE",
    reference: command.reference,
    status: "SUCCESSFUL",
    message: "Device locked remotely",
  });
};

const handleStartAlarmCommand = async (command) => {
  await startAlarm();

  await reportResult({
    reference: command.reference,
    status: "SUCCESSFUL",
    message: "Alarm service started successfully",
  });

  saveLog({
    type: "START_ALARM",
    reference: command.reference,
    status: "SUCCESSFUL",
    message: "Alarm started remotely",
  });
};

const handleStopAlarmCommand = async (command) => {
  await stopAlarm();

  await reportResult({
    reference: command.reference,
    status: "SUCCESSFUL",
    message: "Alarm service stopped successfully",
  });

  saveLog({
    type: "STOP_ALARM",
    reference: command.reference,
    status: "SUCCESSFUL",
    message: "Alarm stopped remotely",
  });
};

const handleSmsCommand = async (command) => {
  const payload = command.payload || {};

  const phoneNumber =
    command.phoneNumber ||
    payload.phoneNumber ||
    payload.phone;

  const message =
    command.message ||
    payload.message;

  const simSlot = Number(
    command.simSlot ??
      payload.simSlot ??
      0
  );

  if (!phoneNumber) {
    throw new Error("SMS phone number is missing");
  }

  if (!message) {
    throw new Error("SMS message is missing");
  }

  await sendSms({
    phoneNumber,
    message,
    simSlot,
    reference: command.reference,
  });

  await reportResult({
    reference: command.reference,
    status: "SUCCESSFUL",
    message: "SMS sent successfully",
    simSlot,
  });

  saveLog({
    type: "SEND_SMS",
    reference: command.reference,
    status: "SUCCESSFUL",
    message: `SMS sent to ${phoneNumber}`,
  });
};

const handleUssdCommand = async (command) => {
  const payload = command.payload || {};

  const ussdCode =
    command.ussdCode ||
    payload.ussdCode;

  const simSlot = Number(
    command.simSlot ??
      payload.simSlot ??
      0
  );

  if (!ussdCode) {
    throw new Error("USSD code is missing");
  }

  console.log("Starting USSD command:", {
    reference: command.reference,
    ussdCode,
    simSlot,
  });

  const result = await sendUssd({
    ussdCode,
    reference: command.reference,
    simSlot,
  });

  console.log("USSD native result:", result);

  const ussdResponse = String(
    result?.response ||
      result?.message ||
      ""
  ).trim();

  if (!ussdResponse) {
    throw new Error(
      "Empty USSD response received from Android"
    );
  }

  await reportResult({
    reference: command.reference,
    status: "SUCCESSFUL",
    message: ussdResponse,
    response: ussdResponse,
    simSlot,
  });

  saveLog({
    type: "USSD",
    reference: command.reference,
    status: "SUCCESSFUL",
    message: ussdResponse,
  });
};

const handleCommand = async (command) => {
  if (!command?.reference) {
    console.log(
      "Invalid gateway command: reference is missing",
      command
    );
    return;
  }

  const commandType = String(
    command.type || ""
  ).toUpperCase();

  saveLog({
    type: commandType || "UNKNOWN",
    reference: command.reference,
    status: "RECEIVED",
    message: "Command received",
  });

  try {
    switch (commandType) {
      case "LOCK_DEVICE":
        await handleLockCommand(command);
        return;

      case "START_ALARM":
        await handleStartAlarmCommand(command);
        return;

      case "STOP_ALARM":
        await handleStopAlarmCommand(command);
        return;

      case "SEND_SMS":
        await reportResult({
          reference: command.reference,
          status: "PROCESSING",
          message: "SMS command is being processed",
        });

        await handleSmsCommand(command);
        return;

      case "USSD":
      case "CHECK_BALANCE":
        await reportResult({
          reference: command.reference,
          status: "PROCESSING",
          message: "USSD command is being processed",
        });

        await handleUssdCommand(command);
        return;

      default:
        throw new Error(
          `Unsupported command type: ${
            commandType || "UNKNOWN"
          }`
        );
    }
  } catch (error) {
    const errorMessage =
      error?.response?.data?.message ||
      error?.message ||
      "Command failed";

    saveLog({
      type: commandType || "UNKNOWN",
      reference: command.reference,
      status: "FAILED",
      message: errorMessage,
    });

    try {
      await reportResult({
        reference: command.reference,
        status: "FAILED",
        message: errorMessage,
      });
    } catch (reportError) {
      console.log(
        "Command failed and result reporting also failed:",
        reportError?.message
      );
    }
  }
};

const queuedCommandHandler = (command) => {
  addToQueue(command, handleCommand);
};

export const connectGatewaySocket = async () => {
  const deviceId = await getDeviceId();
  const secretKey = await getDeviceToken();

  if (!deviceId || !secretKey) {
    console.log(
      "Gateway socket not connected: device is not paired"
    );
    return null;
  }

  if (socket?.connected) {
    return socket;
  }

  if (socket) {
    socket.removeAllListeners();
    socket.disconnect();
    socket = null;
  }

  socket = io(SOCKET_URL, {
    transports: ["websocket"],
    auth: {
      deviceId,
      secretKey,
    },
    reconnection: true,
    reconnectionAttempts: Infinity,
    reconnectionDelay: 3000,
    reconnectionDelayMax: 10000,
    timeout: 20000,
  });

  socket.on("connect", () => {
    console.log(
      "Gateway socket connected:",
      socket.id
    );

    socket.emit("join", deviceId);

    socket.emit("gateway-device-online", {
      deviceId,
      secretKey,
    });
  });

  socket.off(
    "gateway-command",
    queuedCommandHandler
  );

  socket.on(
    "gateway-command",
    queuedCommandHandler
  );

  socket.on("disconnect", (reason) => {
    console.log(
      "Gateway socket disconnected:",
      reason
    );
  });

  socket.on("connect_error", (error) => {
    console.log(
      "Gateway socket connection error:",
      error?.message
    );
  });

  socket.on("error", (error) => {
    console.log(
      "Gateway socket error:",
      error?.message || error
    );
  });

  return socket;
};

export const getGatewaySocket = () => socket;

export const disconnectGatewaySocket = () => {
  if (!socket) return;

  socket.off(
    "gateway-command",
    queuedCommandHandler
  );

  socket.removeAllListeners();
  socket.disconnect();
  socket = null;
};
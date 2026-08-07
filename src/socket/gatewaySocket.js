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

const SOCKET_URL =
  "https://ayax-api-marketplace.onrender.com";

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
    console.log(
      "Unable to save local log:",
      error?.message
    );
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

const isWaitingForSmsResponse = (message = "") => {
  const text = String(message).toLowerCase();

  return (
    text.includes("you will receive an sms") ||
    text.includes("receive an sms") ||
    text.includes("sent to you via sms") ||
    text.includes("details shortly") ||
    text.includes("balance details shortly") ||
    text.includes("request is being processed")
  );
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
    simId: command.simId,
    balanceType: command.balanceType,
    service: command.service,
    network: command.network,
  });

  console.log("NATIVE RESULT");
  console.log(JSON.stringify(result, null, 2));

  const ussdResponse = String(
    result?.response ||
      result?.message ||
      ""
  ).trim();

  if (!ussdResponse) {
    await reportResult({
      reference: command.reference,
      status: "PROCESSING",
      message:
        "USSD request completed. Waiting for network response.",
      simSlot,
    });

    saveLog({
      type: "USSD",
      reference: command.reference,
      status: "PROCESSING",
      message:
        "Waiting for USSD or network SMS response",
    });

    return;
  }

  if (isWaitingForSmsResponse(ussdResponse)) {
    await reportResult({
      reference: command.reference,
      status: "PROCESSING",
      message: ussdResponse,
      response: ussdResponse,
      simSlot,
    });

    saveLog({
      type: "USSD",
      reference: command.reference,
      status: "PROCESSING",
      message: ussdResponse,
    });

    return;
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

  console.log("COMMAND TYPE:", commandType);
  console.log("REFERENCE:", command.reference);
  console.log("PAYLOAD:", JSON.stringify(command.payload || {}));

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
      case "BUY_DATA":
      case "BUY_AIRTIME":
      case "AIRTIME":
      case "DATA": {
        const simSlot = command.simSlot ?? command.payload?.simSlot ?? 0;
        
        await reportResult({
          reference: command.reference,
          status: "PROCESSING",
          message: "USSD command is being processed",
          simSlot,
        });

        await handleUssdCommand(command);
        console.log("USSD COMMAND STARTED");
        console.log("SIM SLOT:", simSlot);
        return;
      }

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
        simSlot:
          command.simSlot ??
          command.payload?.simSlot,
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

  socket.on("gateway-command", async (command) => {
    console.log("=================================");
    console.log("GATEWAY COMMAND RECEIVED");
    console.log(JSON.stringify(command, null, 2));
    console.log("=================================");

    queuedCommandHandler(command);
  });

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
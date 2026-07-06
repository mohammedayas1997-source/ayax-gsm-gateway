import { io } from "socket.io-client";
import { getDeviceId, getDeviceToken } from "../storage/deviceStorage";
import { sendCommandResult } from "../services/commandService";
import { sendSms } from "../services/smsService";
import { sendUssd } from "../services/ussdService";
import { addToQueue } from "../services/queueService";
import { addLog } from "../services/logService";

let socket = null;

const handleCommand = async (command) => {
  try {
    if (!command?.reference) {
      console.log("Invalid command: missing reference");
      addLog({
        type: command.type,
        reference: command.reference,
        status: "RECEIVED",
        message: "Command received",
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
      await sendUssd({
        ussdCode: command.ussdCode,
        reference: command.reference,
      });

      await sendCommandResult({
        reference: command.reference,
        status: "PROCESSING",
        message: "USSD command started on Android device",
      });
      addLog({
  type: "USSD",
  reference: command.reference,
  status: "PROCESSING",
  message: "USSD command started",
});

      return;
    }

    await sendCommandResult({
      reference: command.reference,
      status: "FAILED",
      message: `Unsupported command type: ${command.type}`,
    });
  } catch (error) {
    await sendCommandResult({
      reference: command.reference,
      status: "FAILED",
      message: error.message || "Command failed",
    });
    addLog({
  type: command.type || "UNKNOWN",
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
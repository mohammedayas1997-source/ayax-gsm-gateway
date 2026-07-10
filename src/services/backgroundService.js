import BackgroundService from "react-native-background-actions";
import { sendHeartbeat } from "./heartbeatService";
import { connectGatewaySocket } from "../socket/gatewaySocket";
import { getSimInfo } from "./gsmService";
import { syncLocationToBackend } from "./locationService";
import { startMotionSecurity } from "./deviceManagerService";

const sleep = (time) =>
  new Promise((resolve) => setTimeout(resolve, time));

const gatewayTask = async () => {
  await connectGatewaySocket();
  await startMotionSecurity();

  while (BackgroundService.isRunning()) {
    try {
      await sendHeartbeat();
      await getSimInfo();
      await syncLocationToBackend();
    } catch (error) {
      console.log("Background gateway error:", error.message);
    }

    await sleep(30000);
  }
};

const options = {
  taskName: "Ayax GSM Gateway",
  taskTitle: "Ayax GSM Gateway Running",
  taskDesc: "Monitoring SMS, USSD, GPS, security and gateway commands.",
  taskIcon: {
    name: "ic_launcher",
    type: "mipmap",
  },
  color: "#1565ff",
  linkingURI: "ayaxgsmgateway://dashboard",
  parameters: {},
};

export const startGatewayBackgroundService = async () => {
  const isRunning = await BackgroundService.isRunning();

  if (!isRunning) {
    await BackgroundService.start(gatewayTask, options);
  }
};

export const stopGatewayBackgroundService = async () => {
  const isRunning = await BackgroundService.isRunning();

  if (isRunning) {
    await BackgroundService.stop();
  }
};
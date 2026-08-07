import BackgroundService from "react-native-background-actions";
import { sendHeartbeat } from "./heartbeatService";
import { connectGatewaySocket } from "../socket/gatewaySocket";
import { syncSimInfo } from "./gsmService";
import { syncLocationToBackend } from "./locationService";
import { startMotionSecurity } from "./deviceManagerService";

const sleep = (time) =>
  new Promise((resolve) => setTimeout(resolve, time));

const gatewayTask = async () => {
  try {
    console.log("Ayax Gateway Background Task starting...");
    await connectGatewaySocket();
    await startMotionSecurity();
  } catch (error) {
    console.log("Failed to initialize background tasks:", error?.message);
  }

  while (BackgroundService.isRunning()) {
    try {
      await sendHeartbeat();
      await syncSimInfo();
      await syncLocationToBackend();
    } catch (error) {
      console.log("Background gateway iteration error:", error?.message);
    }

    // Jiran sakan 30 kafin a maimaita aikin na gaba
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
  try {
    const isRunning = await BackgroundService.isRunning();

    if (!isRunning) {
      // An cire await daga gaban start() kamar yadda dokar react-native-background-actions take bukata
      BackgroundService.start(gatewayTask, options).catch((err) => {
        console.log("Error starting background service:", err);
      });
    }
  } catch (error) {
    console.log("Failed to check background service status:", error?.message);
  }
};

export const stopGatewayBackgroundService = async () => {
  try {
    const isRunning = await BackgroundService.isRunning();

    if (isRunning) {
      await BackgroundService.stop();
    }
  } catch (error) {
    console.log("Failed to stop background service:", error?.message);
  }
};
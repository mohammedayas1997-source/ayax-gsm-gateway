import DeviceInfo from "react-native-device-info";
import api from "../api/client";
import { getDeviceId, getDeviceToken } from "../storage/deviceStorage";

export const sendHeartbeat = async () => {
  try {
    const deviceId = await getDeviceId();
    const secretKey = await getDeviceToken();

    if (!deviceId || !secretKey) {
      return null;
    }

    // Dauko bayanan batir da caji tare da kariya
    let battery = 100;
    let charging = false;

    try {
      const batteryLevelRaw = await DeviceInfo.getBatteryLevel();
      // Idan batteryLevelRaw ya dawo da -1 (misali a emulator), a sa 100 ko 0
      battery = batteryLevelRaw >= 0 ? Math.round(batteryLevelRaw * 100) : 100;
      charging = await DeviceInfo.isBatteryCharging();
    } catch (deviceInfoError) {
      console.log("Error fetching device battery info:", deviceInfoError?.message);
    }

    const payload = {
      deviceId,
      secretKey,
      battery,
      charging,
      signal: 0,
      internet: true,
    };

    const res = await api.post("/gateway/heartbeat", payload);

    return res.data;
  } catch (error) {
    console.log(
      "Failed to send heartbeat:",
      error?.response?.data?.message || error?.message || "Unknown error"
    );
    // Ba a fitar da throw error anan ba, domin hakan zai hana background loop din cigaba da aiki idan intanet ta dauke na dan lokaci.
    return null;
  }
};
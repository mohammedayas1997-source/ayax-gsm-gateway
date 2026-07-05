import DeviceInfo from "react-native-device-info";
import api from "../api/client";
import { getDeviceId, getDeviceToken } from "../storage/deviceStorage";

export const sendHeartbeat = async () => {
  const deviceId = await getDeviceId();
  const secretKey = await getDeviceToken();

  if (!deviceId || !secretKey) return null;

  const batteryLevelRaw = await DeviceInfo.getBatteryLevel();
  const battery = Math.round(batteryLevelRaw * 100);

  const charging = await DeviceInfo.isBatteryCharging();

  const res = await api.post("/gateway/heartbeat", {
    deviceId,
    secretKey,
    battery,
    charging,
    signal: 0,
    internet: true,
  });

  return res.data;
};
import api from "../api/client";
import { getDeviceId, getDeviceToken } from "../storage/deviceStorage";
import { getSimInfo } from "./gsmService";

export const syncSimsToBackend = async () => {
  const deviceId = await getDeviceId();
  const secretKey = await getDeviceToken();

  if (!deviceId || !secretKey) return null;

  const simInfo = await getSimInfo();

  const sims = (simInfo?.sims || []).map((sim) => ({
    ...sim,
    airtimeBalance: 0,
    dataBalance: null,
  }));

  const res = await api.post("/gateway/sims/sync", {
    deviceId,
    secretKey,
    sims,
  });

  return res.data;
};
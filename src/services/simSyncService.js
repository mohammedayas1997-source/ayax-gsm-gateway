import api from "../api/client";
import { getDeviceId, getDeviceToken } from "../storage/deviceStorage";
import { getSimInfo } from "./gsmService";

export const syncSimsToBackend = async () => {
  try {
    const deviceId = await getDeviceId();
    const secretKey = await getDeviceToken();

    if (!deviceId || !secretKey) {
      return null;
    }

    const simInfo = await getSimInfo();
    const rawSims = Array.isArray(simInfo?.sims) ? simInfo.sims : [];

    const sims = rawSims.map((sim) => ({
      slotIndex: Number(sim.slotIndex ?? 0),
      subscriptionId: Number(sim.subscriptionId ?? 0),
      carrierName: sim.carrierName || "Unknown",
      displayName: sim.displayName || sim.carrierName || "Unknown",
      phoneNumber: sim.number || sim.phoneNumber || "",
      number: sim.number || sim.phoneNumber || "",
      countryIso: sim.countryIso || "",
      mcc: sim.mcc !== null && sim.mcc !== undefined ? Number(sim.mcc) : null,
      mnc: sim.mnc !== null && sim.mnc !== undefined ? Number(sim.mnc) : null,
      airtimeBalance: 0,
      dataBalance: null,
    }));

    const payload = {
      deviceId,
      secretKey,
      sims,
    };

    const res = await api.post("/gateway/sims/sync", payload);

    return res.data;
  } catch (error) {
    console.log(
      "Failed to sync SIMs to backend:",
      error?.response?.data?.message || error?.message || "Unknown error"
    );
    throw error;
  }
};
import {
  NativeModules,
  PermissionsAndroid,
  Platform,
} from "react-native";

import api from "../api/client";
import {
  getDeviceId,
  getDeviceToken,
} from "../storage/deviceStorage";

const { GsmModule } = NativeModules;

export const requestGsmPermissions = async () => {
  if (Platform.OS !== "android") return true;

  const result = await PermissionsAndroid.requestMultiple([
    PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
    PermissionsAndroid.PERMISSIONS.READ_PHONE_NUMBERS,
  ]);

  return Object.values(result).every(
    (status) =>
      status === PermissionsAndroid.RESULTS.GRANTED
  );
};

export const getSimInfo = async () => {
  const granted = await requestGsmPermissions();

  if (!granted) {
    throw new Error("Phone permissions denied");
  }

  if (!GsmModule) {
    throw new Error("GsmModule not linked");
  }

  return GsmModule.getSimInfo();
};

export const syncSimInfo = async () => {
  const deviceId = await getDeviceId();
  const secretKey = await getDeviceToken();

  if (!deviceId || !secretKey) {
    throw new Error("Device not paired");
  }

  const simInfo = await getSimInfo();
  const sims = Array.isArray(simInfo?.sims)
    ? simInfo.sims
    : [];

  if (sims.length === 0) {
    throw new Error("No active SIM cards found");
  }

  const payload = {
    deviceId,
    secretKey,
    sims: sims.map((sim) => ({
      slotIndex: Number(sim.slotIndex),
      subscriptionId: Number(sim.subscriptionId),
      carrierName: sim.carrierName || "Unknown",
      displayName: sim.displayName || "Unknown",
      phoneNumber: sim.number || "",
      countryIso: sim.countryIso || "",
      mcc:
        sim.mcc === null || sim.mcc === undefined
          ? null
          : Number(sim.mcc),
      mnc:
        sim.mnc === null || sim.mnc === undefined
          ? null
          : Number(sim.mnc),
    })),
  };

  const response = await api.post(
    "/gateway/sims/sync",
    payload
  );

  return {
    ...simInfo,
    syncResult: response.data,
  };
};
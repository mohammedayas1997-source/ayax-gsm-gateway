import { NativeModules, PermissionsAndroid, Platform } from "react-native";
import api from "../api/client";
import { getDeviceId, getDeviceToken } from "../storage/deviceStorage";

const { GsmModule } = NativeModules;

export const requestGsmPermissions = async () => {
  if (Platform.OS !== "android") return true;

  const permissions = [
    PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
    PermissionsAndroid.PERMISSIONS.READ_PHONE_NUMBERS,
  ];

  const result = await PermissionsAndroid.requestMultiple(permissions);

  return Object.values(result).every(
    (status) => status === PermissionsAndroid.RESULTS.GRANTED
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

  const simInfo = await GsmModule.getSimInfo();

  await syncSimsToBackend(simInfo);

  return simInfo;
};

export const syncSimsToBackend = async (simInfo) => {
  const deviceId = await getDeviceId();
  const secretKey = await getDeviceToken();

  if (!deviceId || !secretKey) {
    throw new Error("Device not paired");
  }

  const sims = simInfo?.sims || [];

  const res = await api.post("/gateway/sims/sync", {
    deviceId,
    secretKey,
    sims: sims.map((sim) => ({
      slotIndex: sim.slotIndex,
      carrierName: sim.carrierName,
      displayName: sim.displayName,
      phoneNumber: sim.number || "",
      countryIso: sim.countryIso,
      mcc: sim.mcc,
      mnc: sim.mnc,
    })),
  });

  return res.data;
};
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
  try {
    if (Platform.OS !== "android") return true;

    const result = await PermissionsAndroid.requestMultiple([
      PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
      PermissionsAndroid.PERMISSIONS.READ_PHONE_NUMBERS,
    ]);

    return Object.values(result).every(
      (status) =>
        status === PermissionsAndroid.RESULTS.GRANTED
    );
  } catch (error) {
    console.log("Error requesting GSM permissions:", error?.message);
    return false;
  }
};

export const getSimInfo = async () => {
  try {
    const granted = await requestGsmPermissions();

    if (!granted) {
      throw new Error("Phone permissions denied");
    }

    if (!GsmModule) {
      throw new Error("GsmModule not linked or available in NativeModules");
    }

    if (typeof GsmModule.getSimInfo !== "function") {
      throw new Error("getSimInfo method is not available on GsmModule");
    }

    return await GsmModule.getSimInfo();
  } catch (error) {
    console.log("Error getting SIM info:", error?.message);
    throw error;
  }
};

export const syncSimInfo = async () => {
  try {
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
        slotIndex: Number(sim.slotIndex ?? 0),
        subscriptionId: Number(sim.subscriptionId ?? 0),

        carrierName:
          sim.carrierName || "Unknown",

        displayName:
          sim.displayName ||
          sim.carrierName ||
          "Unknown",

        phoneNumber:
          sim.number ||
          sim.phoneNumber ||
          "",

        number:
          sim.number ||
          sim.phoneNumber ||
          "",

        countryIso:
          sim.countryIso || "",

        mcc:
          sim.mcc === null ||
          sim.mcc === undefined
            ? null
            : Number(sim.mcc),

        mnc:
          sim.mnc === null ||
          sim.mnc === undefined
            ? null
            : Number(sim.mnc),
      })),
    };

    const response = await api.post("/gateway/sims/sync", payload);

    return {
      ...simInfo,
      syncResult: response.data,
    };
  } catch (error) {
    console.log("Error syncing SIM info to backend:", error?.response?.data?.message || error?.message);
    throw error;
  }
};
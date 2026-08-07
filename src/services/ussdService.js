import {
  NativeModules,
  PermissionsAndroid,
  Platform,
} from "react-native";

import {
  getDeviceId,
  getDeviceToken,
} from "../storage/deviceStorage";

const { GsmModule } = NativeModules;

export const requestUssdPermission = async () => {
  try {
    if (Platform.OS !== "android") return true;

    const result = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.CALL_PHONE
    );

    return (
      result ===
      PermissionsAndroid.RESULTS.GRANTED
    );
  } catch (error) {
    console.log("Error requesting USSD permission:", error?.message);
    return false;
  }
};

export const sendUssd = async ({
  ussdCode,
  reference,
  simSlot = 0,
  simId = "",
  balanceType = "",
  service = "",
  network = "",
  replies = [],
}) => {
  try {
    const granted = await requestUssdPermission();

    if (!granted) {
      throw new Error("CALL_PHONE permission denied");
    }

    const deviceId = await getDeviceId();
    const secretKey = await getDeviceToken();

    if (!deviceId || !secretKey) {
      throw new Error("Device not paired");
    }

    if (!GsmModule) {
      throw new Error("GsmModule not linked or available in NativeModules");
    }

    if (!ussdCode) {
      throw new Error("USSD code is missing");
    }

    // Sanya amsoshin USSD idan akwai su kafin a kira aikin
    if (
      typeof GsmModule.setUssdReplies === "function" &&
      Array.isArray(replies) &&
      replies.length > 0
    ) {
      await GsmModule.setUssdReplies(replies);
    }

    if (typeof GsmModule.sendUssdWithSim === "function") {
      return await GsmModule.sendUssdWithSim(
        String(ussdCode),
        String(reference || ""),
        deviceId,
        secretKey,
        Number(simSlot ?? 0),
        String(simId || ""),
        String(balanceType || ""),
        String(service || ""),
        String(network || "")
      );
    }

    if (typeof GsmModule.sendUssd === "function") {
      return await GsmModule.sendUssd(
        String(ussdCode),
        String(reference || ""),
        deviceId,
        secretKey
      );
    }

    throw new Error("No valid USSD method available on GsmModule");
  } catch (error) {
    console.log(
      `Failed to execute USSD [${ussdCode}] with reference [${reference}]:`,
      error?.message || error
    );
    throw error;
  }
};
import { NativeModules, PermissionsAndroid, Platform } from "react-native";
import { getDeviceId, getDeviceToken } from "../storage/deviceStorage";

const { GsmModule } = NativeModules;

export const requestSmsPermissions = async () => {
  try {
    if (Platform.OS !== "android") return true;

    const result = await PermissionsAndroid.requestMultiple([
      PermissionsAndroid.PERMISSIONS.SEND_SMS,
      PermissionsAndroid.PERMISSIONS.READ_SMS,
      PermissionsAndroid.PERMISSIONS.RECEIVE_SMS,
    ]);

    return Object.values(result).every(
      (status) => status === PermissionsAndroid.RESULTS.GRANTED
    );
  } catch (error) {
    console.log("Error requesting SMS permissions:", error?.message);
    return false;
  }
};

export const sendSms = async ({
  phoneNumber,
  message,
  simSlot = 0,
  reference,
}) => {
  try {
    const granted = await requestSmsPermissions();

    if (!granted) {
      throw new Error("SMS permissions denied");
    }

    const deviceId = await getDeviceId();
    const secretKey = await getDeviceToken();

    if (!deviceId || !secretKey) {
      throw new Error("Device not paired");
    }

    if (!GsmModule) {
      throw new Error("GsmModule not linked or available in NativeModules");
    }

    if (!phoneNumber || !message) {
      throw new Error("Phone number or message is missing");
    }

    if (typeof GsmModule.sendSmsWithSim === "function") {
      return await GsmModule.sendSmsWithSim(
        String(phoneNumber),
        String(message),
        Number(simSlot),
        reference || "",
        deviceId,
        secretKey
      );
    }

    if (typeof GsmModule.sendSms === "function") {
      return await GsmModule.sendSms(
        String(phoneNumber),
        String(message)
      );
    }

    throw new Error("No valid SMS method available on GsmModule");
  } catch (error) {
    console.log(
      `Failed to send SMS to [${phoneNumber}] with reference [${reference}]:`,
      error?.message || error
    );
    throw error;
  }
};
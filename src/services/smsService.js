import { NativeModules, PermissionsAndroid, Platform } from "react-native";
import { getDeviceId, getDeviceToken } from "../storage/deviceStorage";

const { GsmModule } = NativeModules;

export const requestSmsPermissions = async () => {
  if (Platform.OS !== "android") return true;

  const result = await PermissionsAndroid.requestMultiple([
    PermissionsAndroid.PERMISSIONS.SEND_SMS,
    PermissionsAndroid.PERMISSIONS.READ_SMS,
    PermissionsAndroid.PERMISSIONS.RECEIVE_SMS,
  ]);

  return Object.values(result).every(
    (status) => status === PermissionsAndroid.RESULTS.GRANTED
  );
};

export const sendSms = async ({
  phoneNumber,
  message,
  simSlot = 0,
  reference,
}) => {
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
    throw new Error("GsmModule not linked");
  }

  if (GsmModule.sendSmsWithSim) {
    return GsmModule.sendSmsWithSim(
      phoneNumber,
      message,
      Number(simSlot),
      reference,
      deviceId,
      secretKey
    );
  }

  return GsmModule.sendSms(phoneNumber, message);
};
import { NativeModules, PermissionsAndroid, Platform } from "react-native";

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

export const sendSms = async ({ phoneNumber, message }) => {
  const granted = await requestSmsPermissions();

  if (!granted) {
    throw new Error("SMS permissions denied");
  }

  if (!GsmModule) {
    throw new Error("GsmModule not linked");
  }

  return GsmModule.sendSms(phoneNumber, message);
};
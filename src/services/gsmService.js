import { NativeModules, PermissionsAndroid, Platform } from "react-native";

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

  return GsmModule.getSimInfo();
};
import AsyncStorage from "@react-native-async-storage/async-storage";
import { NativeModules } from "react-native";

const { GsmModule } = NativeModules;

const DEVICE_TOKEN = "AYAX_DEVICE_TOKEN";
const DEVICE_ID = "AYAX_DEVICE_ID";

export const saveDeviceToken = async (token) => {
  await AsyncStorage.setItem(DEVICE_TOKEN, token);
};

export const getDeviceToken = async () => {
  return AsyncStorage.getItem(DEVICE_TOKEN);
};

export const saveDeviceId = async (id) => {
  await AsyncStorage.setItem(DEVICE_ID, id);
};

export const getDeviceId = async () => {
  return AsyncStorage.getItem(DEVICE_ID);
};

export const saveNativeDeviceCredentials = async (deviceId, secretKey) => {
  if (GsmModule?.saveDeviceCredentials) {
    await GsmModule.saveDeviceCredentials(deviceId, secretKey);
  }
};

export const clearDevice = async () => {
  await AsyncStorage.multiRemove([DEVICE_TOKEN, DEVICE_ID]);
};
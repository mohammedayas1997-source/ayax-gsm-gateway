import AsyncStorage from "@react-native-async-storage/async-storage";

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

export const clearDevice = async () => {
  await AsyncStorage.multiRemove([
    DEVICE_TOKEN,
    DEVICE_ID,
  ]);
};
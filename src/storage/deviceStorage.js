import AsyncStorage from "@react-native-async-storage/async-storage";
import { NativeModules } from "react-native";

const { GsmModule } = NativeModules;

const DEVICE_TOKEN = "AYAX_DEVICE_TOKEN";
const DEVICE_ID = "AYAX_DEVICE_ID";

export const saveDeviceToken = async (token) => {
  try {
    if (!token) {
      throw new Error("Device token is empty");
    }
    await AsyncStorage.setItem(DEVICE_TOKEN, String(token));
  } catch (error) {
    console.log("Error saving device token:", error?.message);
    throw error;
  }
};

export const getDeviceToken = async () => {
  try {
    return await AsyncStorage.getItem(DEVICE_TOKEN);
  } catch (error) {
    console.log("Error getting device token:", error?.message);
    return null;
  }
};

export const saveDeviceId = async (id) => {
  try {
    if (!id) {
      throw new Error("Device ID is empty");
    }
    await AsyncStorage.setItem(DEVICE_ID, String(id));
  } catch (error) {
    console.log("Error saving device ID:", error?.message);
    throw error;
  }
};

export const getDeviceId = async () => {
  try {
    return await AsyncStorage.getItem(DEVICE_ID);
  } catch (error) {
    console.log("Error getting device ID:", error?.message);
    return null;
  }
};

export const saveNativeDeviceCredentials = async (deviceId, secretKey) => {
  try {
    if (GsmModule && typeof GsmModule.saveDeviceCredentials === "function") {
      await GsmModule.saveDeviceCredentials(
        String(deviceId || ""),
        String(secretKey || "")
      );
    }
  } catch (error) {
    console.log("Error saving native device credentials:", error?.message);
  }
};

export const clearDevice = async () => {
  try {
    await AsyncStorage.multiRemove([DEVICE_TOKEN, DEVICE_ID]);
    
    // Idan akwai buƙatar sharewa daga native ma a nan gaba, za a iya ƙara shi
  } catch (error) {
    console.log("Error clearing device storage:", error?.message);
    throw error;
  }
};
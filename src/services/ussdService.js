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
  if (Platform.OS !== "android") return true;

  const result = await PermissionsAndroid.request(
    PermissionsAndroid.PERMISSIONS.CALL_PHONE
  );

  return (
    result ===
    PermissionsAndroid.RESULTS.GRANTED
  );
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

  const granted =
    await requestUssdPermission();

  if (!granted) {
    throw new Error(
      "CALL_PHONE permission denied"
    );
  }

  const deviceId =
    await getDeviceId();

  const secretKey =
    await getDeviceToken();

  if (!deviceId || !secretKey) {
    throw new Error("Device not paired");
  }

  if (!GsmModule) {
    throw new Error(
      "GsmModule not linked"
    );
  }

  if (
    GsmModule.setUssdReplies &&
    Array.isArray(replies) &&
    replies.length > 0
  ) {
    await GsmModule.setUssdReplies(
      replies
    );
  }

  if (GsmModule.sendUssdWithSim) {

    return await GsmModule.sendUssdWithSim(

      ussdCode,

      reference,

      deviceId,

      secretKey,

      Number(simSlot),

      simId,

      balanceType,

      service,

      network

    );

  }

  return await GsmModule.sendUssd(

    ussdCode,

    reference,

    deviceId,

    secretKey

  );
};
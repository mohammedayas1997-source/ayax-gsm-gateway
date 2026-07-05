import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
} from "react-native";

import DeviceInfo from "react-native-device-info";

import api from "../api/client";

import {
  saveDeviceToken,
  saveDeviceId,
} from "../storage/deviceStorage";

export default function PairDeviceScreen({ navigation }) {
  const [deviceCode, setDeviceCode] = useState("");
  const [loading, setLoading] = useState(false);

  const register = async () => {
    if (!deviceCode.trim()) {
      return Alert.alert(
        "Validation",
        "Please enter your Device Pair Code."
      );
    }

    try {
      setLoading(true);

      const deviceName = await DeviceInfo.getDeviceName();

      const response = await api.post("/gateway/pair", {
        deviceName,
        deviceCode: deviceCode.trim(),
        location: "Ayax GSM Gateway",
      });

      const data = response.data;

      await saveDeviceToken(data.secretKey);
      await saveDeviceId(data.deviceId);

      Alert.alert(
        "Success",
        "Device paired successfully."
      );

      navigation.replace("Dashboard");
    } catch (error) {
      console.log(error?.response?.data || error);

      Alert.alert(
        "Pair Failed",
        error?.response?.data?.message ||
          "Unable to pair this device."
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>
        Ayax GSM Gateway
      </Text>

      <Text style={styles.subtitle}>
        Connect this Android device to your
        Ayax API Marketplace.
      </Text>

      <TextInput
        placeholder="Enter Device Pair Code"
        placeholderTextColor="#888"
        value={deviceCode}
        onChangeText={setDeviceCode}
        autoCapitalize="characters"
        style={styles.input}
      />

      <TouchableOpacity
        disabled={loading}
        style={styles.button}
        onPress={register}
      >
        {loading ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.btnText}>
            Pair Device
          </Text>
        )}
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#08111f",
    justifyContent: "center",
    padding: 24,
  },

  title: {
    color: "#fff",
    fontSize: 30,
    fontWeight: "bold",
    textAlign: "center",
  },

  subtitle: {
    color: "#9ca3af",
    textAlign: "center",
    marginTop: 10,
    marginBottom: 35,
    fontSize: 16,
    lineHeight: 24,
  },

  input: {
    backgroundColor: "#fff",
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 15,
    fontSize: 16,
    marginBottom: 20,
    color: "#000",
  },

  button: {
    backgroundColor: "#1565ff",
    paddingVertical: 16,
    borderRadius: 14,
    alignItems: "center",
  },

  btnText: {
    color: "#fff",
    fontWeight: "bold",
    fontSize: 17,
  },
});
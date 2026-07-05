import React, { useEffect, useState } from "react";
import { View, Text, StyleSheet, TouchableOpacity, Alert } from "react-native";
import { sendHeartbeat } from "../services/heartbeatService";
import { clearDevice } from "../storage/deviceStorage";

export default function DashboardScreen({ navigation }) {
  const [status, setStatus] = useState("Connecting...");
  const [battery, setBattery] = useState(0);

  const heartbeat = async () => {
    try {
      const res = await sendHeartbeat();
      setStatus("ONLINE");
      setBattery(res?.device?.battery || 0);
    } catch (error) {
      setStatus("OFFLINE");
    }
  };

  useEffect(() => {
    heartbeat();
    const timer = setInterval(heartbeat, 30000);
    return () => clearInterval(timer);
  }, []);

  const logout = async () => {
    await clearDevice();
    Alert.alert("Device cleared", "Pair this device again.");
    navigation.replace("Pair");
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Ayax GSM Gateway</Text>
      <Text style={styles.status}>Status: {status}</Text>
      <Text style={styles.info}>Battery: {battery}%</Text>

      <TouchableOpacity style={styles.button} onPress={heartbeat}>
        <Text style={styles.btnText}>Send Heartbeat</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.logout} onPress={logout}>
        <Text style={styles.btnText}>Clear Pairing</Text>
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
    marginBottom: 30,
  },
  status: {
    color: "#22c55e",
    fontSize: 20,
    textAlign: "center",
    marginBottom: 12,
  },
  info: {
    color: "#9ca3af",
    fontSize: 18,
    textAlign: "center",
    marginBottom: 25,
  },
  button: {
    backgroundColor: "#1565ff",
    paddingVertical: 16,
    borderRadius: 14,
    alignItems: "center",
    marginBottom: 12,
  },
  logout: {
    backgroundColor: "#dc2626",
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
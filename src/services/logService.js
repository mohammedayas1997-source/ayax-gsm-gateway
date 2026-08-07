let logs = [];
let listeners = [];

const notify = () => {
  listeners.forEach((listener) => {
    try {
      listener(logs);
    } catch (error) {
      console.log("Error in log listener callback:", error?.message);
    }
  });
};

export const addLog = (log) => {
  try {
    const newLog = {
      id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      time: new Date().toLocaleString(),
      type: log?.type || "INFO",
      reference: log?.reference || null,
      status: log?.status || "UNKNOWN",
      message: log?.message || "",
      ...log,
    };

    logs = [newLog, ...logs].slice(0, 100);
    notify();
  } catch (error) {
    console.log("Failed to add log:", error?.message);
  }
};

export const getLogs = () => logs;

export const subscribeLogs = (listener) => {
  if (typeof listener !== "function") return () => {};

  listeners.push(listener);
  try {
    listener(logs);
  } catch (error) {
    console.log("Error initializing log listener:", error?.message);
  }

  return () => {
    listeners = listeners.filter((item) => item !== listener);
  };
};

export const clearLogs = () => {
  logs = [];
  notify();
};
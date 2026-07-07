let logs = [];
let listeners = [];

const notify = () => {
  listeners.forEach((listener) => listener(logs));
};

export const addLog = (log) => {
  logs = [
    {
      id: Date.now().toString(),
      time: new Date().toLocaleString(),
      ...log,
    },
    ...logs,
  ].slice(0, 100);

  notify();
};

export const getLogs = () => logs;

export const subscribeLogs = (listener) => {
  listeners.push(listener);
  listener(logs);

  return () => {
    listeners = listeners.filter((item) => item !== listener);
  };
};
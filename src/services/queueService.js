const queue = [];
let processing = false;

let listeners = [];

const notify = () => {
  const status = getQueueStatus();
  listeners.forEach((listener) => listener(status));
};

export const addToQueue = async (command, handler) => {
  queue.push({ command, handler });
  notify();
  processQueue();
};

const processQueue = async () => {
  if (processing) return;

  processing = true;
  notify();

  while (queue.length > 0) {
    const job = queue.shift();
    notify();

    try {
      await job.handler(job.command);
    } catch (error) {
      console.log("Queue job failed:", error.message);
    }

    await wait(3000);
  }

  processing = false;
  notify();
};

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export const getQueueStatus = () => ({
  processing,
  pending: queue.length,
});

export const subscribeQueueStatus = (listener) => {
  listeners.push(listener);

  listener(getQueueStatus());

  return () => {
    listeners = listeners.filter((item) => item !== listener);
  };
};
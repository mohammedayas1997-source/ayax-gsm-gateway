const queue = [];
let processing = false;
let listeners = [];

const notify = () => {
  const status = getQueueStatus();
  listeners.forEach((listener) => {
    try {
      listener(status);
    } catch (error) {
      console.log("Error in queue status listener:", error?.message);
    }
  });
};

export const addToQueue = async (command, handler) => {
  try {
    if (!command || typeof handler !== "function") {
      console.log("Invalid command or handler passed to queue");
      return;
    }

    queue.push({ command, handler });
    notify();
    processQueue();
  } catch (error) {
    console.log("Failed to add to queue:", error?.message);
  }
};

const processQueue = async () => {
  if (processing) return;

  processing = true;
  notify();

  while (queue.length > 0) {
    const job = queue.shift();
    notify();

    try {
      if (job && typeof job.handler === "function") {
        await job.handler(job.command);
      }
    } catch (error) {
      console.log("Queue job failed:", error?.message || error);
    }

    // Jiran sakan 3 kafin a tafi ga umarni na gaba
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
  if (typeof listener !== "function") return () => {};

  listeners.push(listener);
  
  try {
    listener(getQueueStatus());
  } catch (error) {
    console.log("Error initializing queue listener:", error?.message);
  }

  return () => {
    listeners = listeners.filter((item) => item !== listener);
  };
};

export const clearQueue = () => {
  queue.length = 0;
  processing = false;
  notify();
};
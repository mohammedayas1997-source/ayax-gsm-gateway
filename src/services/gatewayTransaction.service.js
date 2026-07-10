const prisma = require("../config/prisma");
const { emitEvent } = require("../config/socket");

exports.markCommandProcessing = async ({ reference, message }) => {
  const command = await prisma.gsmCommand.update({
    where: { reference },
    data: {
      status: "PROCESSING",
      response: message || "Processing",
    },
  });

  emitEvent("gsm-command-updated", { command });

  return command;
};

exports.markCommandSuccessful = async ({ reference, message }) => {
  const command = await prisma.gsmCommand.update({
    where: { reference },
    data: {
      status: "SUCCESSFUL",
      response: message || "Successful",
      completedAt: new Date(),
    },
  });

  await prisma.transaction.updateMany({
    where: { reference },
    data: {
      status: "SUCCESSFUL",
    },
  });

  emitEvent("gsm-command-updated", { command });
  emitEvent("transaction-updated", { reference, status: "SUCCESSFUL" });

  return command;
};

exports.markCommandFailed = async ({ reference, message }) => {
  const command = await prisma.gsmCommand.update({
    where: { reference },
    data: {
      status: "FAILED",
      response: message || "Failed",
      completedAt: new Date(),
    },
  });

  const transaction = await prisma.transaction.findFirst({
    where: { reference },
  });

  if (transaction && transaction.status !== "FAILED") {
    await prisma.transaction.update({
      where: { id: transaction.id },
      data: { status: "FAILED" },
    });

    if (transaction.type === "DEBIT") {
      const wallet = await prisma.wallet.findUnique({
        where: { userId: transaction.userId },
      });

      if (wallet) {
        const balanceBefore = wallet.balance;
        const balanceAfter = balanceBefore + Number(transaction.amount);

        await prisma.wallet.update({
          where: { userId: transaction.userId },
          data: { balance: balanceAfter },
        });

        await prisma.walletLedger.create({
          data: {
            userId: transaction.userId,
            reference: `${reference}-REFUND`,
            type: "CREDIT",
            amount: transaction.amount,
            balanceBefore,
            balanceAfter,
            module: "REFUND",
            description: `Auto refund for failed transaction ${reference}`,
          },
        });

        emitEvent("wallet-updated", {
          userId: transaction.userId,
          balance: balanceAfter,
        });
      }
    }
  }

  emitEvent("gsm-command-updated", { command });
  emitEvent("transaction-updated", { reference, status: "FAILED" });

  return command;
};
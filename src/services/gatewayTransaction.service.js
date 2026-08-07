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
  // Yin amfani da transaction domin tabbatar da cewa duka sun yi nasara tare
  const result = await prisma.$transaction(async (tx) => {
    const command = await tx.gsmCommand.update({
      where: { reference },
      data: {
        status: "SUCCESSFUL",
        response: message || "Successful",
        completedAt: new Date(),
      },
    });

    await tx.transaction.updateMany({
      where: { reference },
      data: {
        status: "SUCCESSFUL",
      },
    });

    return command;
  });

  emitEvent("gsm-command-updated", { command: result });
  emitEvent("transaction-updated", { reference, status: "SUCCESSFUL" });

  return result;
};

exports.markCommandFailed = async ({ reference, message }) => {
  let updatedCommand;
  let refundedUserId = null;
  let newWalletBalance = null;

  // Yin amfani da Prisma transaction domin tsaron kudi (acid compliance)
  await prisma.$transaction(async (tx) => {
    updatedCommand = await tx.gsmCommand.update({
      where: { reference },
      data: {
        status: "FAILED",
        response: message || "Failed",
        completedAt: new Date(),
      },
    });

    const transaction = await tx.transaction.findFirst({
      where: { reference },
    });

    if (transaction && transaction.status !== "FAILED") {
      await tx.transaction.update({
        where: { id: transaction.id },
        data: { status: "FAILED" },
      });

      if (transaction.type === "DEBIT") {
        const wallet = await tx.wallet.findUnique({
          where: { userId: transaction.userId },
        });

        if (wallet) {
          const balanceBefore = wallet.balance;
          const balanceAfter = balanceBefore + Number(transaction.amount);
          refundedUserId = transaction.userId;
          newWalletBalance = balanceAfter;

          await tx.wallet.update({
            where: { userId: transaction.userId },
            data: { balance: balanceAfter },
          });

          await tx.walletLedger.create({
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
        }
      }
    }
  });

  // Tura abubuwan da suka faru ta Socket bayan DB transaction ya gama lodi lafiya
  emitEvent("gsm-command-updated", { command: updatedCommand });
  emitEvent("transaction-updated", { reference, status: "FAILED" });

  if (refundedUserId !== null && newWalletBalance !== null) {
    emitEvent("wallet-updated", {
      userId: refundedUserId,
      balance: newWalletBalance,
    });
  }

  return updatedCommand;
};
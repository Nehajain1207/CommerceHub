import { api } from "./api";
import type { PaymentInfo } from "./types";

// Opens the payment step for an order and reports how it ended.
//
// With Razorpay keys configured on the server, this opens Razorpay's own payment
// window. Card details are typed into Razorpay's window and never touch our code.
// Without keys the server is in demo mode, and a simple confirmation stands in.
//
// Either way, "paid" is only believed after the SERVER has checked the signature.

export type PaymentOutcome = "paid" | "cancelled";

const CHECKOUT_SCRIPT = "https://checkout.razorpay.com/v1/checkout.js";

interface RazorpaySuccess {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string;
}

interface RazorpayOptions {
  key: string;
  order_id: string;
  amount: number;
  currency: string;
  name: string;
  description: string;
  prefill?: { name?: string; email?: string };
  theme?: { color: string };
  handler: (response: RazorpaySuccess) => void;
  modal: { ondismiss: () => void };
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => { open: () => void };
  }
}

function loadCheckoutScript(): Promise<void> {
  if (window.Razorpay) {
    return Promise.resolve();
  }

  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = CHECKOUT_SCRIPT;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Could not load the payment window. Check your internet connection."));
    document.body.appendChild(script);
  });
}

export async function payForOrder(
  orderNumber: string,
  payment: PaymentInfo,
  customer: { name: string; email: string },
): Promise<PaymentOutcome> {
  if (payment.demo) {
    const rupees = (payment.amountPaise / 100).toLocaleString("en-IN");
    const agreed = window.confirm(
      `Demo payment\n\nPay ₹${rupees} for order ${orderNumber}?\n\n` +
        "No payment company is connected, so no money moves. " +
        "Press OK to pay, or Cancel to leave the order unpaid.",
    );

    if (!agreed) {
      return "cancelled";
    }

    await api.demoConfirmPayment(orderNumber);
    return "paid";
  }

  await loadCheckoutScript();

  return new Promise<PaymentOutcome>((resolve, reject) => {
    const Razorpay = window.Razorpay;

    if (!Razorpay) {
      reject(new Error("The payment window is not available."));
      return;
    }

    const checkout = new Razorpay({
      key: payment.publicKey,
      order_id: payment.gatewayOrderId,
      amount: payment.amountPaise,
      currency: payment.currency,
      name: "CommerceHub",
      description: `Order ${orderNumber}`,
      prefill: { name: customer.name, email: customer.email },
      theme: { color: "#16233f" },
      handler: (response) => {
        api
          .verifyPayment(
            orderNumber,
            response.razorpay_order_id,
            response.razorpay_payment_id,
            response.razorpay_signature,
          )
          .then(() => resolve("paid"))
          .catch(reject);
      },
      modal: { ondismiss: () => resolve("cancelled") },
    });

    checkout.open();
  });
}

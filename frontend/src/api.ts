import type {
  AuthResponse,
  Cart,
  Category,
  CheckoutResult,
  Order,
  OrderStatus,
  OrderUpdate,
  Page,
  PaymentInfo,
  Product,
  ProductForm,
} from "./types";

// One place for every call to the backend. Pages never use fetch() directly.

const TOKEN_KEY = "commercehub.token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

/** An error carrying the message the API sent, so pages can show it as is. */
export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  let response: Response;

  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "Cannot reach the server. Check that the backend is running.");
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const data = text ? safeJson(text) : null;

  if (!response.ok) {
    const message =
      data && typeof data.message === "string"
        ? data.message
        : `Request failed (${response.status}).`;
    throw new ApiError(response.status, message);
  }

  return data as T;
}

function safeJson(text: string): any {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

export interface ProductQuery {
  q?: string;
  categoryId?: number | null;
  sort?: string;
  page?: number;
  size?: number;
}

function queryString(params: Record<string, string | number | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export const api = {
  // ----- accounts -----
  register: (name: string, email: string, password: string) =>
    request<AuthResponse>("POST", "/api/auth/register", { name, email, password }),
  login: (email: string, password: string) =>
    request<AuthResponse>("POST", "/api/auth/login", { email, password }),

  // ----- shop -----
  products: (query: ProductQuery) =>
    request<Page<Product>>("GET", `/api/products${queryString({ ...query })}`),
  product: (id: number) => request<Product>("GET", `/api/products/${id}`),
  categories: () => request<Category[]>("GET", "/api/categories"),

  // ----- cart -----
  cart: () => request<Cart>("GET", "/api/cart"),
  addToCart: (productId: number, quantity: number) =>
    request<Cart>("POST", "/api/cart/items", { productId, quantity }),
  setCartQuantity: (productId: number, quantity: number) =>
    request<Cart>("PUT", `/api/cart/items/${productId}`, { quantity }),
  removeFromCart: (productId: number) => request<Cart>("DELETE", `/api/cart/items/${productId}`),

  // ----- orders -----
  checkout: (shippingAddress: string) =>
    request<CheckoutResult>("POST", "/api/checkout", { shippingAddress }),
  paymentInfo: (orderNumber: string) =>
    request<PaymentInfo>("GET", `/api/orders/${orderNumber}/payment`),
  verifyPayment: (orderNumber: string, gatewayOrderId: string, gatewayPaymentId: string, signature: string) =>
    request<{ paid: boolean }>("POST", `/api/orders/${orderNumber}/payment/verify`, {
      gatewayOrderId,
      gatewayPaymentId,
      signature,
    }),
  demoConfirmPayment: (orderNumber: string) =>
    request<{ paid: boolean }>("POST", `/api/orders/${orderNumber}/payment/demo-confirm`),
  myOrders: () => request<Order[]>("GET", "/api/orders"),
  cancelOrder: (orderNumber: string) => request<Order>("POST", `/api/orders/${orderNumber}/cancel`),

  // ----- order updates (served by the separate notification service) -----
  myOrderUpdates: () => request<OrderUpdate[]>("GET", "/notify/api/notifications"),

  // ----- admin -----
  adminProducts: (q: string, page: number) =>
    request<Page<Product>>("GET", `/api/admin/products${queryString({ q, page, size: 20 })}`),
  createProduct: (form: ProductForm) => request<Product>("POST", "/api/admin/products", form),
  updateProduct: (id: number, form: ProductForm) =>
    request<Product>("PUT", `/api/admin/products/${id}`, form),
  adjustStock: (id: number, delta: number) =>
    request<Product>("PATCH", `/api/admin/products/${id}/stock`, { delta }),
  lowStock: (threshold: number) =>
    request<Product[]>("GET", `/api/admin/products/low-stock${queryString({ threshold })}`),
  adminOrders: (page: number) =>
    request<Page<Order>>("GET", `/api/admin/orders${queryString({ page, size: 20 })}`),
  setOrderStatus: (orderNumber: string, status: OrderStatus) =>
    request<Order>("PATCH", `/api/admin/orders/${orderNumber}/status`, { status }),
};

export function formatPrice(amount: number): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(amount);
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleString("en-IN", {
    day: "numeric",
    month: "short",
    hour: "numeric",
    minute: "2-digit",
  });
}

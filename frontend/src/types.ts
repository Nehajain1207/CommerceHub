// The shapes of the JSON the API sends back. They mirror the Java "record" classes in the backend.

export type Role = "CUSTOMER" | "ADMIN";

export interface AuthResponse {
  token: string;
  name: string;
  email: string;
  role: Role;
}

export interface Category {
  id: number;
  name: string;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  price: number;
  stock: number;
  categoryId: number;
  categoryName: string;
  active: boolean;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CartLine {
  productId: number;
  name: string;
  unitPrice: number;
  quantity: number;
  stock: number;
  lineTotal: number;
}

export interface Cart {
  items: CartLine[];
  itemCount: number;
  total: number;
}

export type OrderStatus = "PLACED" | "SHIPPED" | "DELIVERED" | "CANCELLED";

export interface OrderLine {
  productId: number;
  name: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface Order {
  orderNumber: string;
  status: OrderStatus;
  total: number;
  shippingAddress: string;
  createdAt: string;
  customerName: string;
  customerEmail: string;
  items: OrderLine[];
}

export interface ProductForm {
  sku: string;
  name: string;
  description: string;
  price: number;
  stock: number;
  categoryId: number;
  active: boolean;
}

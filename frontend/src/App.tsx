import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import Header from "./components/Header";
import { useSession } from "./session";
import ShopPage from "./pages/ShopPage";
import ProductPage from "./pages/ProductPage";
import CartPage from "./pages/CartPage";
import OrdersPage from "./pages/OrdersPage";
import LoginPage from "./pages/LoginPage";
import AdminProductsPage from "./pages/AdminProductsPage";
import AdminOrdersPage from "./pages/AdminOrdersPage";

/** Sends people who are not logged in to the login page, and remembers where they were going. */
function RequireLogin({ children, adminOnly = false }: { children: ReactNode; adminOnly?: boolean }) {
  const { user } = useSession();
  const location = useLocation();

  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  if (adminOnly && user.role !== "ADMIN") {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <>
      <Header />
      <main className="page">
        <Routes>
          <Route path="/" element={<ShopPage />} />
          <Route path="/products/:id" element={<ProductPage />} />
          <Route path="/login" element={<LoginPage mode="login" />} />
          <Route path="/register" element={<LoginPage mode="register" />} />
          <Route
            path="/cart"
            element={
              <RequireLogin>
                <CartPage />
              </RequireLogin>
            }
          />
          <Route
            path="/orders"
            element={
              <RequireLogin>
                <OrdersPage />
              </RequireLogin>
            }
          />
          <Route
            path="/admin/products"
            element={
              <RequireLogin adminOnly>
                <AdminProductsPage />
              </RequireLogin>
            }
          />
          <Route
            path="/admin/orders"
            element={
              <RequireLogin adminOnly>
                <AdminOrdersPage />
              </RequireLogin>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </>
  );
}

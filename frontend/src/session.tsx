import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { api, setToken } from "./api";
import type { AuthResponse, Role } from "./types";

// Who is logged in, and how many items are in their cart.
// Kept in one place so the header and every page agree.

interface SessionUser {
  name: string;
  email: string;
  role: Role;
}

interface Session {
  user: SessionUser | null;
  cartCount: number;
  signIn: (response: AuthResponse) => void;
  signOut: () => void;
  setCartCount: (count: number) => void;
  refreshCart: () => Promise<void>;
}

const USER_KEY = "commercehub.user";
const SessionContext = createContext<Session | null>(null);

function loadUser(): SessionUser | null {
  try {
    const saved = localStorage.getItem(USER_KEY);
    return saved ? (JSON.parse(saved) as SessionUser) : null;
  } catch {
    return null;
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(loadUser);
  const [cartCount, setCartCount] = useState(0);

  const signOut = useCallback(() => {
    setToken(null);
    localStorage.removeItem(USER_KEY);
    setUser(null);
    setCartCount(0);
  }, []);

  const signIn = useCallback((response: AuthResponse) => {
    const next = { name: response.name, email: response.email, role: response.role };
    setToken(response.token);
    localStorage.setItem(USER_KEY, JSON.stringify(next));
    setUser(next);
  }, []);

  const refreshCart = useCallback(async () => {
    try {
      const cart = await api.cart();
      setCartCount(cart.itemCount);
    } catch (error) {
      // An expired login shows up here first: go back to "logged out"
      if (error instanceof Error && "status" in error && (error as { status: number }).status === 401) {
        signOut();
      }
    }
  }, [signOut]);

  useEffect(() => {
    if (user) {
      void refreshCart();
    }
  }, [user, refreshCart]);

  const value = useMemo(
    () => ({ user, cartCount, signIn, signOut, setCartCount, refreshCart }),
    [user, cartCount, signIn, signOut, refreshCart],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) {
    throw new Error("useSession must be used inside SessionProvider");
  }
  return session;
}

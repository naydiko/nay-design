import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { AuthApi } from "../api/endpoints";
import { clearToken, getStoredUser, getToken, setStoredUser, setToken } from "../api/client";
import type { LoginRequest, RegisterRequest, UserResponse } from "../api/types";

interface AuthContextValue {
  user: UserResponse | null;
  loading: boolean;
  login: (body: LoginRequest) => Promise<void>;
  register: (body: RegisterRequest) => Promise<void>;
  loginWithGoogle: (idToken: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(() => getStoredUser<UserResponse>());
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Validate any stored token against the backend on first load.
    const token = getToken();
    if (!token) {
      setLoading(false);
      return;
    }
    AuthApi.me()
      .then((me) => {
        setUser(me);
        setStoredUser(me);
      })
      .catch(() => {
        clearToken();
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      login: async (body) => {
        const res = await AuthApi.login(body);
        setToken(res.token);
        setStoredUser(res.user);
        setUser(res.user);
      },
      register: async (body) => {
        const res = await AuthApi.register(body);
        setToken(res.token);
        setStoredUser(res.user);
        setUser(res.user);
      },
      loginWithGoogle: async (idToken) => {
        const res = await AuthApi.loginWithGoogle({ idToken });
        setToken(res.token);
        setStoredUser(res.user);
        setUser(res.user);
      },
      logout: () => {
        clearToken();
        setUser(null);
      },
    }),
    [user, loading]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}


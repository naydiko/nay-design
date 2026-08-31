import { type FormEvent, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import GoogleSignInButton from "../auth/GoogleSignInButton";

export default function LoginPage() {
  const { login, loginWithGoogle } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login({ email, password });
      navigate("/projects", { replace: true });
    } catch (err) {
      setError((err as { message?: string }).message ?? "Login failed");
    } finally {
      setBusy(false);
    }
  }

  async function onGoogleCredential(idToken: string) {
    setError(null);
    try {
      await loginWithGoogle(idToken);
      navigate("/projects", { replace: true });
    } catch (err) {
      setError((err as { message?: string }).message ?? "Google sign-in failed");
    }
  }

  return (
    <div className="page-center">
      <form className="card" onSubmit={onSubmit}>
        <h1>NayDesign</h1>
        <p className="muted">Sign in to continue</p>
        <label>
          Email
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoFocus
          />
        </label>
        <label>
          Password
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {error && <div className="error">{error}</div>}
        <button type="submit" disabled={busy}>
          {busy ? "Signing in…" : "Log in"}
        </button>
        <p className="muted">
          <Link to="/forgot-password">Forgot password?</Link>
        </p>
        <GoogleSignInButton onCredential={onGoogleCredential} />
        <p className="muted">
          No account? <Link to="/register">Register</Link>
        </p>
      </form>
    </div>
  );
}


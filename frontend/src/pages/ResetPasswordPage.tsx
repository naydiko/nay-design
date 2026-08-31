import { type FormEvent, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { AuthApi } from "../api/endpoints";

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get("token") ?? "";
  const [newPassword, setNewPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await AuthApi.resetPassword({ token, newPassword });
      setDone(true);
      setTimeout(() => navigate("/login", { replace: true }), 1500);
    } catch (err) {
      setError((err as { message?: string }).message ?? "Failed to reset password");
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <div className="page-center">
        <div className="card">
          <h1>Reset password</h1>
          <div className="error">This reset link is missing its token.</div>
          <p className="muted">
            <Link to="/forgot-password">Request a new link</Link>
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="page-center">
      <form className="card" onSubmit={onSubmit}>
        <h1>Reset password</h1>
        {done ? (
          <p>Password has been reset. Redirecting to login…</p>
        ) : (
          <>
            <label>
              New password
              <input
                type="password"
                required
                minLength={8}
                autoFocus
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
              />
            </label>
            {error && <div className="error">{error}</div>}
            <button type="submit" disabled={busy}>
              {busy ? "Resetting…" : "Reset password"}
            </button>
          </>
        )}
        <p className="muted">
          <Link to="/login">Back to login</Link>
        </p>
      </form>
    </div>
  );
}


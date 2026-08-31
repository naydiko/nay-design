import { type FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { AuthApi } from "../api/endpoints";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      // Backend intentionally returns the same generic message whether or
      // not the email belongs to an account — never surface anything else.
      await AuthApi.forgotPassword({ email });
      setSubmitted(true);
    } catch {
      setError("Something went wrong. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page-center">
      <form className="card" onSubmit={onSubmit}>
        <h1>Forgot password</h1>
        {submitted ? (
          <p>If an account exists for that email, a password reset link has been sent.</p>
        ) : (
          <>
            <p className="muted">Enter your email and we'll send you a reset link.</p>
            <label>
              Email
              <input
                type="email"
                required
                autoFocus
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </label>
            {error && <div className="error">{error}</div>}
            <button type="submit" disabled={busy}>
              {busy ? "Sending…" : "Send reset link"}
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


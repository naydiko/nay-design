import { type FormEvent, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    email: "",
    displayName: "",
    firstName: "",
    lastName: "",
    phoneNumber: "",
    password: "",
  });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  function update<K extends keyof typeof form>(key: K, value: string) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await register(form);
      navigate("/projects", { replace: true });
    } catch (err) {
      setError((err as { message?: string }).message ?? "Registration failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page-center">
      <form className="card" onSubmit={onSubmit}>
        <h1>Create account</h1>
        <label>
          Email
          <input
            type="email"
            required
            value={form.email}
            onChange={(e) => update("email", e.target.value)}
          />
        </label>
        <label>
          Display name
          <input
            required
            value={form.displayName}
            onChange={(e) => update("displayName", e.target.value)}
          />
        </label>
        <label>
          First name
          <input value={form.firstName} onChange={(e) => update("firstName", e.target.value)} />
        </label>
        <label>
          Last name
          <input value={form.lastName} onChange={(e) => update("lastName", e.target.value)} />
        </label>
        <label>
          Phone number
          <input
            value={form.phoneNumber}
            onChange={(e) => update("phoneNumber", e.target.value)}
          />
        </label>
        <label>
          Password
          <input
            type="password"
            required
            minLength={8}
            value={form.password}
            onChange={(e) => update("password", e.target.value)}
          />
        </label>
        {error && <div className="error">{error}</div>}
        <button type="submit" disabled={busy}>
          {busy ? "Creating…" : "Register"}
        </button>
        <p className="muted">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </div>
  );
}


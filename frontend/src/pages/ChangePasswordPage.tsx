import { type FormEvent, useState } from "react";
import { AuthApi } from "../api/endpoints";

export default function ChangePasswordPage() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(false);
    setBusy(true);
    try {
      await AuthApi.changePassword({ currentPassword, newPassword });
      setSuccess(true);
      setCurrentPassword("");
      setNewPassword("");
    } catch (err) {
      setError((err as { message?: string }).message ?? "Failed to change password");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h1>Change password</h1>
      <form className="card" onSubmit={onSubmit} style={{ maxWidth: 420 }}>
        <label>
          Current password
          <input
            type="password"
            required
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
          />
        </label>
        <label>
          New password
          <input
            type="password"
            required
            minLength={8}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
        </label>
        {error && <div className="error">{error}</div>}
        {success && <div className="success">Password changed successfully.</div>}
        <button type="submit" disabled={busy}>
          {busy ? "Saving…" : "Change password"}
        </button>
      </form>
    </div>
  );
}


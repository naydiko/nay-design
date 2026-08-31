import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { AuthApi } from "../api/endpoints";

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const [status, setStatus] = useState<"pending" | "success" | "error">("pending");
  const [message, setMessage] = useState("Verifying your email…");

  useEffect(() => {
    if (!token) {
      setStatus("error");
      setMessage("This verification link is missing its token.");
      return;
    }
    AuthApi.verifyEmail(token)
      .then((res) => {
        setStatus("success");
        setMessage(res.message);
      })
      .catch((err) => {
        setStatus("error");
        setMessage((err as { message?: string }).message ?? "Failed to verify email");
      });
  }, [token]);

  return (
    <div className="page-center">
      <div className="card">
        <h1>Email verification</h1>
        <p className={status === "error" ? "error" : undefined}>{message}</p>
        <p className="muted">
          <Link to="/login">Back to login</Link>
        </p>
      </div>
    </div>
  );
}


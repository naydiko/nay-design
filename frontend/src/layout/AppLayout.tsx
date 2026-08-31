import { Link, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export default function AppLayout() {
  const { user, logout } = useAuth();
  return (
    <div className="app-shell">
      <header className="app-header">
        <Link to="/projects" className="brand">
          NayDesign
        </Link>
        <div className="spacer" />
        {user && (
          <>
            <span className="muted">{user.displayName}</span>
            <button className="link-button" onClick={logout}>
              Log out
            </button>
          </>
        )}
      </header>
      <main className="app-content">
        <Outlet />
      </main>
    </div>
  );
}


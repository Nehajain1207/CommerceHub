import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useSession } from "../session";
import Notice from "../components/Notice";

/** One page for both logging in and creating an account; "mode" decides which. */
export default function LoginPage({ mode }: { mode: "login" | "register" }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { signIn } = useSession();
  const from = (location.state as { from?: string } | null)?.from ?? "/";

  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const isRegister = mode === "register";

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");

    try {
      const response = isRegister
        ? await api.register(name, email, password)
        : await api.login(email, password);
      signIn(response);
      navigate(from, { replace: true });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="narrow">
      <h1>{isRegister ? "Create account" : "Log in"}</h1>

      <form className="form" onSubmit={submit}>
        {isRegister && (
          <label>
            Name
            <input required value={name} onChange={(event) => setName(event.target.value)} autoComplete="name" />
          </label>
        )}
        <label>
          Email
          <input
            required
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
          />
        </label>
        <label>
          Password
          <input
            required
            type="password"
            minLength={isRegister ? 6 : undefined}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete={isRegister ? "new-password" : "current-password"}
          />
          {isRegister && <span className="hint">At least 6 characters</span>}
        </label>

        {error && <Notice kind="error">{error}</Notice>}

        <button type="submit" className="button button-wide" disabled={busy}>
          {busy ? "Please wait…" : isRegister ? "Create account" : "Log in"}
        </button>
      </form>

      <p className="switch">
        {isRegister ? (
          <>
            Already have an account? <Link to="/login" state={{ from }}>Log in</Link>
          </>
        ) : (
          <>
            New here? <Link to="/register" state={{ from }}>Create account</Link>
          </>
        )}
      </p>
    </div>
  );
}

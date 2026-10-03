import { Link, NavLink, useNavigate } from "react-router-dom";
import { useSession } from "../session";

export default function Header() {
  const { user, cartCount, signOut } = useSession();
  const navigate = useNavigate();

  function handleSignOut() {
    signOut();
    navigate("/");
  }

  return (
    <header className="header">
      <div className="header-inner">
        <Link to="/" className="brand">
          CommerceHub
        </Link>

        <nav className="nav" aria-label="Main">
          <NavLink to="/" end>
            Shop
          </NavLink>
          {user && <NavLink to="/orders">My orders</NavLink>}
          {user?.role === "ADMIN" && (
            <>
              <NavLink to="/admin/products">Manage products</NavLink>
              <NavLink to="/admin/orders">Manage orders</NavLink>
            </>
          )}
        </nav>

        <div className="header-actions">
          {user ? (
            <>
              <Link to="/cart" className="cart-link">
                Cart
                <span className="cart-count" aria-label={`${cartCount} items in cart`}>
                  {cartCount}
                </span>
              </Link>
              <span className="who">{user.name}</span>
              <button type="button" className="link-button" onClick={handleSignOut}>
                Log out
              </button>
            </>
          ) : (
            <>
              <Link to="/login">Log in</Link>
              <Link to="/register" className="button button-small">
                Create account
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}

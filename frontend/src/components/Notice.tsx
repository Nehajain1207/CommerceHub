/** A one-line message: an error to fix, or confirmation that something worked. */
export default function Notice({ kind, children }: { kind: "error" | "success"; children: string }) {
  return (
    <p className={`notice notice-${kind}`} role={kind === "error" ? "alert" : "status"}>
      {children}
    </p>
  );
}

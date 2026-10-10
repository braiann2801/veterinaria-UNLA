"use client"

import { LogIn, LogOut } from "lucide-react"

/**
 * Columna de una lista del tablero del Mostrador.
 *
 * <p>Es presentacional pura: no pide datos ni guarda estado, solo dibuja los
 * items que le pasa su contenedor. Existe porque las tres columnas del tablero
 * (Turnos, Ingresan, Se retiran) comparten exactamente la misma estructura, y
 * duplicarla habría dejado tres lugares donde arreglar el mismo borde.</p>
 *
 * <p>Los datos ya vienen resueltos por el contenedor. Ese reparto es el que
 * permite que `Mostrador` siga siendo el único que conoce la fecha y dispara
 * las recargas después de un alta.</p>
 */
export function ColumnaLista({
  title,
  items,
  icon,
  nota,
}: {
  title: string
  items: { id: string; top: string; sub: string }[]
  icon: "turno" | "in" | "out"
  nota?: string
}) {
  return (
    <div className="rounded-lg border border-border">
      <div className="flex items-center gap-2 border-b border-border bg-muted/40 px-3 py-2 text-xs font-medium">
        {icon === "in" && <LogIn className="h-3.5 w-3.5" />}
        {icon === "out" && <LogOut className="h-3.5 w-3.5" />}
        {title} <span className="ml-auto text-muted-foreground">{items.length}</span>
      </div>
      <ul className="divide-y divide-border">
        {items.length === 0 ? (
          <li className="px-3 py-3 text-sm text-muted-foreground">Sin registros.</li>
        ) : (
          items.map((it) => (
            <li key={it.id} className="px-3 py-2">
              <p className="text-sm font-medium">{it.top}</p>
              <p className="text-xs text-muted-foreground">{it.sub}</p>
            </li>
          ))
        )}
      </ul>
      {nota && <p className="border-t border-border px-3 py-1.5 text-xs text-muted-foreground">{nota}</p>}
    </div>
  )
}

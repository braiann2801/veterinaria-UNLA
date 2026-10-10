"use client"

import { Panel } from "@/components/ui/field"
import type { AforoGuarderia } from "@/lib/api"
import type { EstadoApi } from "@/lib/use-api"
import { Recurso } from "@/lib/vista"

/**
 * Widget de ocupación de guardería del día.
 *
 * <p>La barra se pinta según `datos.completo`, que ya calcula el backend: no se
 * reimplementa el tope de 10 acá (regla 2.1). El ancho se clampa a 100% por si
 * el cupo llegara a superarse por un error de datos, para no romper el layout.</p>
 */
export function WidgetAforo({ aforo, fecha }: { aforo: EstadoApi<AforoGuarderia>; fecha: string }) {
  return (
    <Panel title="Ocupación guardería" className="lg:col-span-1">
      <Recurso
        estado={aforo}
        textoCarga="Consultando el aforo"
        onReintentar={aforo.recargar}
      >
        {(datos) => (
          <>
            <p className="text-3xl font-bold">
              {datos.cuposOcupados}{" "}
              <span className="text-base font-normal text-muted-foreground">
                de {datos.aforoMaximo} ocupados
              </span>
            </p>
            <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-muted">
              <div
                className={`h-full rounded-full transition-all ${datos.completo ? "bg-destructive" : "bg-primary"}`}
                style={{
                  width: `${Math.min(100, (datos.cuposOcupados / datos.aforoMaximo) * 100)}%`,
                }}
              />
            </div>
            <p className="mt-2 text-xs text-muted-foreground">
              {datos.cuposDisponibles} lugares disponibles el {fecha}
            </p>
          </>
        )}
      </Recurso>
    </Panel>
  )
}

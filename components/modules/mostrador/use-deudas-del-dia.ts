"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"

import { tutores as apiTutores } from "@/lib/api"
import type { DeudaTutor, MascotaDelDia } from "@/lib/api"

/**
 * Deuda consolidada de los tutores que estuvieron en la jornada.
 *
 * <p><b>Por qué es N+1.</b> No existe consulta masiva de deuda: lo único que hay
 * es `GET /api/v1/tutores/{id}/deuda`, una por tutor. Se deduplica por
 * `tutorId` y se piden en paralelo con `Promise.allSettled`, así que el costo es
 * una request por tutor presente y todas vuelan a la vez.</p>
 *
 * <p><b>Por qué `allSettled` y no `all`.</b> Con `all`, un tutor al que se le cae
 * la red tumba el aviso de morosidad de los demás. Con `allSettled` se informa cuántos
 * quedaron sin consultar y el operador sabe que le puede faltar un aviso; lo
 * contrario sería mostrar "nadie debe nada" cuando en realidad no se consultó.</p>
 *
 * <p><b>Por qué un hook y no un `useApi`.</b> `useApi` resuelve el caso de "una
 * llamada a un recurso". Esto es "N llamadas derivadas de los datos de otra
 * llamada", que ya es un segundo nivel. Encerrarlo aparte deja explícito que es un
 * costo conocido y no un descuido.</p>
 */
export function useDeudasDelDia(jornada: MascotaDelDia[] | null, fecha: string) {
  const [estado, setEstado] = useState<{
    clave: string
    porTutor: Record<number, DeudaTutor>
    fallidos: number
    cargando: boolean
  }>({ clave: "", porTutor: {}, fallidos: 0, cargando: false })

  const [tique, setTique] = useState(0)

  const ids = useMemo(() => {
    const unicos = new Set<number>()
    for (const mascota of jornada ?? []) {
      for (const tutor of mascota.tutores) unicos.add(tutor.tutorId)
    }
    return [...unicos].sort((a, b) => a - b)
  }, [jornada])

  /**
   * Los ids se leen de un ref y no se ponen en las dependencias.
   *
   * <p>El arreglo cambia de largo según quantos tutores vinieron, y React exige
   * que el arreglo de dependencias mantenga su tamaño entre renders. Encerrarlo en
   * una clave de texto y guardar los ids aparte cumple las dos cosas: la clave
   * dispara el efecto, el ref aporta los valores.</p>
   */
  const idsRef = useRef(ids)
  idsRef.current = ids
  const clave = `${fecha}:${ids.join(",")}`

  useEffect(() => {
    const aConsultar = idsRef.current
    let vigente = true

    if (aConsultar.length === 0) {
      setEstado({ clave, porTutor: {}, fallidos: 0, cargando: false })
      return
    }

    setEstado({ clave, porTutor: {}, fallidos: 0, cargando: true })
    Promise.allSettled(aConsultar.map((id) => apiTutores.deuda(id))).then((resultados) => {
      if (!vigente) return
      const porTutor: Record<number, DeudaTutor> = {}
      let fallidos = 0
      resultados.forEach((resultado, i) => {
        if (resultado.status === "fulfilled") porTutor[aConsultar[i]] = resultado.value
        else fallidos += 1
      })
      setEstado({ clave, porTutor, fallidos, cargando: false })
    })

    return () => {
      vigente = false
    }
  }, [clave, tique])

  const recargar = useCallback(() => setTique((t) => t + 1), [])

  // Se expone el `cargando` aunque la vista todavia no lo pinte: el dato existe,
  // y omitirlo obligaria al proximo consumidor a reinventarlo.
  return { ...estado, recargar }
}

/** Forma del resultado de {@link useDeudasDelDia}, para tipar props. */
export type DeudasDelDia = ReturnType<typeof useDeudasDelDia>

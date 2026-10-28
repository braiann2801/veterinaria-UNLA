/**
 * Datos del negocio que muestra la landing pública (`/`) y el login.
 *
 * Es el único archivo que hay que editar para personalizar el sitio: el diseño
 * de la landing lee todo de acá. Los valores actuales son DE EJEMPLO.
 */

export const NEGOCIO = {
  nombre: "Veterinaria y Guardería Canina",
  eslogan: "Atención clínica y guardería para tu perro, en un solo lugar.",
  descripcion:
    "Consultas veterinarias con turnos organizados y una guardería con cupos limitados, para que cada perro tenga la atención que necesita.",

  servicios: [
    {
      titulo: "Consultas clínicas",
      detalle: "Turnos de 45 minutos con desinfección entre pacientes. Historia clínica de cada mascota.",
      icono: "stethoscope",
    },
    {
      titulo: "Guardería canina",
      detalle: "Hasta 10 perros por día, para que cada uno reciba atención. Reservá tu lugar con anticipación.",
      icono: "dog",
    },
    {
      titulo: "Retiro seguro",
      detalle: "Solo los tutores autorizados pueden retirar a cada mascota.",
      icono: "shield",
    },
  ],

  horarios: [
    { dias: "Lunes a viernes", horas: "9:00 a 19:00" },
    { dias: "Sábados", horas: "9:00 a 13:00" },
    { dias: "Domingos y feriados", horas: "Cerrado" },
  ],

  contacto: {
    telefono: "+54 11 0000-0000",
    /** Solo dígitos con código de país, sin "+": se usa para armar el link wa.me. */
    whatsapp: "5491100000000",
    email: "contacto@ejemplo.com",
    direccion: "Calle Ejemplo 123, Lanús, Buenos Aires",
    /** Link de Google Maps; vacío para no mostrar el botón "Cómo llegar". */
    mapa: "https://maps.google.com/?q=Calle+Ejemplo+123+Lanus",
  },
} as const

export type IconoServicio = (typeof NEGOCIO.servicios)[number]["icono"]

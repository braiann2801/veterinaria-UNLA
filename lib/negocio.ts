/**
 * Datos del negocio que muestra la landing pública (`/`) y el login.
 *
 * Es el único archivo que hay que editar para personalizar el sitio: el diseño
 * de la landing lee todo de acá. Los valores actuales son DE EJEMPLO.
 */

/** Íconos de lucide que la landing sabe resolver (ver `app/page.tsx`). */
export type IconoServicio = "stethoscope" | "dog" | "shield"
export type IconoMotivo = "stethoscope" | "dog" | "clipboard" | "shield" | "calendar" | "map"

/** Fondo pastel de los círculos de la sección "Nuestros estándares". */
export type ColorEstandar = "blush" | "lilac" | "mint"

export const NEGOCIO = {
  nombre: "Veterinaria y Guardería Canina",
  /** Versión corta para el logo y el pie de página. */
  nombreCorto: "Veterinaria UNLA",
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
  ] satisfies readonly { titulo: string; detalle: string; icono: IconoServicio }[],

  estandares: [
    {
      titulo: "Guardería con cupos limitados",
      detalle:
        "Recibimos hasta 10 perros por día para que cada uno tenga espacio para correr, jugar y descansar acompañado, sin hacinamiento.",
      imagen: "/landing/play.jpg",
      alt: "Cachorro de pelo rizado con anteojos de sol jugando en la guardería",
      color: "blush",
    },
    {
      titulo: "Turnos clínicos ordenados",
      detalle:
        "Cada consulta dura 45 minutos y entre paciente y paciente desinfectamos la sala durante 15 minutos antes de recibir al siguiente.",
      imagen: "/landing/clean.jpg",
      alt: "Perro descansando en un consultorio limpio",
      color: "lilac",
    },
    {
      titulo: "Salidas solo con autorización",
      detalle:
        "Cada mascota tiene registrados a todos sus tutores y solo quien está autorizado puede retirarla del establecimiento.",
      imagen: "/landing/rest.jpg",
      alt: "Perro tranquilo esperando a su tutor",
      color: "mint",
    },
  ] satisfies readonly {
    titulo: string
    detalle: string
    imagen: string
    alt: string
    color: ColorEstandar
  }[],

  motivos: [
    {
      titulo: "Atención veterinaria",
      detalle: "Profesionales atentos con cada paciente.",
      icono: "stethoscope",
    },
    {
      titulo: "Guardería con cupos",
      detalle: "Máximo 10 perros por día, sin excepciones.",
      icono: "dog",
    },
    {
      titulo: "Ficha por mascota",
      detalle: "Anamnesis, diagnóstico y tratamiento a mano.",
      icono: "clipboard",
    },
    {
      titulo: "Retiro autorizado",
      detalle: "Solo los tutores acreditados se llevan a su perro.",
      icono: "shield",
    },
    {
      titulo: "Turnos puntuales",
      detalle: "45 minutos de consulta y 15 de desinfección.",
      icono: "calendar",
    },
    {
      titulo: "Fácil de llegar",
      detalle: "Ubicación accesible y estacionamiento cercano.",
      icono: "map",
    },
  ] satisfies readonly { titulo: string; detalle: string; icono: IconoMotivo }[],

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

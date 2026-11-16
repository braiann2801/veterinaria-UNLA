import { Analytics } from '@vercel/analytics/next'
import { Fredoka, Poppins } from 'next/font/google'
import type { Metadata, Viewport } from 'next'
import './globals.css'

/* Tipografías del producto: Poppins para el texto y Fredoka para los títulos.
   Se cargan como variables CSS y las aplica `app/globals.css`, así la landing y
   el sistema interno comparten la misma identidad. */
const fredoka = Fredoka({
  subsets: ['latin'],
  weight: ['500', '600', '700'],
  variable: '--font-fredoka',
  display: 'swap',
})

const poppins = Poppins({
  subsets: ['latin'],
  weight: ['400', '500', '600'],
  variable: '--font-poppins',
  display: 'swap',
})

export const metadata: Metadata = {
  title: 'Veterinaria · Guardería canina',
  description: 'Sistema interno de gestión para veterinaria y guardería canina: mostrador, guardería, consultas, cobros, balance y fichas médicas.',
  generator: 'v0.app',
  icons: {
    icon: [
      {
        url: '/icon-light-32x32.png',
        media: '(prefers-color-scheme: light)',
      },
      {
        url: '/icon-dark-32x32.png',
        media: '(prefers-color-scheme: dark)',
      },
      {
        url: '/icon.svg',
        type: 'image/svg+xml',
      },
    ],
    apple: '/apple-icon.png',
  },
}

export const viewport: Viewport = {
  colorScheme: 'light',
  themeColor: '#faf7f1',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html
      lang="es"
      className={`${fredoka.variable} ${poppins.variable}`}
      suppressHydrationWarning
    >
      <body className="antialiased">
        {children}
        {process.env.NODE_ENV === 'production' && <Analytics />}
      </body>
    </html>
  )
}

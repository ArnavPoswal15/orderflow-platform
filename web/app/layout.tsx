import type { Metadata } from "next";
import { Geist } from "next/font/google";
import { Suspense } from "react";
import { SiteHeader } from "@/components/SiteHeader";
import { CartProvider } from "@/lib/cart";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: {
    default: "Corner Market",
    template: "%s · Corner Market",
  },
  description: "Grocery orders with a live status timeline.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${geistSans.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col bg-slate-50 text-zinc-900">
        <CartProvider>
          <Suspense fallback={<div className="h-14 border-b border-zinc-200 bg-white" />}>
            <SiteHeader />
          </Suspense>
          <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">{children}</main>
        </CartProvider>
      </body>
    </html>
  );
}

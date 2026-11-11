import type { Metadata } from "next";
import localFont from "next/font/local";
import { AppShell } from "@/components/AppShell";
import { getServerPublicConfig, serializePublicConfigForInlineScript } from "@/lib/runtime-config";
import { Providers } from "./providers";
import "./globals.css";

export const dynamic = "force-dynamic";

const geistSans = localFont({
  src: "./fonts/GeistVF.woff",
  variable: "--font-geist-sans",
  weight: "100 900",
});
const geistMono = localFont({
  src: "./fonts/GeistMonoVF.woff",
  variable: "--font-geist-mono",
  weight: "100 900",
});

export const metadata: Metadata = {
  title: "Payagent — Bilateral deal room",
  description:
    "A bounded buyer and seller workspace for intake, mandates, negotiation, agreement, sandbox settlement, and evidence review.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const publicConfig = getServerPublicConfig();

  return (
    <html lang="en">
      <body className={`${geistSans.variable} ${geistMono.variable} antialiased`}>
        <script
          id="payagent-public-config"
          dangerouslySetInnerHTML={{
            __html: `window.__PAYAGENT_CONFIG__=${serializePublicConfigForInlineScript(publicConfig)};`,
          }}
        />
        <Providers>
          <AppShell>{children}</AppShell>
        </Providers>
      </body>
    </html>
  );
}

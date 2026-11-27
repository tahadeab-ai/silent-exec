/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"IBM Plex Sans Arabic"', '"Cairo"', "system-ui", "sans-serif"],
        mono: ['"JetBrains Mono"', '"Cascadia Code"', "ui-monospace", "monospace"],
      },
      colors: {
        canvas: "#0B0F17",
        card: "#131A26",
        borderSubtle: "#1D2738",
        accent: "#38BDF8",
      },
    },
  },
  plugins: [],
};

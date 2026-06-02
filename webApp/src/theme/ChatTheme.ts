export interface ChatTheme {
    name: string;
    bgGradient: string;
    myBubble: string;
    otherBubble: string;
}

export const ChatThemeManager = {
    themes: [
        {
            name: "Default",
            bgGradient: "var(--bg-dark)", 
            myBubble: "var(--accent-orange)",
            otherBubble: "var(--surface-dark)"
        },
        {
            name: "Midnight",
            bgGradient: "linear-gradient(180deg, #0F0C29, #302B63, #24243E)",
            myBubble: "#8A2387",
            otherBubble: "rgba(48, 43, 99, 0.6)"
        },
        {
            name: "Ocean",
            bgGradient: "linear-gradient(180deg, #000428, #004e92)",
            myBubble: "#00B4DB",
            otherBubble: "rgba(0, 78, 146, 0.6)"
        },
        {
            name: "Sunset",
            bgGradient: "linear-gradient(180deg, #23074d, #cc5333)",
            myBubble: "#FF416C",
            otherBubble: "rgba(74, 21, 75, 0.6)"
        },
        {
            name: "Forest",
            bgGradient: "linear-gradient(180deg, #0f2027, #203a43, #2c5364)",
            myBubble: "#11998e",
            otherBubble: "rgba(32, 58, 67, 0.6)"
        },
        {
            name: "Cyberpunk",
            bgGradient: "linear-gradient(180deg, #120E1F, #25082E, #1A0B2E)",
            myBubble: "#00F0FF",
            otherBubble: "rgba(208, 0, 255, 0.4)"
        },
        {
            name: "Matcha",
            bgGradient: "linear-gradient(180deg, #1E2A24, #2B3A32)",
            myBubble: "#86A873",
            otherBubble: "rgba(43, 58, 50, 0.8)"
        },
        {
            name: "Lavender",
            bgGradient: "linear-gradient(180deg, #2A233C, #403058)",
            myBubble: "#B088F9",
            otherBubble: "rgba(64, 48, 88, 0.8)"
        },
        {
            name: "Coffee",
            bgGradient: "linear-gradient(180deg, #2C1E16, #4A3022)",
            myBubble: "#C49A76",
            otherBubble: "rgba(74, 48, 34, 0.8)"
        },
        {
            name: "Ruby",
            bgGradient: "linear-gradient(180deg, #2D0A0E, #5E131E)",
            myBubble: "#E23E57",
            otherBubble: "rgba(94, 19, 30, 0.8)"
        },
        {
            name: "Abyss",
            bgGradient: "linear-gradient(180deg, #050505, #121417)",
            myBubble: "#3B82F6",
            otherBubble: "rgba(31, 41, 55, 0.8)"
        },
        {
            name: "Cherry Blossom",
            bgGradient: "linear-gradient(180deg, #331922, #572A3C)",
            myBubble: "#FFA6C9",
            otherBubble: "rgba(87, 42, 60, 0.8)"
        },
        {
            name: "Neon Mint",
            bgGradient: "linear-gradient(180deg, #0D211C, #12352B)",
            myBubble: "#00E676",
            otherBubble: "rgba(18, 53, 43, 0.8)"
        },
        {
            name: "Volcano",
            bgGradient: "linear-gradient(180deg, #290A0A, #3D1C1C)",
            myBubble: "#FF5722",
            otherBubble: "rgba(61, 28, 28, 0.8)"
        },
        {
            name: "Royal Gold",
            bgGradient: "linear-gradient(180deg, #1A1A1A, #2A2A2A)",
            myBubble: "#FFD700",
            otherBubble: "rgba(51, 51, 51, 0.8)"
        }
    ]
};
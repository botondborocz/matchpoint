import { Player } from '../components/Screens/MatchScreen';
import { SERVER_IP } from "../constants";

export interface UserProfile {
    imageUrl: string;
    id: string;
    name: string;
    email: string;
    elo: number;
    winRate: string;
    preferredLanguage: string;
    blade? : string;
    rubberFh? : string;
    rubberBh? : string;
}

// Module-level variable acts as our in-memory cache
let cachedProfile: UserProfile | null = null;

export const fetchUserProfile = async (forceRefresh: boolean = false): Promise<UserProfile> => {
    // 1. Return cached profile instantly if we have it (Tab Switch!)
    if (!forceRefresh && cachedProfile !== null) {
        return cachedProfile;
    }

    // 2. Otherwise, fetch from the Ktor backend
    const token = localStorage.getItem("auth_token");
    if (!token) throw new Error("No auth token found");

    // Make sure to replace this with your actual VM IP or domain
    const response = await fetch(`${SERVER_IP}/api/users/me`, {
        method: "GET",
        headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
        }
    });

    if (!response.ok) {
        throw new Error(`Failed to fetch profile: ${response.statusText}`);
    }

    const data: UserProfile = await response.json();

    // 3. Save to cache
    cachedProfile = data;

    return data;
};

export const clearProfileCache = () => {
    cachedProfile = null;
};

export const fetchNearbyPlayers = async (): Promise<Player[]> => {
    const token = localStorage.getItem("auth_token");
    if (!token) throw new Error("No auth token found.");

    // Update with your actual backend IP/Domain
    const response = await fetch(`${SERVER_IP}/api/users/nearby`, {
        method: "GET",
        headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
        }
    });

    if (!response.ok) {
        throw new Error(`Failed to fetch matches: ${response.statusText}`);
    }

    const data = await response.json();

    // Map the backend PlayerResponse to your frontend Player interface
    return data.map((backendPlayer: any) => ({
        id: backendPlayer.id,
        username: backendPlayer.username,
        skillLevel: backendPlayer.skillLevel,
        // ⚠️ Fallbacks: Your current Ktor endpoint doesn't return these 3 fields yet!
        // You'll want to add them to your Ktor 'PlayerResponse' data class later.
        age: backendPlayer.age || 25,
        elo: backendPlayer.elo || 1200,
        distanceKm: 0, // Default to 0 since we didn't send GPS coordinates
        imageUrl: backendPlayer.imageUrl
    }));
};

export const submitSwipe = async (targetPlayerId: string, isLiked: boolean): Promise<{ isMatch: boolean }> => {
    const token = localStorage.getItem('auth_token');
    
    const response = await fetch(`${SERVER_IP}/api/users/${targetPlayerId}/swipe`, { // Adjust URL to match your routing
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ isLiked })
    });

    if (!response.ok) {
        throw new Error('Failed to submit swipe');
    }

    return response.json();
};
import { SERVER_IP } from "../constants";

export interface ChatThreadDto {
  id: string;
  otherUserName: string;
  otherUserImageUrl: string;
  lastMessage: string;
  timestamp: string;
  unreadCount: number;
  isOnline: boolean;
  theme?: string; // 👈 NEW: Optional theme field for each chat thread
}

// 👇 NEW: Define what a reaction looks like
export interface ReactionDto {
  userId: string;
  emoji: string;
}

// 👇 UPDATED: Added replies and reactions!
export interface MessageDto {
  id: string;
  senderId: string;
  content: string;
  createdAt: string;
  replyToMessageId?: string; 
  reactions?: ReactionDto[];
  mediaUrls?: string[];
}

export const chatService = {
  /**
   * Fetches the user's active chat connections from the server.
   */
  getConnections: async (): Promise<ChatThreadDto[]> => {
    // 1. Grab the JWT token you saved during login
    const token = localStorage.getItem('auth_token'); 
    
    if (!token) {
      throw new Error("No authentication token found.");
    }

    // 2. Make the request to your Ktor route
    const response = await fetch(`${SERVER_IP}/api/connections`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });

    // 3. Handle any HTTP errors (401, 500, etc.)
    if (!response.ok) {
      throw new Error(`Failed to load connections: ${response.statusText}`);
    }

    // 4. Return the parsed JSON array
    return response.json();
  },

  // 1. Fetch History via REST
  getMessageHistory: async (connectionId: string): Promise<MessageDto[]> => {
    const token = localStorage.getItem('auth_token');
    if (!token) throw new Error("No token");

    const response = await fetch(`${SERVER_IP}/api/connections/${connectionId}/messages`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (!response.ok) throw new Error("Failed to fetch history");
    return response.json();
  },

  // 2. Mark as Read
  markMessagesAsRead: async (connectionId: string): Promise<void> => {
    const token = localStorage.getItem('auth_token');
    if (!token) return;

    await fetch(`${SERVER_IP}/api/connections/${connectionId}/messages/read`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    });
  },

  // 3. Save FCM Token (If you implement web push notifications later)
  savePushToken: async (fcmToken: string): Promise<void> => {
    const token = localStorage.getItem('auth_token');
    if (!token) return;

    await fetch(`${SERVER_IP}/api/users/fcm-token`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ token: fcmToken })
    });
  },

  // 👇 2. ADD THIS NEW FUNCTION TO UPLOAD MEDIA
  uploadMedia: async (connectionId: string, files: File[]): Promise<string[]> => {
    const token = localStorage.getItem('auth_token');
    if (!token) throw new Error("No token");

    const formData = new FormData();
    // Your Ktor backend explicitly looks for "media_0", "media_1", etc.
    files.forEach((file, index) => {
      formData.append(`media_${index}`, file);
    });

    const response = await fetch(`${SERVER_IP}/api/connections/${connectionId}/images`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`
        // DO NOT SET 'Content-Type'. The browser sets it automatically with the correct boundary!
      },
      body: formData
    });

    if (!response.ok) throw new Error("Failed to upload media");

    const json = await response.json();
    return json.imageUrls; // Assuming server returns { "imageUrls": [...] }
  },

  uploadVoiceMessage: async (connectionId: string, audioBlob: Blob): Promise<string> => {
    const token = localStorage.getItem('auth_token');
    if (!token) throw new Error("No token");

    const formData = new FormData();
    // Your Ktor backend explicitly looks for the field named "voice_note"
    // We pass 'voice_note.m4a' as a fallback filename, just like Kotlin!
    formData.append('voice_note', audioBlob, 'voice_note.m4a');

    const response = await fetch(`${SERVER_IP}/api/connections/${connectionId}/voice`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`
        // Do NOT set 'Content-Type'. The browser handles the multipart boundary automatically!
      },
      body: formData
    });

    if (!response.ok) {
      throw new Error("Failed to upload voice message");
    }

    const json = await response.json();
    return json.audioUrl; // Matches your Kotlin backend response parsing!
  },

  updateChatTheme: async (connectionId: string, themeName: string): Promise<void> => {
    const token = localStorage.getItem('auth_token');
    if (!token) throw new Error("No token");

    const response = await fetch(`${SERVER_IP}/api/connections/${connectionId}/theme`, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ themeName: themeName })
    });

    if (!response.ok) {
      throw new Error(`Failed to update theme. Status: ${response.status}`);
    }
  }
};
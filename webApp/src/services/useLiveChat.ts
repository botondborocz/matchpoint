import { useState, useEffect, useRef, useCallback } from 'react';
import { SERVER_DNS } from '../constants';
import { chatService, MessageDto } from './ChatService';

export function useLiveChat(connectionId: string | undefined) {
  const [messages, setMessages] = useState<MessageDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isConnected, setIsConnected] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (!connectionId) return;

    const token = localStorage.getItem('auth_token');
    if (!token) return;

    let isMounted = true;

    // 1. Fetch History First
    const loadHistory = async () => {
      try {
        const history = await chatService.getMessageHistory(connectionId);
        if (isMounted) {
          setMessages(history);
          setIsLoading(false);
          await chatService.markMessagesAsRead(connectionId);
        }
      } catch (error) {
        console.error("Failed to load history", error);
      }
    };

    loadHistory().then(() => {
      // 2. Connect to WebSocket AFTER history loads
      const wsUrl = `wss://${SERVER_DNS}/api/connections/${connectionId}/chat?token=${token}`;
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = () => {
        if (isMounted) setIsConnected(true);
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          
          if (!isMounted) return;

          // 👇 NEW: Intercept WebSocket events based on type!
          if (data.type === 'reaction') {
            setMessages((prev) => prev.map((msg) => {
              if (msg.id === data.messageId) {
                const currentReactions = msg.reactions || [];
                
                // 👇 THE FIX: Remove ANY existing reaction from this specific user
                const filtered = currentReactions.filter(r => r.userId !== data.userId);
                
                // Then, append their new reaction!
                return { 
                  ...msg, 
                  reactions: [...filtered, { userId: data.userId, emoji: data.emoji }] 
                };
              }
              return msg;
            }));

          } else if (data.type === 'remove_reaction') {
            setMessages((prev) => prev.map((msg) => {
              if (msg.id === data.messageId) {
                const currentReactions = msg.reactions || [];
                // Filter out the reaction from this specific user
                const filtered = currentReactions.filter(r => r.userId !== data.userId);
                return { ...msg, reactions: filtered };
              }
              return msg;
            }));

          } else {
            // It's a standard message! Append it.
            setMessages((prev) => [...prev, data]);
            chatService.markMessagesAsRead(connectionId);
          }
        } catch (err) {
          console.error("Failed to parse incoming message", err);
        }
      };

      ws.onclose = () => {
        if (isMounted) setIsConnected(false);
      };
    });

    // 3. Disconnect when the user leaves the screen
    return () => {
      isMounted = false;
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, [connectionId]);

  // 👇 4. Send Message (Now supports replies and JSON format)
  const sendMessage = useCallback((text: string, replyToMessageId: string | null = null) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      const payload = {
        type: "message",
        content: text,
        replyToMessageId: replyToMessageId
      };
      wsRef.current.send(JSON.stringify(payload));
    } else {
      console.warn("WebSocket is not connected.");
    }
  }, []);

  // 👇 2. ADD THE MEDIA SENDER FUNCTION
  const sendMedia = useCallback(async (files: FileList | File[], replyToMessageId: string | null = null) => {
    if (!connectionId) return;
    
    setIsUploading(true);
    try {
      const fileArray = Array.from(files);
      // 1. Upload files via REST first
      const urls = await chatService.uploadMedia(connectionId, fileArray);
      
      // 2. Format the payload exactly like your Kotlin App!
      let payloadContent = "";
      const isVideo = fileArray.some(f => f.type.startsWith('video/'));

      if (isVideo) {
          // Send video (Web usually doesn't generate local thumbs easily, so we just pass the URL.
          // Your Kotlin fallback logic handles missing thumbnails perfectly: [VIDEO]videoUrl)
          payloadContent = `[VIDEO]${urls[0]}`; 
      } else if (urls.length === 1) {
          payloadContent = `[IMAGE]${urls[0]}`;
      } else {
          payloadContent = `[IMAGES]${urls.join(',')}`;
      }

      // 3. Blast it to the WebSocket!
      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        const payload = {
          type: "message",
          content: payloadContent,
          replyToMessageId: replyToMessageId
        };
        wsRef.current.send(JSON.stringify(payload));
      }
    } catch (error) {
      console.error("Media upload failed:", error);
    } finally {
      setIsUploading(false);
    }
  }, [connectionId]);

  const sendVoiceMessage = useCallback(async (audioBlob: Blob, replyToMessageId: string | null = null) => {
    if (!connectionId) return;
    
    setIsUploading(true);
    try {
      // 1. Upload the audio blob via REST
      const url = await chatService.uploadVoiceMessage(connectionId, audioBlob);
      
      // 2. Blast the formatted string to the WebSocket!
      if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
        const payload = {
          type: "message",
          content: `[VOICE]${url}`,
          replyToMessageId: replyToMessageId
        };
        wsRef.current.send(JSON.stringify(payload));
      }
    } catch (error) {
      console.error("Voice upload failed:", error);
    } finally {
      setIsUploading(false);
    }
  }, [connectionId]);

  // 👇 5. Send Reaction
  const sendReaction = useCallback((messageId: string, emoji: string) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      const payload = {
        type: "reaction",
        content: emoji,
        targetMessageId: messageId
      };
      wsRef.current.send(JSON.stringify(payload));
    }
  }, []);

  // 👇 6. Remove Reaction
  const removeReaction = useCallback((messageId: string) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      const payload = {
        type: "remove_reaction",
        content: "",
        targetMessageId: messageId
      };
      wsRef.current.send(JSON.stringify(payload));
    }
  }, []);

  const updateTheme = useCallback(async (themeName: string) => {
    if (!connectionId) return;

    try {
      await chatService.updateChatTheme(connectionId, themeName);
      // Optional: You could update local state here if you wanted to optimistically
      // show the theme change, but since you manage the theme state in ChatDetailScreen,
      // it's fine to just let the API call happen in the background.
    } catch (error) {
      console.error("Failed to update theme:", error);
    }
  }, [connectionId]);

  // 👇 7. Return the new functions!
  return { 
    messages, 
    isLoading, 
    isConnected, 
    isUploading,
    sendMessage, 
    sendMedia,
    sendVoiceMessage,
    sendReaction, 
    removeReaction,
    updateTheme
  };
}
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { RefreshCw } from 'lucide-react';
import './MessagesScreen.css';
// 👇 Import the service and the type
import { chatService, ChatThreadDto } from '../../services/ChatService';

// Helpers
const getInitials = (name: string) => {
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) return `${parts[0][0].toUpperCase()}${parts[1][0].toUpperCase()}`;
  if (name) return name.substring(0, 2).toUpperCase();
  return '?';
};

const formatTime = (isoString: string) => {
  // Add a safety check in case the timestamp from the server is empty/invalid
  if (!isoString) return '';
  const date = new Date(isoString);
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

export default function MessagesScreen() {
  const navigate = useNavigate();
  const [threads, setThreads] = useState<ChatThreadDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 👇 The REAL API Call
  const loadConnections = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await chatService.getConnections();
      setThreads(data);
    } catch (err) {
      console.error(err);
      setError("Failed to load messages.");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadConnections();
  }, []);

  const handleChatClick = (thread: ChatThreadDto) => {
    navigate(`/chat/${thread.id}`, {
      state: { 
        otherUsername: thread.otherUserName, 
        otherUserImageUrl: thread.otherUserImageUrl,
        theme: thread.theme || "Default" // 👈 THE FIX: Pass the theme down to the Chat Screen!
      }
    });
  };

  return (
    <div className="messages-wrapper">
      <header className="messages-header">
        <h1 className="messages-title">Messages</h1>
        <button
          className={`refresh-btn ${isLoading ? 'spinning' : ''}`}
          onClick={loadConnections}
          disabled={isLoading}
        >
          <RefreshCw size={20} />
        </button>
      </header>

      {/* Error State */}
      {error && (
        <div style={{ color: 'var(--error-color)', textAlign: 'center', padding: '1rem' }}>
          {error}
        </div>
      )}

      {/* Loading State */}
      {isLoading && threads.length === 0 ? (
        <div className="center-content">
          <div className="loading-spinner"></div>
        </div>
      ) : threads.length === 0 && !error ? (
        /* Empty State */
        <div className="center-content empty-state fade-in-up">
          <div className="empty-icon">💬</div>
          <h2 className="empty-title">No Messages Yet</h2>
          <p className="empty-text">Swipe on players nearby or join a table to start a conversation.</p>
        </div>
      ) : (
        /* The List */
        <div className="chat-list">
          {threads.map((thread, index) => (
            <div
              key={thread.id}
              className="chat-list-item slide-in-bottom"
              style={{ animationDelay: `${index * 0.04}s` }}
              onClick={() => handleChatClick(thread)}
            >
              {/* Avatar & Online Indicator */}
              <div className="avatar-container">
                {thread?.otherUserImageUrl ? (
                  <img
                    src={thread?.otherUserImageUrl}
                    alt="Profile"
                    className="avatar-image"
                    // Convert database bias (-1 to 1) to CSS percentage (0% to 100%)
                    style={{ objectPosition: `50% ${((/*profile.avatarYBias ||*/ 0) + 1) * 50}%` }}
                  />
                ) : (
                  <div className="chat-avatar">{getInitials(thread.otherUserName || "User")}</div>
                )}
                {thread.isOnline && <div className="online-indicator"></div>}
              </div>

              {/* Content Box */}
              <div className="chat-item-content">
                <div className="chat-item-top">
                  <span className={`chat-item-name ${thread.unreadCount > 0 ? 'unread-bold' : ''}`}>
                    {thread.otherUserName}
                  </span>
                  <span className={`chat-item-time ${thread.unreadCount > 0 ? 'unread-time' : ''}`}>
                    {formatTime(thread.timestamp)}
                  </span>
                </div>

                <div className="chat-item-bottom">
                  <span className={`chat-item-message ${thread.unreadCount > 0 ? 'unread-message-bold' : ''}`}>
                    {thread.lastMessage}
                  </span>
                  {thread.unreadCount > 0 && (
                    <div className="unread-badge">{thread.unreadCount}</div>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
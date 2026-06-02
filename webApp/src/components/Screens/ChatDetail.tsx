import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { ArrowLeft, ChevronLeft, Send, Reply, Smile, X, Loader2, ImagePlus, Pause, Play, Mic, Palette, Check, ChevronRight } from 'lucide-react';
import './ChatDetail.css';

import { useLiveChat } from '../../services/useLiveChat';
import { ChatThemeManager } from '../../theme/ChatTheme';

// --- HELPERS ---
const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

const getInitials = (name: string) => {
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) return `${parts[0][0].toUpperCase()}${parts[1][0].toUpperCase()}`;
  if (name) return name.substring(0, 2).toUpperCase();
  return '?';
};

const formatMessageTime = (isoString: string) => {
  if (!isoString) return '';
  const date = new Date(isoString);
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

const isTimeGapGreater = (olderTime: string, newerTime: string, minutes: number) => {
  if (!olderTime || !newerTime) return false;
  const older = new Date(olderTime).getTime();
  const newer = new Date(newerTime).getTime();
  return (newer - older) / (1000 * 60) > minutes;
};

const EMOJIS = ["❤️", "😂", "😮", "😢", "🏓", "👍"];

const getDisplayContent = (rawContent: string) => {
  const content = rawContent.trim();
  if (content.startsWith('[VOICE]')) return '🎤 Voice Message';
  if (content.startsWith('[VIDEO]')) return '🎥 Video';
  if (content.startsWith('[IMAGE]') || content.startsWith('[IMAGES]')) return '📸 Photo';
  return content;
};

// --- CUSTOM VOICE PLAYER ---
const VoicePlayer = ({ url, isMe, onPlayStateChange }: { url: string, isMe: boolean, onPlayStateChange?: (playing: boolean) => void }) => {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);

  const togglePlay = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (audioRef.current) {
      const nextState = !isPlaying;
      if (isPlaying) audioRef.current.pause();
      else audioRef.current.play();

      setIsPlaying(nextState);
      if (onPlayStateChange) onPlayStateChange(nextState);
    }
  };

  const handleTimeUpdate = () => {
    if (audioRef.current) setProgress((audioRef.current.currentTime / audioRef.current.duration) * 100);
  };

  const handleLoadedMetadata = () => {
    if (audioRef.current) setDuration(audioRef.current.duration);
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (isPlaying && audioRef.current) {
      const newProgress = Number(e.target.value);
      const newTime = (newProgress / 100) * duration;
      audioRef.current.currentTime = newTime;
      setProgress(newProgress);
    }
  };

  const formatTime = (time: number) => {
    if (!time || isNaN(time)) return "0:00";
    const mins = Math.floor(time / 60);
    const secs = Math.floor(time % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div className={`voice-player ${isMe ? 'voice-me' : 'voice-them'}`} onClick={(e) => e.stopPropagation()}>
      <audio
        ref={audioRef}
        src={url}
        onTimeUpdate={handleTimeUpdate}
        onLoadedMetadata={handleLoadedMetadata}
        onEnded={() => {
          setIsPlaying(false);
          if (onPlayStateChange) onPlayStateChange(false);
        }}
        hidden
      />
      <button type="button" className="voice-play-btn" onClick={togglePlay}>
        {isPlaying ? <Pause size={18} fill="currentColor" /> : <Play size={18} fill="currentColor" className="play-offset" />}
      </button>
      <div className="voice-progress-container">

        <input
          type="range"
          min="0"
          max="100"
          value={progress || 0}
          onChange={handleSeek}
          disabled={!isPlaying}
          className="voice-slider"
          style={{
            background: `linear-gradient(to right, ${isMe ? 'white' : 'var(--accent-orange)'} ${progress}%, ${isMe ? 'rgba(255,255,255,0.3)' : 'rgba(156,163,175,0.3)'} ${progress}%)`
          }}
        />

        <span className="voice-time">
          {isPlaying && audioRef.current ? formatTime(audioRef.current.currentTime) : formatTime(duration)}
        </span>
      </div>
    </div>
  );
};


// --- GESTURE & HOVER AWARE MESSAGE COMPONENT ---
const MessageItem = ({ msg, isMe, isNewerSame, isOlderSame, isSelected, showTimeHeader, quotedMessage, currentUserId, otherUsername, activeReactionMenu, setActiveReactionMenu, setReplyingToMessageId, handleReactionClick, renderMessageContent, setSelectedMessageId, setReactionSheetMessageId }: any) => {

  const [swipeOffset, setSwipeOffset] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const touchStart = useRef({ x: 0, y: 0 });
  const pressTimer = useRef<NodeJS.Timeout | null>(null);
  const hasMoved = useRef(false);

  const [isAudioPlaying, setIsAudioPlaying] = useState(false);

  const topStart = !isMe && isOlderSame ? '0.25rem' : '1rem';
  const bottomStart = !isMe && isNewerSame ? '0.25rem' : '1rem';
  const topEnd = isMe && isOlderSame ? '0.25rem' : '1rem';
  const bottomEnd = isMe && isNewerSame ? '0.25rem' : '1rem';

  const isSingleImage = msg.content.trim().startsWith('[IMAGE]') && !msg.content.trim().startsWith('[IMAGES]');
  const isMultiImage = msg.content.trim().startsWith('[IMAGES]');
  const isVideo = msg.content.trim().startsWith('[VIDEO]');
  const isMediaNoPadding = isSingleImage || isMultiImage || isVideo;
  const showReactionMenu = activeReactionMenu === msg.id;

  const groupedReactions = msg.reactions?.reduce((acc: any, reaction: any) => {
    acc[reaction.emoji] = acc[reaction.emoji] || [];
    acc[reaction.emoji].push(reaction);
    return acc;
  }, {}) || {};

  const handleTouchStart = (e: React.TouchEvent) => {
    if (isAudioPlaying) return;

    const target = e.target as HTMLElement;
    if (
      target.closest('.message-actions') ||
      target.closest('.reaction-menu-popover') ||
      target.closest('.reactions-badge') ||
      target.closest('.voice-slider') ||
      target.closest('.voice-play-btn')
    ) return;

    touchStart.current = { x: e.touches[0].clientX, y: e.touches[0].clientY };
    setIsDragging(true);
    hasMoved.current = false;

    pressTimer.current = setTimeout(() => {
      if (!hasMoved.current) {
        setActiveReactionMenu(msg.id);
        if (navigator.vibrate) navigator.vibrate(50);
      }
    }, 400);
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    if (!isDragging || isAudioPlaying || showReactionMenu) return;

    const dx = e.touches[0].clientX - touchStart.current.x;
    const dy = e.touches[0].clientY - touchStart.current.y;

    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
      hasMoved.current = true;
      if (pressTimer.current) {
        clearTimeout(pressTimer.current);
        pressTimer.current = null;
      }
    }

    if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 5) {
      let rawOffset = isMe ? Math.min(0, dx) : Math.max(0, dx);
      let frictionOffset = rawOffset * 0.4;
      setSwipeOffset(frictionOffset || 0);
    }
  };

  const handlePointerDown = (e: React.PointerEvent) => {
    if (isAudioPlaying) return;

    const target = e.target as HTMLElement;
    if (
      target.closest('.message-actions') ||
      target.closest('.reaction-menu-popover') ||
      target.closest('.reactions-badge') ||
      target.closest('.voice-slider') ||
      target.closest('.voice-play-btn')
    ) return;

    touchStart.current = { x: e.clientX, y: e.clientY };
    setIsDragging(true);
    hasMoved.current = false;

    pressTimer.current = setTimeout(() => {
      if (!hasMoved.current) {
        setActiveReactionMenu(msg.id);
        if (navigator.vibrate) navigator.vibrate(50);
      }
    }, 400);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!isDragging || isAudioPlaying || showReactionMenu) return;

    const dx = e.clientX - touchStart.current.x;
    const dy = e.clientY - touchStart.current.y;

    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
      hasMoved.current = true;
      if (pressTimer.current) {
        clearTimeout(pressTimer.current);
        pressTimer.current = null;
      }
    }

    if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 5) {
      let rawOffset = isMe ? Math.min(0, dx) : Math.max(0, dx);
      let frictionOffset = rawOffset * 0.4;
      setSwipeOffset(frictionOffset || 0);
    }
  };

  const handleTouchEnd = () => {
    if (pressTimer.current) clearTimeout(pressTimer.current);
    setIsDragging(false);

    if (Math.abs(swipeOffset) > 50) {
      setReplyingToMessageId(msg.id);
      if (navigator.vibrate) navigator.vibrate(50);
    }
    setSwipeOffset(0);
  };

  const iconOpacity = Math.min(1, Math.abs(swipeOffset) / 50);
  const iconScale = 0.5 + (iconOpacity * 0.5);

  return (
    <div className={`message-cluster ${isMe ? 'is-me' : 'is-them'}`}>
      {showTimeHeader && <div className="time-header">{formatMessageTime(msg.createdAt)}</div>}
      <div className={`toggleable-time ${isSelected && !showTimeHeader ? 'visible' : ''}`}>{formatMessageTime(msg.createdAt)}</div>

      <div className="message-swipe-container">

        {/* 👇 Swipe icon alignment fixed in CSS */}
        <div className={`swipe-reply-icon ${isMe ? 'right' : 'left'}`} style={{ opacity: iconOpacity, transform: `scale(${iconScale})` }}>
          <div className="swipe-reply-circle"><Reply size={18} /></div>
        </div>

        <div
          className={`message-bubble-wrapper ${isNewerSame ? 'tight-gap' : 'loose-gap'}`}
          style={{
            transform: `translateX(${swipeOffset}px)`,
            transition: isDragging ? 'none' : 'transform 0.25s cubic-bezier(0.2, 0.8, 0.2, 1)'
          }}
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
          onTouchCancel={handleTouchEnd}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handleTouchEnd}
          onPointerCancel={handleTouchEnd}
          onPointerLeave={handleTouchEnd}
        >
          {/* DESKTOP ACTIONS */}
          <div className={`message-actions ${showReactionMenu ? 'force-visible' : ''} ${isMe ? 'actions-left' : 'actions-right'}`}>
            <div className="reaction-action-container">
              <button onClick={(e) => { e.stopPropagation(); setActiveReactionMenu(msg.id); }}>
                <Smile size={16} />
              </button>

              {/* 💻 DESKTOP POPOVER: Springs from the Emoji icon! */}
              {showReactionMenu && (
                <div className="reaction-menu-popover desktop-popover">
                  {EMOJIS.map(emoji => {
                    const hasReacted = msg.reactions?.some((r: any) => String(r.userId).trim().toLowerCase() === String(currentUserId).trim().toLowerCase() && r.emoji === emoji);
                    return (
                      <button key={emoji} className={`reaction-emoji-btn ${hasReacted ? 'active' : ''}`} onClick={(e) => { e.stopPropagation(); handleReactionClick(msg.id, emoji, hasReacted); }}>
                        {emoji}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            <button onClick={(e) => { e.stopPropagation(); setReplyingToMessageId(msg.id); setSelectedMessageId(null); }}>
              <Reply size={16} />
            </button>
          </div>

          {/* 📱 MOBILE POPOVER: Springs from the Bubble! */}
          {showReactionMenu && (
            <div className="reaction-menu-popover mobile-popover">
              {EMOJIS.map(emoji => {
                const hasReacted = msg.reactions?.some((r: any) => String(r.userId).trim().toLowerCase() === String(currentUserId).trim().toLowerCase() && r.emoji === emoji);
                return (
                  <button key={emoji} className={`reaction-emoji-btn ${hasReacted ? 'active' : ''}`} onClick={(e) => { e.stopPropagation(); handleReactionClick(msg.id, emoji, hasReacted); }}>
                    {emoji}
                  </button>
                );
              })}
            </div>
          )}

          {/* Main Bubble */}
          <div
            className={`message-bubble ${isSelected ? 'selected' : ''} ${isMediaNoPadding ? 'media-edge-to-edge' : ''}`}
            style={{ borderRadius: `${topStart} ${topEnd} ${bottomEnd} ${bottomStart}` }}
            onClick={() => { setSelectedMessageId(isSelected ? null : msg.id); setActiveReactionMenu(null); }}
          >
            {quotedMessage && (
              <div className={`quote-box ${isMediaNoPadding ? 'quote-floating' : ''}`} onClick={(e) => e.stopPropagation()}>
                <div className="quote-sender">{String(quotedMessage.senderId).trim().toLowerCase() === String(currentUserId).trim().toLowerCase() ? 'You' : otherUsername}</div>
                <div className="quote-text">{getDisplayContent(quotedMessage.content)}</div>
              </div>
            )}

            {renderMessageContent(msg.content, isMe, setIsAudioPlaying)}
          </div>

          {Object.keys(groupedReactions).length > 0 && (
            <div className="reactions-badge clickable-badge" onClick={(e) => { e.stopPropagation(); setReactionSheetMessageId(msg.id); }}>
              {Object.entries(groupedReactions).map(([emoji, reactions]: [string, any]) => (
                <span key={emoji} className={reactions.some((r: any) => String(r.userId).trim().toLowerCase() === String(currentUserId).trim().toLowerCase()) ? 'my-reaction' : ''}>
                  {emoji} {reactions.length > 1 && <span className="reaction-count">{reactions.length}</span>}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};


// --- MAIN SCREEN COMPONENT ---
export default function ChatDetailScreen() {
  const { chatId } = useParams<{ chatId: string }>();
  const navigate = useNavigate();
  const location = useLocation();

  const otherUsername = location.state?.otherUsername || "Player";
  const otherUserImageUrl = location.state?.otherUserImageUrl || "";
  const currentUserId = String(localStorage.getItem("user_id") || "").trim().toLowerCase();
  const initialThemeName = location.state?.theme || "Default";

  const [messageText, setMessageText] = useState('');
  const [selectedMessageId, setSelectedMessageId] = useState<string | null>(null);
  const [forceShowIcons, setForceShowIcons] = useState(false); // Controls the "Expand" arrow

  const isTyping = messageText.length > 0;

  const [replyingToMessageId, setReplyingToMessageId] = useState<string | null>(null);
  const [activeReactionMenu, setActiveReactionMenu] = useState<string | null>(null);
  const [reactionSheetMessageId, setReactionSheetMessageId] = useState<string | null>(null);

  const [fullScreenImage, setFullScreenImage] = useState<string | null>(null);

  const { messages, isLoading, isConnected, isUploading, sendMessage, sendMedia, sendVoiceMessage, sendReaction, removeReaction, updateTheme } = useLiveChat(chatId);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [isRecordingVoice, setIsRecordingVoice] = useState(false);
  const [recordingDuration, setRecordingDuration] = useState(0);

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<BlobPart[]>([]);

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mediaRecorder = new MediaRecorder(stream);

      mediaRecorderRef.current = mediaRecorder;
      audioChunksRef.current = [];

      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) audioChunksRef.current.push(e.data);
      };

      mediaRecorder.start();
      setIsRecordingVoice(true);
    } catch (error) {
      console.error("Microphone access denied or error:", error);
      alert("Could not access the microphone. Please check your browser permissions.");
    }
  };

  const stopAndSendRecording = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      mediaRecorderRef.current.onstop = () => {
        const mimeType = mediaRecorderRef.current?.mimeType || 'audio/webm';
        const audioBlob = new Blob(audioChunksRef.current, { type: mimeType });

        sendVoiceMessage(audioBlob, replyingToMessageId);
        setReplyingToMessageId(null);
        mediaRecorderRef.current?.stream.getTracks().forEach(track => track.stop());
      };
      mediaRecorderRef.current.stop();
    }
    setIsRecordingVoice(false);
  };

  const cancelRecording = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== "inactive") {
      mediaRecorderRef.current.onstop = () => {
        mediaRecorderRef.current?.stream.getTracks().forEach(track => track.stop());
      };
      mediaRecorderRef.current.stop();
    }
    setIsRecordingVoice(false);
  };

  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (isRecordingVoice) {
      interval = setInterval(() => {
        setRecordingDuration((prev) => prev + 1);
      }, 1000);
    } else {
      setRecordingDuration(0);
    }
    return () => clearInterval(interval);
  }, [isRecordingVoice]);

  const formatRecordingTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const [isThemeSheetOpen, setIsThemeSheetOpen] = useState(false);
  // 👇 THE FIX: Use the passed initialThemeName to find the correct theme object immediately
  const [currentTheme, setCurrentTheme] = useState(() => {
    return ChatThemeManager.themes.find(t => t.name === initialThemeName) || ChatThemeManager.themes[0];
  });

  // 👇 ALSO ADD THIS: If the user navigates between different chats, 
  // we need to update the theme if the location state changes.
  useEffect(() => {
    if (location.state?.theme) {
      const found = ChatThemeManager.themes.find(t => t.name === location.state.theme);
      if (found) setCurrentTheme(found);
    }
  }, [location.state?.theme, chatId]);

  // 👇 Convert the active theme into dynamic CSS Variables
  const themeStyles = {
    '--theme-bg': currentTheme.bgGradient,
    '--theme-my-bubble': currentTheme.myBubble,
    '--theme-other-bubble': currentTheme.otherBubble,
  } as React.CSSProperties;

  const prevMessageCountRef = useRef(messages.length);
  const isInitialMount = useRef(true);

  useEffect(() => {
    if (isInitialMount.current && messages.length > 0) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
      isInitialMount.current = false;
      prevMessageCountRef.current = messages.length;
    }
    else if (messages.length > prevMessageCountRef.current) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
      prevMessageCountRef.current = messages.length;
    }
  }, [messages]);

  useEffect(() => {
    if (replyingToMessageId && inputRef.current) {
      inputRef.current.focus();
    }
  }, [replyingToMessageId]);

  const handleBackgroundClick = (e: React.MouseEvent) => {
    const target = e.target as HTMLElement;
    if (
      target.closest('.message-bubble-wrapper') ||
      target.closest('.reaction-menu-popover') ||
      target.closest('.chat-input-container')
    ) return;

    setSelectedMessageId(null);
    setActiveReactionMenu(null);
  };

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (!messageText.trim()) return;

    sendMessage(messageText, replyingToMessageId);
    setMessageText('');
    setReplyingToMessageId(null);
  };

  const handleReactionClick = (msgId: string, emoji: string, hasReacted: boolean | undefined) => {
    if (hasReacted) removeReaction(msgId);
    else sendReaction(msgId, emoji);
    setActiveReactionMenu(null);
    setSelectedMessageId(null);
  };

  const replyingToMessage = messages.find(m => m.id === replyingToMessageId);
  const reactionSheetMessage = messages.find(m => m.id === reactionSheetMessageId);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      sendMedia(e.target.files, replyingToMessageId);
      setReplyingToMessageId(null);
      e.target.value = '';
    }
  };

  const renderMessageContent = (rawContent: string, isMe: boolean, onPlayStateChange?: (playing: boolean) => void) => {
    const content = rawContent.trim();

    const openImage = (e: React.MouseEvent, url: string) => {
      e.stopPropagation();
      setFullScreenImage(url);
    };

    if (content.startsWith('[IMAGES]')) {
      const urls = content.replace('[IMAGES]', '').split(',').map(u => u.trim());
      const count = urls.length;

      if (count === 1) return <img src={urls[0]} alt="Attachment" className="media-single zoomable" onClick={(e) => openImage(e, urls[0])} />;
      if (count === 2) return <div className="media-grid-2"><img src={urls[0]} alt="Attachment 1" className="zoomable" onClick={(e) => openImage(e, urls[0])} /><img src={urls[1]} alt="Attachment 2" className="zoomable" onClick={(e) => openImage(e, urls[1])} /></div>;
      if (count === 3) return <div className="media-grid-3"><img src={urls[0]} alt="Attachment 1" className="media-top-full zoomable" onClick={(e) => openImage(e, urls[0])} /><div className="media-row"><img src={urls[1]} alt="Attachment 2" className="zoomable" onClick={(e) => openImage(e, urls[1])} /><img src={urls[2]} alt="Attachment 3" className="zoomable" onClick={(e) => openImage(e, urls[2])} /></div></div>;

      return (
        <div className="media-grid-4">
          <div className="media-row"><img src={urls[0]} alt="Attachment 1" className="zoomable" onClick={(e) => openImage(e, urls[0])} /><img src={urls[1]} alt="Attachment 2" className="zoomable" onClick={(e) => openImage(e, urls[1])} /></div>
          <div className="media-row">
            <img src={urls[2]} alt="Attachment 3" className="zoomable" onClick={(e) => openImage(e, urls[2])} />
            <div className="media-more-wrapper">
              <img src={urls[3]} alt="Attachment 4" className="zoomable" onClick={(e) => openImage(e, urls[3])} />
              {count > 4 && <div className="media-more-overlay pointer-none">+{count - 4}</div>}
            </div>
          </div>
        </div>
      );
    }

    if (content.startsWith('[IMAGE]')) {
      const url = content.replace('[IMAGE]', '');
      return <img src={url.trim()} alt="Sent image" className="media-single zoomable" onClick={(e) => openImage(e, url.trim())} />;
    }

    if (content.startsWith('[VIDEO]')) {
      const data = content.replace('[VIDEO]', '').split(',');
      if (data.length > 1) return <video src={data[1].trim()} poster={data[0].trim()} controls className="media-single" onClick={(e) => e.stopPropagation()} />;
      return <video src={data[0].trim()} controls className="media-single" onClick={(e) => e.stopPropagation()} />;
    }

    if (content.startsWith('[VOICE]')) {
      const url = content.replace('[VOICE]', '');
      return <VoicePlayer url={url.trim()} isMe={isMe} onPlayStateChange={onPlayStateChange} />;
    }

    return <div className="message-text-content">{content}</div>;
  };

  return (
    <div className="chat-screen-wrapper" style={themeStyles} onClick={handleBackgroundClick}>
      {/* HEADER */}
      <header className="chat-header">
        <button className={isIOS ? "icon-btn-ios" : "icon-btn"} onClick={() => navigate(-1)}>
          {isIOS ? <ChevronLeft size={32} /> : <ArrowLeft size={24} />}
        </button>
        <div className="chat-header-user">
          {otherUserImageUrl ? (
            <img src={otherUserImageUrl} alt="Profile" className="chat-avatar-image" />
          ) : (
            <div className="chat-avatar">{getInitials(otherUsername)}</div>
          )}
          <span className="chat-title">
            {otherUsername}
            {!isConnected && <span style={{ fontSize: '10px', color: 'gray', marginLeft: '8px' }}>(Connecting...)</span>}
          </span>
          <button className="icon-btn theme-btn" onClick={() => setIsThemeSheetOpen(true)}>
            <Palette size={20} />
          </button>
        </div>
      </header>

      {/* MESSAGES LIST */}
      <main className="chat-messages-container">
        {isLoading && messages.length === 0 ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '2rem' }}><div className="loading-spinner"></div></div>
        ) : (
          messages.map((msg, index) => {
            const isMe = String(msg.senderId).trim().toLowerCase() === currentUserId;

            const olderMessage = messages[index - 1];
            const newerMessage = messages[index + 1];

            const showTimeHeader = !olderMessage || isTimeGapGreater(olderMessage.createdAt, msg.createdAt, 30);
            const newerShowsHeader = newerMessage && isTimeGapGreater(msg.createdAt, newerMessage.createdAt, 30);

            const isOlderSame = olderMessage?.senderId === msg.senderId && !showTimeHeader;
            const isNewerSame = newerMessage?.senderId === msg.senderId && !newerShowsHeader;

            const quotedMessage = msg.replyToMessageId ? messages.find(m => m.id === msg.replyToMessageId) : null;

            return (
              <MessageItem
                key={msg.id}
                msg={msg}
                isMe={isMe}
                isNewerSame={isNewerSame}
                isOlderSame={isOlderSame}
                isSelected={selectedMessageId === msg.id}
                showTimeHeader={showTimeHeader}
                quotedMessage={quotedMessage}
                currentUserId={currentUserId}
                otherUsername={otherUsername}
                activeReactionMenu={activeReactionMenu}
                setActiveReactionMenu={setActiveReactionMenu}
                setReplyingToMessageId={setReplyingToMessageId}
                handleReactionClick={handleReactionClick}
                renderMessageContent={renderMessageContent}
                setSelectedMessageId={setSelectedMessageId}
                setReactionSheetMessageId={setReactionSheetMessageId}
              />
            );
          })
        )}
        <div ref={messagesEndRef} />
      </main>

      {/* INPUT BAR */}
      <footer className="chat-input-container">
        <div className="chat-input-wrapper">

          {replyingToMessage && (
            <div className="reply-preview-bar">
              <div className="reply-preview-content">
                <span className="reply-preview-name">Replying to {String(replyingToMessage.senderId) === String(currentUserId) ? 'yourself' : otherUsername}</span>
                <span className="reply-preview-text">{getDisplayContent(replyingToMessage.content)}</span>
              </div>
              <button className="reply-cancel-btn" onClick={() => setReplyingToMessageId(null)}><X size={18} /></button>
            </div>
          )}

          <div className="unified-input-island">

            {isRecordingVoice ? (
              <div className="recording-ui-container">
                <button className="cancel-record-btn" onClick={cancelRecording}>
                  <X size={24} />
                </button>

                <div className="recording-indicator-pill">
                  <div className="recording-dot"></div>
                  <span className="recording-text">Recording...</span>
                  <span className="recording-timer">{formatRecordingTime(recordingDuration)}</span>
                </div>

                <button
                  className="send-record-btn"
                  onClick={stopAndSendRecording}
                >
                  <Send size={18} className="send-icon" />
                </button>
              </div>
            ) : (
              // --- DYNAMIC TYPING UI ---
              <form className="chat-input-form" onSubmit={handleSendMessage}>

                {/* 1. LEFT ICONS GROUP */}
                <div className={`left-action-group ${isTyping && !forceShowIcons ? 'collapsed' : 'expanded'}`}>
                  <button type="button" className="chat-icon-btn" onClick={() => fileInputRef.current?.click()}>
                    <ImagePlus size={18} />
                  </button>
                  <button type="button" className="chat-icon-btn mic-btn" onClick={startRecording}>
                    <Mic size={18} />
                  </button>
                </div>

                {/* 2. EXPAND ARROW */}
                {isTyping && !forceShowIcons && (
                  <button type="button" className="expand-btn fade-in" onClick={() => setForceShowIcons(true)}>
                    <ChevronRight size={18} />
                  </button>
                )}

                <input type="file" ref={fileInputRef} style={{ display: 'none' }} multiple accept="image/*,video/*" onChange={handleFileChange} />

                {/* 3. TEXT INPUT */}
                <input
                  ref={inputRef}
                  type="text"
                  className="chat-text-input"
                  placeholder="Message..."
                  value={messageText}
                  onFocus={() => setForceShowIcons(false)}
                  onChange={(e) => {
                    setMessageText(e.target.value);
                    if (e.target.value.length === 0) setForceShowIcons(false);
                  }}
                />

                {/* 4. SEND BUTTON (Modified: class always kept, state toggled) */}
                <button
                  type="submit"
                  className={`chat-send-btn ${isTyping ? 'active' : 'inactive'}`}
                  disabled={!isTyping}
                >
                  <Send size={18} className="send-icon" />
                </button>
              </form>
            )}

          </div>
        </div>
      </footer>

      {/* BOTTOM SHEET */}
      {reactionSheetMessage && (
        <div className="reaction-sheet-overlay" onClick={() => setReactionSheetMessageId(null)}>
          <div className="reaction-sheet-content" onClick={(e) => e.stopPropagation()}>
            <div className="reaction-sheet-header">
              <h3>Reactions</h3>
              <button onClick={() => setReactionSheetMessageId(null)}><X size={20} /></button>
            </div>
            <div className="reaction-sheet-list">
              {reactionSheetMessage.reactions?.map((reaction, index) => {
                const isMyReaction = String(reaction.userId).trim().toLowerCase() === currentUserId;

                return (
                  <div key={index} className="reaction-sheet-item">
                    <div className="reaction-sheet-emoji">{reaction.emoji}</div>
                    <div className="reaction-sheet-info">
                      <span className="reaction-sheet-name">{isMyReaction ? 'You' : otherUsername}</span>
                      {isMyReaction && <span className="reaction-sheet-hint">Tap to remove</span>}
                    </div>
                    {isMyReaction && (
                      <button className="reaction-sheet-remove-btn" onClick={() => { removeReaction(reactionSheetMessage.id); setReactionSheetMessageId(null); }}>
                        Remove
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* THEME SELECTION BOTTOM SHEET */}
      {isThemeSheetOpen && (
        <div className="reaction-sheet-overlay" onClick={() => setIsThemeSheetOpen(false)}>
          <div className="reaction-sheet-content theme-sheet" onClick={(e) => e.stopPropagation()}>
            <div className="reaction-sheet-header">
              <h3>Chat Theme</h3>
              <button onClick={() => setIsThemeSheetOpen(false)}><X size={20} /></button>
            </div>

            <div className="reaction-sheet-list">
              {ChatThemeManager.themes.map((theme) => {
                const isSelected = currentTheme.name === theme.name;
                return (
                  <div
                    key={theme.name}
                    className={`theme-list-item ${isSelected ? 'selected' : ''}`}
                    onClick={() => {
                      // 1. Update UI instantly
                      setCurrentTheme(theme);
                      setIsThemeSheetOpen(false);
                      // 2. Fire off the backend save in the background!
                      updateTheme(theme.name);
                    }}
                  >
                    <div
                      className="theme-preview-circle"
                      style={{ background: theme.bgGradient, borderColor: isSelected ? theme.myBubble : 'transparent' }}
                    />
                    <span className="theme-name">{theme.name}</span>
                    {isSelected && <Check size={20} color={theme.myBubble} />}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* FULL SCREEN IMAGE OVERLAY */}
      {fullScreenImage && (
        <div className="fullscreen-image-overlay" onClick={() => setFullScreenImage(null)}>
          <button className="fullscreen-close-btn" onClick={() => setFullScreenImage(null)}>
            <X size={24} />
          </button>
          <img src={fullScreenImage} alt="Fullscreen View" onClick={(e) => e.stopPropagation()} />
        </div>
      )}
    </div>
  );
}
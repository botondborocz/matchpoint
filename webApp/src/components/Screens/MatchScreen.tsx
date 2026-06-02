import React, { useState, useRef, useEffect } from 'react';
import { Star, MapPin, X, Check, Flame, Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { fetchNearbyPlayers, submitSwipe } from '../../services/UserService'; // 👈 Import your fetch function
import { useNavigate } from 'react-router-dom';
import './MatchScreen.css';

export interface Player {
    id: string;
    username: string;
    age: number;
    elo: number;
    distanceKm: number;
    skillLevel: string;
    imageUrl?: string;
}

// Define dummy data for not logged in users
const dummyPlayer: Player = {
    id: 'dummy',
    username: 'Table Tennis Fan',
    age: 28,
    elo: 1500,
    distanceKm: 5,
    skillLevel: 'Advanced',
    // A nice portrait from Unsplash
    imageUrl: 'https://images.unsplash.com/photo-1527980965255-d3b416303d12?auto=format&fit=crop&q=80&w=800'
};

export default function MatchScreen() {
    const { t } = useTranslation();
    const navigate = useNavigate();

    const isLoggedIn = !!localStorage.getItem("auth_token");

    // --- State ---
    // Initialize players based on auth status
    const [players, setPlayers] = useState<Player[]>(isLoggedIn ? [] : [dummyPlayer]);
    const [matchedPlayer, setMatchedPlayer] = useState<Player | null>(null);
    const [isLoading, setIsLoading] = useState(isLoggedIn); // Loading only if fetching
    const [error, setError] = useState<string | null>(null);
    const [hasSwiped, setHasSwiped] = useState(false);

    // --- Fetch Real Data ---
    useEffect(() => {
        if (!isLoggedIn) return; // Only fetch if logged in

        setIsLoading(true);
        fetchNearbyPlayers()
            .then((data) => {
                console.log(data)
                setPlayers(data);
                setIsLoading(false);
            })
            .catch((err) => {
                console.error(err);
                setError("Failed to load matches.");
                setIsLoading(false);
            });
    }, [isLoggedIn]);

    // --- Swipe Physics State ---
    const [dragPos, setDragPos] = useState({ x: 0, y: 0 });
    const [isDragging, setIsDragging] = useState(false);
    const [exitAnim, setExitAnim] = useState<'left' | 'right' | null>(null);
    const dragStart = useRef({ x: 0, y: 0 });

    const topPlayer = players[0];
    const nextPlayer = players[1];

    // --- Pointer (Touch/Mouse) Handlers ---
    const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
        // Prevent capturing if an animation is currently playing
        if (exitAnim) return;

        setIsDragging(true);
        // Record where the pointer started relative to the current card position
        dragStart.current = { x: e.clientX - dragPos.x, y: e.clientY - dragPos.y };
        e.currentTarget.setPointerCapture(e.pointerId); // Keeps tracking even if finger leaves the card
    };

    const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
        if (!isDragging) return;
        setDragPos({
            x: e.clientX - dragStart.current.x,
            y: e.clientY - dragStart.current.y
        });
    };

    const handlePointerUp = () => {
        if (!isDragging) return;
        setIsDragging(false);

        const threshold = window.innerWidth / 3;
        if (dragPos.x > threshold) {
            triggerSwipe('right');
        } else if (dragPos.x < -threshold) {
            triggerSwipe('left');
        } else {
            // Snap back to center
            setDragPos({ x: 0, y: 0 });
        }
    };

    // --- Action Triggers ---
    const triggerSwipe = async (direction: 'left' | 'right') => {
        if (!topPlayer) return;

        // 1. Immediately trigger the CSS exit animation for instant visual feedback
        setExitAnim(direction);
        setHasSwiped(true);

        const targetPlayerId = topPlayer.id;
        const isLiked = direction === 'right';

        // 2. Fire off the network request in the background
        try {
            const result = await submitSwipe(targetPlayerId, isLiked);

            // If the backend says it's a mutual match, show the celebration!
            if (result.isMatch) {
                setMatchedPlayer(topPlayer);
            }
        } catch (err) {
            console.error("Failed to record swipe:", err);
            // Optional: You could show a tiny toast here if you want to notify them it failed
        }

        // 3. Wait for the CSS animation to finish (300ms), then update the deck
        setTimeout(() => {
            setPlayers(prev => prev.slice(1)); // Remove the top player we just swiped
            setDragPos({ x: 0, y: 0 });        // Reset positions for next card
            setExitAnim(null);                 // Clear animation state
        }, 300);
    };

    return (
        <div className="match-container">
            {/* Common parts which can be blurred */}
            <div className={`blurred-content-wrapper ${!isLoggedIn ? 'blurred' : ''}`}>
                {/* --- HEADER --- */}
                <div className="match-header fade-in-down">
                    <Star size={24} className="text-orange" />
                    <h1 className="header-title">{t('find_your_match', 'Find your match')}</h1>
                </div>

                {/* --- CARD STACK AREA --- */}
                {/* --- CARD STACK AREA --- */}
                <div className="card-stack-area">
                    {isLoading ? (
                        // 👇 1. Show this while waiting for the Ktor backend
                        <div className="loading-state fade-in">
                            <Loader2 size={48} className="spinner text-orange" />
                            {/* <p className="text-muted mt-4">{t('finding_matches', 'Finding players nearby...')}</p> */}
                        </div>
                    ) : players.length === 0 ? (
                        // 2. Show this if backend returns an empty array
                        <p className="text-muted">No more matches nearby!</p>
                    ) : (
                        // 3. Show the cards if we have data!
                        <>
                            <div className={`card-stack-wrapper ${!hasSwiped ? 'animate-spring-up' : ''}`}>
                                {/* 1. The Background Card (Next Player) */}
                                {nextPlayer && (
                                    <div key={`bg-${nextPlayer.id}`} className="match-card background-card">
                                        <MatchCardContent player={nextPlayer} animate={false} />
                                    </div>
                                )}

                                {/* 2. The Foreground Interactive Card (Top Player) */}
                                {topPlayer && (
                                    <div
                                        key={`top-${topPlayer.id}`}
                                        className={`match-card top-card ${!isDragging ? 'animate-snap' : ''} ${exitAnim ? `exit-${exitAnim}` : ''}`}
                                        style={{
                                            transform: exitAnim
                                                ? undefined
                                                : `translate(${dragPos.x}px, ${dragPos.y}px) rotate(${dragPos.x * 0.05}deg)`,
                                            opacity: exitAnim ? 0 : 1 - Math.abs(dragPos.x) / (window.innerWidth * 1.5),
                                            touchAction: 'none'
                                        }}
                                        onPointerDown={handlePointerDown}
                                        onPointerMove={handlePointerMove}
                                        onPointerUp={handlePointerUp}
                                        onPointerCancel={handlePointerUp}
                                    >
                                        <MatchCardContent player={topPlayer} animate={!hasSwiped} />
                                    </div>
                                )}
                            </div>

                            {/* --- ACTION BUTTONS (Moved OUTSIDE the wrapper!) --- */}
                            <div className="action-buttons">
                                <button className="action-btn pass-btn" onClick={() => triggerSwipe('left')}>
                                    <X size={32} />
                                </button>
                                <button className="action-btn like-btn" onClick={() => triggerSwipe('right')}>
                                    <Check size={32} />
                                </button>
                            </div>
                        </>
                    )}
                </div>
            </div>

            {/* --- CELEBRATION OVERLAY --- */}
            {matchedPlayer && (
                <div className="match-overlay fade-in">
                    <h1 className="match-title">IT'S A MATCH!</h1>
                    <p className="match-subtitle">You and {matchedPlayer.username} liked each other.</p>

                    <div className="match-avatars">
                        <div className="avatar me">JD</div>
                        <div className="avatar them">{matchedPlayer.username.charAt(0)}</div>
                    </div>

                    <button className="primary-btn mt-12" onClick={() => setMatchedPlayer(null)}>
                        SEND MESSAGE
                    </button>
                    <button className="secondary-btn mt-4" onClick={() => setMatchedPlayer(null)}>
                        KEEP SWIPING
                    </button>
                </div>
            )}

            {/* --- UNAUTH OVERLAY --- */}
            {!isLoggedIn && (
                <div className="unauth-overlay">
                    <div className="unauth-content">
                        <h1 className="overlay-title">
                            {t('find_your_match_unauth_title', 'READY TO MEET YOUR MATCH?').toUpperCase()}
                        </h1>

                        <p className="overlay-subtitle">
                            {t('login_to_swipe_overlay', 'Log in to discover table tennis players near you, compare ELOs, and set up your next game.')}
                        </p>

                        <button
                            className="primary-btn mt-4"
                            onClick={() => navigate('/profile')} // Routes to your Login screen
                        >
                            {t('login', 'Log In / Sign Up')} / {t('register', 'Register')}
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}

// --- SUB-COMPONENTS ---

// 1. Add the animate prop
function MatchCardContent({ player, animate = false }: { player: Player, animate?: boolean }) {
    console.log(player);
    return (
        <>
            {player.imageUrl && (
                <img
                    src={player.imageUrl}
                    alt={`${player.username}'s profile`}
                    className="card-image"
                />
            )}
            <div className="card-gradient-overlay" />

            <div className="card-content-inner">
                <div className="card-info">
                    {/* 2. Conditionally apply stagger-1 */}
                    <div className={`info-row align-bottom ${animate ? 'stagger-1' : ''}`}>
                        <span className="card-name">{player.username},</span>
                        <span className="card-age">{player.age}</span>
                        <div className="elo-tag">
                            <Star size={14} />
                            <span>ELO {player.elo}</span>
                        </div>
                    </div>

                    {/* 3. Conditionally apply stagger-2 */}
                    <div className={`info-row mt-2 text-gray ${animate ? 'stagger-2' : ''}`}>
                        <MapPin size={16} />
                        <span>{player.distanceKm} km away</span>
                    </div>

                    {/* 4. Conditionally apply stagger-3 */}
                    <div className={`info-row mt-4 ${animate ? 'stagger-3' : ''}`}>
                        <div className="skill-chip">{player.skillLevel}</div>
                    </div>
                </div>
            </div>
        </>
    );
}
import { useState, useEffect, useRef } from 'react';
import {
    Wrench, Globe, Palette, Settings as SettingsIcon, LogOut,
    ChevronDown, ChevronRight, Check,
    Map, Camera, X, Edit2
} from 'lucide-react';
import { fetchUserProfile, clearProfileCache, UserProfile } from '../../services/UserService.ts'; // Adjust path
import './ProfileScreen.css';
import { useTheme } from '../../theme/ThemeContext';
import { SharedRes } from '../../shared/SharedRes.ts';
import { useTranslation } from 'react-i18next';
import i18n from '../../i18n.tsx';
import { SERVER_IP } from '../../constants.ts';

interface ProfileScreenProps {
    onLogout: () => void;
}

export default function ProfileScreen({ onLogout }: ProfileScreenProps) {
    const { t } = useTranslation();
    // --- Data State ---
    const [profile, setProfile] = useState<UserProfile | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [editError, setEditError] = useState<string | null>(null);

    // --- Dropdown State ---
    const [openDropdown, setOpenDropdown] = useState<'language' | 'theme' | null>(null);
    const [language, setLanguage] = useState(t('english'));
    const { theme, setTheme } = useTheme();

    const getThemeDisplayText = () => {
        if (theme === 'light') return t('light');
        if (theme === 'dark') return t('dark');
        return t('system_default');
    };

    // Detect if the user is on iOS
    const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) ||
        (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

    // 👇 1. Add Snackbar state and handler
    const [showToast, setShowToast] = useState(false);

    // 👇 2. NEW: State and Refs for Image Cropping
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [imageToCrop, setImageToCrop] = useState<{ file: File, url: string } | null>(null);
    const [yBias, setYBias] = useState<number>(0);
    const [zoom, setZoom] = useState<number>(1);
    const [isUploading, setIsUploading] = useState(false);

    // 👇 2. NEW: State for the Edit Profile Modal
    const [isEditProfileModalOpen, setIsEditProfileModalOpen] = useState(false);
    const [editForm, setEditForm] = useState({
        name: '',
        blade: '',
        forehand: '',
        backhand: ''
    });
    const [isSavingProfile, setIsSavingProfile] = useState(false);

    const handleResetMapChoice = () => {
        localStorage.removeItem('preferred_map_app'); // Clear the preference
        setShowToast(true); // Show the snackbar

        // Hide it automatically after 3 seconds
        setTimeout(() => {
            setShowToast(false);
        }, 3000);
    };

    // Fetch data exactly once when the screen loads
    useEffect(() => {
        fetchUserProfile()
            .then((data) => {
                if (localStorage.getItem('user_id') === null) {
                    localStorage.setItem('user_id', data.id);
                }

                setProfile(data);
                if (data.preferredLanguage) {
                    handleSelectLanguage(data.preferredLanguage);
                } else {
                    // 2. If no DB preference, check the System Language!
                    // navigator.language returns things like "hu-HU" or "en-US"
                    const systemLang = navigator.language.toLowerCase();

                    // If their system is Hungarian, set 'hu'. Otherwise, fallback to 'en'.
                    if (systemLang.startsWith('hu')) {
                        handleSelectLanguage('hu');
                    } else {
                        handleSelectLanguage('en');
                    }
                }
                setIsLoading(false);
            })
            .catch((err) => {
                console.error(err);
                setError("Failed to load profile data.");
                setIsLoading(false);
            });
    }, []);

    const handleLogoutClick = () => {
        clearProfileCache(); // Wipe memory cache
        onLogout();          // Call App.tsx logout (wipes localStorage)
    };

    const toggleDropdown = (menu: 'language' | 'theme') => {
        setOpenDropdown(openDropdown === menu ? null : menu);
    };

    const handleSelectLanguage = async (lang: string, save_to_db: boolean = true) => {
        const newLang = lang === 'en' ? 'en' : 'hu';
        i18n.changeLanguage(newLang);
        setLanguage(newLang === 'en' ? 'English' : 'Magyar');
        setOpenDropdown(null);
        if (!save_to_db) return; // Skip DB call if we're just syncing with profile data
        // 2. Save to Database in the background
        try {
            const token = localStorage.getItem('auth_token');
            if (!token) return; // If they aren't logged in, skip the DB call

            // Note: Make sure this URL matches your Ktor routing! 
            // If your route block is inside /api/users, use `${SERVER_IP}/api/users/language`
            const response = await fetch(`${SERVER_IP}/api/users/language`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${token}`
                },
                body: JSON.stringify({ language: newLang })
            });

            if (!response.ok) {
                console.error("Failed to sync language to database:", await response.text());
                // Optional: Show a tiny error toast to the user here
            } else {
                console.log("Language successfully saved to database!");
            }

        } catch (error) {
            console.error("Network error while saving language:", error);
        }
    };

    const handleSelectTheme = (thm: 'light' | 'dark' | 'system') => {
        setTheme(thm);
        setOpenDropdown(null);
    };

    // 👇 3. NEW: Handle file selection from the hidden input
    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) {
            // Create a temporary local URL to preview the image
            const url = URL.createObjectURL(file);
            setImageToCrop({ file, url });
            setYBias(0); // Reset bias to center
            setZoom(1);
        }
        // Clear the input so selecting the same file again still triggers onChange
        if (fileInputRef.current) fileInputRef.current.value = '';
    };

    // 👇 4. NEW: Handle the actual upload
    const handleUploadImage = async () => {
        if (!imageToCrop) return;
        setIsUploading(true);

        try {
            const token = localStorage.getItem('auth_token');
            const formData = new FormData();
            formData.append('image', imageToCrop.file);
            formData.append('avatarYBias', yBias.toString()); // Send the bias to Ktor
            formData.append('avatarZoom', zoom.toString());

            // Note: When sending FormData, DO NOT set the 'Content-Type' header.
            // The browser automatically sets it to 'multipart/form-data' with the correct boundary.
            const response = await fetch(`${SERVER_IP}/api/users/profile-image`, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${token}` },
                body: formData
            });

            if (response.ok) {
                // Refresh the profile to get the new image URL and bias from the server
                const updatedProfile = await fetchUserProfile();
                setProfile(updatedProfile);
                setImageToCrop(null); // Close dialog
            } else {
                console.error("Upload failed");
                // Optional: show an error toast
            }
        } catch (error) {
            console.error("Network error during upload", error);
        } finally {
            setIsUploading(false);
        }
    };

    // 👇 3. NEW: Open the modal and pre-fill current values
    const openEditModal = () => {
        setEditForm({
            name: profile?.name || '',
            blade: profile?.blade || 'Butterfly Viscaria', // Fallbacks to your placeholders if null
            forehand: profile?.rubberFh || 'Tenergy 05',
            backhand: profile?.rubberBh || 'Dignics 09C'
        });
        setIsEditProfileModalOpen(true);
    };

    // 👇 4. NEW: Handle saving the text data to Ktor
    const handleSaveProfileDetails = async () => {
        setIsSavingProfile(true);
        setEditError(null); // Clear any previous errors

        try {
            const token = localStorage.getItem('auth_token');
            const response = await fetch(`${SERVER_IP}/api/users/me`, {
                method: 'PUT',
                headers: {
                    'Authorization': `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(editForm)
            });

            if (response.ok) {
                // Success!
                setProfile(prev => prev ? { ...prev, ...editForm } : null);
                setIsEditProfileModalOpen(false);
            } else if (response.status === 409) {
                // 👇 Catch the duplicate username error!
                setEditError("That username is already taken. Please choose another.");
            } else {
                setEditError("Failed to update profile. Please try again.");
            }
        } catch (error) {
            console.error("Network error saving profile", error);
            setEditError("Network error. Check your connection.");
        } finally {
            setIsSavingProfile(false);
        }
    };

    // Helper to get initials (e.g., "player_2" -> "P2", "János Doe" -> "JD")
    const getInitials = (name: string) => {
        const parts = name.split(/[ _-]/); // Split by space, underscore, or dash
        if (parts.length >= 2) {
            return (parts[0][0] + parts[1][0]).toUpperCase();
        }
        return name.substring(0, 2).toUpperCase();
    };

    if (isLoading) {
        return <div className="profile-container flex items-center justify-center pt-20"><p className="text-muted">Betöltés...</p></div>;
    }

    if (error || !profile) {
        return <div className="profile-container flex items-center justify-center pt-20"><p className="text-red-500">{error}</p></div>;
    }

    return (
        <div className="profile-container pb-24">
            {/* 👇 5. NEW: Hidden File Input */}
            <input
                type="file"
                accept="image/*"
                hidden
                ref={fileInputRef}
                onChange={handleFileSelect}
            />
            {/* --- TOP SECTION: AVATAR & INFO (Animates immediately) --- */}
            <div className="profile-header card-surface animate-slide-up">
                {/* 👇 6. UPDATED: Avatar Wrapper is now clickable */}
                <div
                    className="avatar-wrapper clickable-avatar"
                    onClick={() => fileInputRef.current?.click()}
                >
                    <div className="avatar-gradient-ring">
                        {profile?.imageUrl ? (
                            <img
                                src={profile.imageUrl}
                                alt="Profile"
                                className="avatar-image"
                                // Convert database bias (-1 to 1) to CSS percentage (0% to 100%)
                                style={{
                                    objectPosition: `50% ${((/*profile.avatarYBias ||*/ 0) + 1) * 50}%`,
                                    transform: `scale(${/*profile.avatarZoom ||*/ 1})`
                                }}
                            />
                        ) : (
                            <div className="avatar-inner">{getInitials(profile?.name || "User")}</div>
                        )}
                    </div>

                    {/* The small camera icon overlay */}
                    <div className="avatar-edit-bubble">
                        <Camera size={14} color="white" />
                    </div>
                </div>
                {/* <div className="avatar-wrapper">
                    <div className="avatar-gradient-ring">
                        <div className="avatar-inner">{getInitials(profile.name)}</div>
                    </div>
                </div> */}



                <div className="header-info">
                    {/* 👇 5. Add an Edit icon next to the username */}
                    <div className="username-row" onClick={openEditModal}>
                        <h1 className="username mb-0">{profile?.name}</h1>
                        <Edit2 size={16} className="text-muted edit-icon-btn" />
                    </div>

                    <div className="badges-row mt-3">
                        <span className="elo-badge">ELO {profile?.elo}</span>
                        <span className="member-since">Win Rate: {profile?.winRate}</span>
                    </div>
                </div>
            </div>

            {/* 👇 7. NEW: The Crop/Frame Dialog Overlay */}
            {imageToCrop && (
                <div className="crop-modal-overlay">
                    <div className="crop-modal-card">

                        <button className="close-modal-btn" onClick={() => setImageToCrop(null)}>
                            <X size={24} />
                        </button>

                        <h2 className="crop-title">Frame Your Avatar</h2>

                        {/* 1:1 Preview Box */}
                        <div className="crop-preview-box">
                            <img
                                src={imageToCrop.url}
                                alt="Preview"
                                className="crop-preview-image"
                                // Real-time preview as the slider moves
                                style={{
                                    objectPosition: `50% ${(yBias + 1) * 50}%`,
                                    transform: `scale(${zoom})`
                                }}
                            />
                        </div>

                        {/* --- ZOOM SLIDER --- */}
                        <div className="slider-container" style={{ width: '100%', marginBottom: '16px' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                                <span className="crop-subtitle">Zoom</span>
                                <span className="crop-subtitle">{Math.round(zoom * 100)}%</span>
                            </div>
                            <input
                                type="range"
                                min="1" max="3" step="0.05"
                                value={zoom}
                                onChange={(e) => setZoom(parseFloat(e.target.value))}
                                className="bias-slider"
                                style={{ marginBottom: '8px' }} // Tweak margin so it stacks nicely
                            />
                        </div>

                        {/* --- PAN SLIDER --- */}
                        <div className="slider-container" style={{ width: '100%', marginBottom: '24px' }}>
                            <span className="crop-subtitle">Vertical Pan</span>
                            <input
                                type="range"
                                min="-1" max="1" step="0.01"
                                value={yBias}
                                onChange={(e) => setYBias(parseFloat(e.target.value))}
                                className="bias-slider"
                            />
                        </div>

                        <div className="crop-actions">
                            <button className="crop-cancel-btn" onClick={() => setImageToCrop(null)}>
                                Cancel
                            </button>
                            <button
                                className="crop-save-btn"
                                onClick={handleUploadImage}
                                disabled={isUploading}
                            >
                                {isUploading ? "Saving..." : "Save & Upload"}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* --- SECTION: MY EQUIPMENT (Delayed by 0.1s) --- */}
            <div className="content-section animate-slide-up delay-1">
                <div className="section-title justify-between">
                    <div className="flex items-center gap-3">
                        <Wrench size={22} className="text-muted" />
                        <h2>{t('my_gear')}</h2>
                    </div>
                    {/* 👇 6. Add an Edit button for the gear */}
                    <button className="icon-btn" onClick={openEditModal}>
                        <Edit2 size={18} className="text-muted" />
                    </button>
                </div>

                <div className="item-list equipment-grid">
                    {/* Blade */}
                    <div className="item-card">
                        <div className="icon-box">
                            <span role="img" aria-label="paddle" className="text-xl">🏓</span>
                        </div>
                        <div className="item-info">
                            <span className="item-label">{t('blade')}</span>
                            <p className="item-value">{profile?.blade}</p>
                        </div>
                    </div>

                    {/* Forehand Rubber */}
                    <div className="item-card">
                        <div className="icon-box">
                            <div className="rubber-dot red"></div>
                        </div>
                        <div className="item-info">
                            <span className="item-label">{t('forehand')}</span>
                            <p className="item-value">{profile?.rubberFh}</p>
                        </div>
                    </div>

                    {/* Backhand Rubber */}
                    <div className="item-card">
                        <div className="icon-box">
                            <div className="rubber-dot black"></div>
                        </div>
                        <div className="item-info">
                            <span className="item-label">{t('backhand')}</span>
                            <p className="item-value">{profile?.rubberBh}</p>
                        </div>
                    </div>
                </div>
            </div>

            {/* 👇 7. NEW: EDIT PROFILE MODAL */}
            {isEditProfileModalOpen && (
                <div className="crop-modal-overlay">
                    <div className="crop-modal-card">
                        <button className="close-modal-btn" onClick={() => {
                            setIsEditProfileModalOpen(false);
                            setEditError(null);
                        }}>
                            <X size={24} />
                        </button>

                        <h2 className="crop-title">Edit Profile</h2>

                        {/* 👇 Show the red error text if it exists */}
                        {editError && (
                            <p style={{ color: '#FF4B4B', fontSize: '14px', marginBottom: '16px', textAlign: 'center' }}>
                                {editError}
                            </p>
                        )}

                        <div className="edit-form-container">
                            <div className="input-group">
                                <label>Username</label>
                                <input
                                    type="text"
                                    className="edit-input"
                                    value={editForm.name}
                                    onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                                />
                            </div>
                            <div className="input-group">
                                <label>Blade</label>
                                <input
                                    type="text"
                                    className="edit-input"
                                    value={editForm.blade}
                                    onChange={(e) => setEditForm({ ...editForm, blade: e.target.value })}
                                />
                            </div>
                            <div className="input-group">
                                <label>Forehand Rubber</label>
                                <input
                                    type="text"
                                    className="edit-input"
                                    value={editForm.forehand}
                                    onChange={(e) => setEditForm({ ...editForm, forehand: e.target.value })}
                                />
                            </div>
                            <div className="input-group">
                                <label>Backhand Rubber</label>
                                <input
                                    type="text"
                                    className="edit-input"
                                    value={editForm.backhand}
                                    onChange={(e) => setEditForm({ ...editForm, backhand: e.target.value })}
                                />
                            </div>
                        </div>

                        <div className="crop-actions mt-6">
                            <button className="crop-cancel-btn" onClick={() => {
                                setIsEditProfileModalOpen(false);
                                setEditError(null);
                            }}>
                                Cancel
                            </button>
                            <button
                                className="crop-save-btn"
                                onClick={handleSaveProfileDetails}
                                disabled={isSavingProfile}
                            >
                                {isSavingProfile ? "Saving..." : "Save Details"}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* --- SECTION: SETTINGS (Delayed by 0.2s) --- */}
            <div className="content-section mt-6 animate-slide-up delay-2">
                <div className="item-list settings-grid">

                    {/* --- LANGUAGE DROPDOWN --- */}
                    <div className={`expandable-card ${openDropdown === 'language' ? 'open' : ''}`}>
                        <div className="expandable-header" onClick={() => toggleDropdown('language')}>
                            <Globe size={22} className="text-muted ml-1 mr-4" />
                            <span className="item-title">{t('language')}</span>
                            <div className="item-action">
                                <span className="text-muted">{language}</span>
                                <ChevronDown size={20} className="text-muted chevron-icon" />
                            </div>
                        </div>

                        {/* The Animated Content */}
                        <div className="expandable-content-wrapper">
                            <div className="expandable-content">
                                <div className="options-list">
                                    <button
                                        className={`option-btn ${language === 'Magyar' ? 'selected' : ''}`}
                                        onClick={() => handleSelectLanguage('hu')}
                                    >
                                        {t('hungarian')}
                                    </button>
                                    <button
                                        className={`option-btn ${language === 'English' ? 'selected' : ''}`}
                                        onClick={() => handleSelectLanguage('en')}
                                    >
                                        {t('english')}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* --- THEME DROPDOWN --- */}
                    <div className={`expandable-card ${openDropdown === 'theme' ? 'open' : ''}`}>
                        <div className="expandable-header" onClick={() => toggleDropdown('theme')}>
                            <Palette size={22} className="text-muted ml-1 mr-4" />
                            <span className="item-title">{t('theme')}</span>
                            <div className="item-action">
                                {/* Show the dynamic display text */}
                                <span className="text-muted">{getThemeDisplayText()}</span>
                                <ChevronDown size={20} className="text-muted chevron-icon" />
                            </div>
                        </div>

                        <div className="expandable-content-wrapper">
                            <div className="expandable-content">
                                <div className="options-list">
                                    <button
                                        className={`option-btn ${theme === 'light' ? 'selected' : ''}`}
                                        onClick={() => { setTheme('light'); setOpenDropdown(null); }}
                                    >
                                        {t('light')}
                                    </button>
                                    <button
                                        className={`option-btn ${theme === 'dark' ? 'selected' : ''}`}
                                        onClick={() => { setTheme('dark'); setOpenDropdown(null); }}
                                    >
                                        {t('dark')}
                                    </button>
                                    <button
                                        className={`option-btn ${theme === 'system' ? 'selected' : ''}`}
                                        onClick={() => { setTheme('system'); setOpenDropdown(null); }}
                                    >
                                        {t('system_default')}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* --- RESET MAP PREFERENCE (iOS Only) --- */}
                    {isIOS && (
                        <div className="item-card clickable" onClick={handleResetMapChoice}>
                            <Map size={22} className="text-muted ml-1 mr-4" />
                            <span className="item-title">{t('reset_map_preference', 'Reset Default Map App')}</span>
                            <div className="item-action">
                                <ChevronRight size={20} className="text-muted" />
                            </div>
                        </div>
                    )}

                    {/* Account Settings */}
                    <div className="item-card clickable">
                        <SettingsIcon size={22} className="text-muted ml-1 mr-4" />
                        <span className="item-title">Account Settings</span>
                        <div className="item-action">
                            <ChevronRight size={20} className="text-muted" />
                        </div>
                    </div>

                </div>
                {/* --- SNACKBAR / TOAST NOTIFICATION --- */}
                {showToast && (
                    <div
                        className="fixed bottom-24 left-1/2 transform -translate-x-1/2 bg-green-500 text-white px-5 py-3 rounded-full shadow-lg flex items-center gap-2 z-50"
                        style={{ animation: 'fadeInOut 3s ease-in-out' }}
                    >
                        <Check size={18} />
                        <span className="font-medium text-sm">
                            {t('map_reset_success', 'Map preference reset!')}
                        </span>
                    </div>
                )}
            </div>

            {/* --- LOGOUT BUTTON (Delayed by 0.3s) --- */}
            <div className="content-section mt-8 desktop-logout-container animate-slide-up delay-3">
                <button className="logout-btn" onClick={handleLogoutClick}>
                    <LogOut size={20} />
                    <span>{t('logout')}</span>
                </button>
            </div>

        </div>
    );
}
import React, { useMemo, useState } from 'react';
import { AlertCircle, Loader2, Eye, EyeOff, Mail, Lock } from 'lucide-react';
import './Auth.css';
import { SERVER_IP } from '../../constants';
import { useNavigate } from 'react-router-dom';
import { useGoogleLogin } from '@react-oauth/google';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../theme/ThemeContext';

// 👇 1. Added the exact same prop so we can auto-login!
interface RegisterScreenProps {
    onLoginSuccess: (token: string) => void;
}

export default function RegisterScreen({ onLoginSuccess }: RegisterScreenProps) {
    const { t } = useTranslation();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const { theme } = useTheme();

    const isDarkMode = useMemo(() => {
        if (theme === 'dark') return true;
        if (theme === 'light') return false;
        // If it's 'system', ask the browser/phone what the OS is currently set to!
        return window.matchMedia('(prefers-color-scheme: dark)').matches;
    }, [theme]);

    const navigate = useNavigate();

    const handleRegister = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!email || !password) return;

        setIsLoading(true);
        setError(null);

        try {
            const response = await fetch(`${SERVER_IP}/api/auth/register`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password }),
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || 'Registration failed.');
            }

            const data = await response.json();

            // 🎉 Auto-login the user immediately!
            onLoginSuccess(data.token);

        } catch (err: any) {
            setError(err.message);
        } finally {
            setIsLoading(false);
        }
    };

    // 👇 2. Allow Google registration too!
    const registerWithGoogle = useGoogleLogin({
        onSuccess: async (tokenResponse) => {
            setIsLoading(true);
            setError(null);
            try {
                const response = await fetch(`${SERVER_IP}/api/auth/google`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ idToken: tokenResponse.access_token }),
                });

                if (!response.ok) throw new Error("Failed to authenticate with server");

                const data = await response.json();
                onLoginSuccess(data.token);
            } catch (err) {
                setError("Google Registration failed. Please try again.");
            } finally {
                setIsLoading(false);
            }
        },
        onError: () => setError("Google popup was closed or failed to connect.")
    });

    return (
        <div className="auth-wrapper">
            <form onSubmit={handleRegister} className="auth-form">

                <div className="fade-in-up" style={{ textAlign: 'center' }}>
                    {isDarkMode ? (
                        <img
                            src="../../../assets/matchpoint_logo_long_dark.png"
                            alt="Match Logo"
                            className="auth-logo"
                        />)
                        : (
                            <img
                                src="../../../assets/matchpoint_logo_long_light.png"
                                alt="Match Logo"
                                className="auth-logo"
                            />
                        )
                    }
                    <h1 className="auth-title">{t('join_the_club')}</h1>
                    <p className="auth-subtitle">{t('find')}</p>
                </div>

                {error && (
                    <div className="error-banner fade-in-up delay-1">
                        <AlertCircle size={20} />
                        <span>{error}</span>
                    </div>
                )}

                <div className="input-group fade-in-up delay-1">
                    {/* EMAIL INPUT */}
                    <div className="input-wrapper">
                        <Mail className="input-icon" size={20} />
                        <input
                            type="email"
                            id="email"
                            name="email"
                            autoComplete="email"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            placeholder=" "
                            required
                            className="floating-input"
                        />
                        <label htmlFor="email" className="floating-label">{t('email')}</label>
                    </div>

                    {/* PASSWORD INPUT */}
                    <div className="input-wrapper">
                        <Lock className="input-icon" size={20} />
                        <input
                            type={showPassword ? "text" : "password"}
                            id="password"
                            name="email"
                            autoComplete="new-password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder=" "
                            required
                            minLength={6}
                            className="floating-input"
                        />
                        <label htmlFor="password" className="floating-label">{t('password')}</label>

                        <button
                            type="button"
                            className="password-toggle-btn"
                            onClick={() => setShowPassword(!showPassword)}
                            tabIndex={-1}
                        >
                            {showPassword ? <EyeOff size={20} /> : <Eye size={20} />}
                        </button>
                    </div>
                </div>

                <button type="submit" disabled={isLoading || !email || !password} className="primary-btn fade-in-up delay-2">
                    {isLoading ? <Loader2 className="spinner" size={24} /> : t('create_account').toUpperCase()}
                </button>

                <div className="divider-container fade-in-up delay-2">
                    <div className="divider-line"></div>
                    <span className="divider-text">{t('or')}</span>
                    <div className="divider-line"></div>
                </div>

                <button type="button" onClick={() => registerWithGoogle()} className="google-btn fade-in-up delay-2">
                    <svg
                        className="google-btn-icon"
                        xmlns="http://www.w3.org/2000/svg"
                        viewBox="0 0 48 48"
                        width="20px"
                        height="20px"
                    >
                        <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
                        <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
                        <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
                        <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
                    </svg>
                    <span className="google-btn-text">{t('continue_with_google')}</span>
                </button>

                <div className="auth-prompt fade-in-up delay-3">
                    <span className="auth-prompt-text">{t('already_have_an_account')}</span>
                    <button
                        type="button"
                        className="auth-link-btn"
                        onClick={() => navigate('/login')}
                    >
                        {t('login')}
                    </button>
                </div>

            </form>
        </div>
    );
}
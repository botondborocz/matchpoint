import { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom';
import SideBar from './components/Navigation/SideBar';
import TopBar from './components/Navigation/TopBar';
import LoginScreen from './components/Screens/LoginScreen'; 
import ProfileScreen from './components/Screens/ProfileScreen'; 
import MapScreen from './components/Screens/MapScreen';
import { SharedRes } from './shared/SharedRes';
import { SharedTheme } from './theme/SharedTheme';
import { ThemeProvider } from './theme/ThemeContext'; // 👈 Import the ThemeProvider we made!
import './App.css';
import MatchScreen from './components/Screens/MatchScreen';
import RegisterScreen from './components/Screens/RegisterScreen';
import { GoogleOAuthProvider } from '@react-oauth/google';
import ChatDetail from './components/Screens/ChatDetail';
import ChatDetailScreen from './components/Screens/ChatDetail';
import MessagesScreen from './components/Screens/MessagesScreen';

function AppContent() {
  // 1. Theme Injection
  useEffect(() => {
    const root = document.documentElement;
    // Iterates over the TS object and creates vars like --hexBackground: #0F172A
    Object.entries(SharedTheme).forEach(([key, value]) => {
      root.style.setProperty(`--${key}`, value as string);
    });
  }, []);

  const [isMobile, setIsMobile] = useState(window.innerWidth <= 768);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isLoggedIn, setIsLoggedIn] = useState(() => !!localStorage.getItem("auth_token"));
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  // 2. NEW: Listen for window resize events instantly!
  useEffect(() => {
    const handleResize = () => {
      setIsMobile(window.innerWidth <= 768);
      
      // Optional polish: Auto-close the mobile menu if they stretch the window to desktop size
      if (window.innerWidth > 768) {
        setIsMenuOpen(false);
      }
    };

    window.addEventListener('resize', handleResize);
    
    // Cleanup the listener when the app unmounts
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const handleLoginSuccess = (token: string) => {
    localStorage.setItem("auth_token", token);
    setIsLoggedIn(true);
    navigate("/profile", { replace: true });
  };

  const handleLogout = () => {
    localStorage.removeItem("auth_token");
    setIsLoggedIn(false);
    navigate("/login", { replace: true });
  };

  // Get title based on URL
  const getPageTitle = () => {
    const path = location.pathname.substring(1) || 'home';
    const titles: Record<string, string> = {
      home: SharedRes.strings.home,
      map: SharedRes.strings.map,
      coach: SharedRes.strings.aiCoach,
      match: SharedRes.strings.match,
      profile: SharedRes.strings.profile
    };
    return titles[path] || SharedRes.strings.appName;
  };

  return (
    <div className="app-container">
      <SideBar 
        isMobile={isMobile}
        isOpen={isMenuOpen}
        onClose={() => setIsMenuOpen(false)}
        isCollapsed={isSidebarCollapsed}
        onToggleCollapse={() => setIsSidebarCollapsed(!isSidebarCollapsed)}
      />

      {isMobile && (
        <TopBar onMenuClick={() => setIsMenuOpen(true)} title={getPageTitle()} />
      )}

      <main className="main-content">
      <Routes>
          {/* Public Tabs */}
          {/* <Route path="/home" element={<HomeScreen />} /> */}
          <Route path="/map" element={<MapScreen />} />
          <Route path="/match" element={<MatchScreen />} />
          {/* <Route path="/messages" element={<ChatDetailScreen />} /> */}
          <Route path="/messages" element={<MessagesScreen />} />
        
          {/* The :chatId captures the ID from the URL! */}
          <Route path="/chat/:chatId" element={<ChatDetailScreen />} />
          
          {/* The Gateway Tab */}
          <Route 
            path="/profile" 
            element={
              isLoggedIn 
                ? <ProfileScreen onLogout={handleLogout} /> 
                // 👇 THE FIX: Instantly redirects the URL and renders the Auth screen!
                : <Navigate to="/login" replace /> 
            } 
          />
          
          {/* Authentication Screens */}
          <Route path="/register" element={<RegisterScreen onLoginSuccess={handleLoginSuccess} />} />
          <Route path="/login" element={<LoginScreen onLoginSuccess={handleLoginSuccess} />} />

          {/* Default Redirects */}
          <Route path="/" element={<Navigate to="/map" replace />} />
          <Route path="*" element={<Navigate to="/map" replace />} />
        </Routes>
      </main>
    </div>
  );
}

export default function App() {
  const clientId = import.meta.env.VITE_GOOGLE_OAUTH_CLIENT_ID;
  return (
    // 3. Wrap everything in the ThemeProvider so ProfileScreen can access it
    <GoogleOAuthProvider clientId={clientId || ""}>
      <ThemeProvider>
        <Router>
          <AppContent />
        </Router>
      </ThemeProvider>
    </GoogleOAuthProvider>
  );
}
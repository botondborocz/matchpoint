import React, { useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Home, Map, Brain, Flame, User as UserIcon, Settings, X,
  LucideIcon, Menu,
  LayoutList,
  Activity,
  Rss,
  Newspaper,
  MessageCircle,
  Zap
} from 'lucide-react'; // 👈 Added ChevronLeft and ChevronRight
import { SharedRes } from '../../shared/SharedRes';
import { useTranslation } from 'react-i18next';
import { LanguageToggle } from '../LanguageToggle';
import '../../App.css';
import { log } from 'console';
import { useTheme } from '../../theme/ThemeContext';

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
  isMobile: boolean;
  // 👇 Added new collapse props
  isCollapsed: boolean;
  onToggleCollapse: () => void;
}

interface NavItem {
  id: string;
  label: string;
  icon: LucideIcon;
  isPro?: boolean;
  notification?: boolean;
}

const SideBar: React.FC<SidebarProps> = ({ isOpen, onClose, isMobile, isCollapsed, onToggleCollapse }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  const { theme } = useTheme();

  const isDarkMode = useMemo(() => {
    if (theme === 'dark') return true;
    if (theme === 'light') return false;
    // If it's 'system', ask the browser/phone what the OS is currently set to!
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  }, [theme]);

  console.log(isDarkMode ? "Dark mode is active" : "Light mode is active");

  // Determine active tab from the URL (e.g., "/home" -> "home")
  const rawPath = location.pathname.substring(1) || 'map';

  // 👇 Force login and register routes to highlight the Profile tab
  const activeTab = ['login', 'register', 'profile'].includes(rawPath)
    ? 'profile'
    : rawPath.includes('chat') || rawPath.includes('messages')
    ? 'messages'
    : rawPath;

  console.log(activeTab);
  console.log(rawPath.includes('chat') ? 'messages' : rawPath);
  

  const menuItems: NavItem[] = [
    { id: 'map', label: t('map'), icon: Map },
    { id: 'match', label: t('match'), icon: Zap },
    { id: 'coach', label: t('ai_coach'), icon: Brain, isPro: true },
    // { id: 'home', label: t('home'), icon: Home },
    // { id: 'feed', label: t('feed'), icon: LayoutList },
    { id: 'messages', label: t('messages'), icon: MessageCircle },
    { id: 'profile', label: t('profile'), icon: UserIcon },
  ];

  const activeIndex = Math.max(0, menuItems.findIndex(item => item.id === activeTab));

  // 👇 Updated to apply the "collapsed" class on desktop
  const sidebarClass = isMobile
    ? `sidebar mobile-drawer ${isOpen ? 'open' : ''}`
    : `sidebar desktop ${isCollapsed ? 'collapsed' : ''}`;

  return (
    <>
      {isMobile && isOpen && (
        <div className="mobile-overlay" onClick={onClose} />
      )}

      <aside className={sidebarClass}>
        {isMobile && (
          <button className="close-btn" onClick={onClose}>
            <X size={24} color="#9CA3AF" />
          </button>
        )}

        <div className="logo-section">
          {/* Note: I changed the image path to use an absolute path so it works on any route */}
          {isDarkMode ? (
            <img src="/assets/matchpoint_logo_long_dark.png" alt="Match Logo" className="logo-image" />)
            : (
              <img src="/assets/matchpoint_logo_long_light.png" alt="Match Logo" className="logo-image" />
            )
          }

          {/* 👇 The new collapse toggle button (Only visible on Desktop) */}
          {!isMobile && (
            <button onClick={onToggleCollapse} className="collapse-btn">
              <Menu size={20} />
            </button>
          )}
        </div>

        <nav className="nav-column">
          <div
            className="active-indicator"
            style={{
              transform: `translateY(calc(${activeIndex} * 56px + 12px))`
            }}
          />

          {menuItems.map((item) => {
            const isActive = activeTab === item.id;
            return (
              <div
                key={item.id}
                onClick={() => {
                  navigate(`/${item.id}`);
                  if (isMobile) onClose();
                }}
                className={`nav-item ${isActive ? 'active' : ''}`}
              >
                <item.icon
                  size={20}
                  color={isActive ? "var(--accent-orange)" : "var(--text-secondary)"}
                />
                <span className="nav-text">{item.label}</span>
                {item.isPro && <span className="pro-badge">{SharedRes.strings.pro || 'PRO'}</span>}
              </div>
            );
          })}
        </nav>

        {/* 👇 Hide LanguageToggle smoothly when collapsed */}
        {/* <div style={{
          padding: '0 16px',
          opacity: isCollapsed ? 0 : 1,
          pointerEvents: isCollapsed ? 'none' : 'auto',
          transition: 'opacity 0.2s ease'
        }}>
          <LanguageToggle />
        </div>

        <div className="user-footer">
          <div className="avatar">JD</div>
          <div className="user-info">
            <div className="user-name">János Doe</div>
            <div className="user-role">ELO 1380</div>
          </div>
          {/* 👇 Hide Settings gear when collapsed so avatar stays centered }
          {!isCollapsed && <Settings size={18} color="var(--text-secondary)" />}
        </div> */}
      </aside>
    </>
  );
};

export default SideBar;
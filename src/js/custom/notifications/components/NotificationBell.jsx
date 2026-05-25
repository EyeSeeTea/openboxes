import React, { useEffect, useRef, useState } from 'react';

import NotificationDropdown from 'custom/notifications/components/NotificationDropdown';
import useNotifications from 'custom/notifications/hooks/useNotifications';
import { RiNotification3Line } from 'react-icons/ri';

import 'custom/notifications/styles/_bell.scss';

const NotificationBell = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('unread');
  const containerRef = useRef(null);
  const {
    notifications,
    unreadCount,
    markRead,
    markAllRead,
    loadMore,
    hasMore,
    loadingMore,
  } = useNotifications({ unreadOnly: activeTab === 'unread', open: isOpen, limit: 20 });

  useEffect(() => {
    if (!isOpen) return undefined;

    const handleOutsideClick = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleOutsideClick);
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick);
    };
  }, [isOpen]);

  const handleMarkRead = (id) => {
    markRead(id);
  };

  const handleMarkAllRead = () => {
    markAllRead();
  };

  return (
    <li className="nav-item notification-bell" ref={containerRef}>
      <button
        type="button"
        className="menu-icon"
        aria-label={`Notifications${unreadCount > 0 ? `, ${unreadCount} unread` : ''}`}
        aria-expanded={isOpen}
        aria-haspopup="dialog"
        onClick={() => setIsOpen((prev) => !prev)}
      >
        <RiNotification3Line />
        {unreadCount > 0 && (
          <span className="notification-badge" aria-hidden="true">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>
      {isOpen && (
        <NotificationDropdown
          notifications={notifications}
          unreadCount={unreadCount}
          activeTab={activeTab}
          onTabChange={setActiveTab}
          onMarkRead={handleMarkRead}
          onMarkAllRead={handleMarkAllRead}
          onLoadMore={loadMore}
          hasMore={hasMore}
          loadingMore={loadingMore}
        />
      )}
    </li>
  );
};

export default NotificationBell;

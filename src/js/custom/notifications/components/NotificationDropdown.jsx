import React, { useState } from 'react';

import NotificationModal from 'custom/notifications/components/NotificationModal';
import { formatDistanceToNow } from 'date-fns';
import PropTypes from 'prop-types';
import { useHistory } from 'react-router-dom';

import Translate from 'utils/Translate';

const isSameOriginPath = (url) => typeof url === 'string' && url.startsWith('/') && !url.startsWith('//');

const formatRelative = (value) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return formatDistanceToNow(date, { addSuffix: true });
};

const NotificationDropdown = ({
  notifications,
  unreadCount,
  activeTab,
  onTabChange,
  onMarkRead,
  onMarkAllRead,
  onLoadMore,
  hasMore,
  loadingMore,
  error,
}) => {
  const history = useHistory();
  const [selectedNotification, setSelectedNotification] = useState(null);

  const handleActivate = (notification) => {
    if (!notification.read) {
      onMarkRead(notification.id);
    }
    setSelectedNotification(notification);
  };

  const handleCloseModal = () => {
    setSelectedNotification(null);
  };

  const handleOpenLink = (linkUrl) => {
    if (isSameOriginPath(linkUrl)) {
      history.push(linkUrl);
    }
  };

  return (
    <div className="notification-dropdown" role="dialog" aria-label="Notifications">
      <div className="notification-dropdown__header">
        <h6><Translate id="notifications.bell.title" defaultMessage="Notifications" /></h6>
        {unreadCount > 0 && (
        <button
          type="button"
          className="notification-dropdown__mark-all"
          onClick={onMarkAllRead}
        >
          <Translate id="notifications.bell.markAllRead" defaultMessage="Mark all as read" />
        </button>
        )}
      </div>
      <div className="notification-dropdown__tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'unread'}
          className={`notification-dropdown__tab${activeTab === 'unread' ? ' notification-dropdown__tab--active' : ''}`}
          onClick={() => onTabChange('unread')}
        >
          <Translate id="notifications.bell.tabUnread" defaultMessage="Unread" />
          {unreadCount > 0 && (
          <span className="notification-dropdown__tab-count">{unreadCount > 99 ? '99+' : unreadCount}</span>
          )}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'all'}
          className={`notification-dropdown__tab${activeTab === 'all' ? ' notification-dropdown__tab--active' : ''}`}
          onClick={() => onTabChange('all')}
        >
          <Translate id="notifications.bell.tabAll" defaultMessage="All" />
        </button>
      </div>
      {error && notifications.length === 0 ? (
        <p className="notification-dropdown__empty">
          <Translate id="notifications.bell.error" defaultMessage="Could not load notifications" />
        </p>
      ) : notifications.length === 0 ? (
        <p className="notification-dropdown__empty">
          {activeTab === 'unread' ? (
            <Translate id="notifications.bell.emptyUnread" defaultMessage="No unread notifications" />
          ) : (
            <Translate id="notifications.bell.empty" defaultMessage="No notifications yet" />
          )}
        </p>
      ) : (
        <ul className="notification-dropdown__list">
          {notifications.map((notification) => (
            <li key={notification.id} className="notification-item__wrapper">
              <button
                type="button"
                className={`notification-item${notification.read ? '' : ' notification-item--unread'}`}
                onClick={() => handleActivate(notification)}
              >
                <div className="notification-item__content">
                  <div className="notification-item__title">{notification.title}</div>
                  <div className="notification-item__time">
                    {formatRelative(notification.createdAt)}
                  </div>
                </div>
                {!notification.read && (
                  <span className="unread-dot" aria-label="Unread" />
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
      {hasMore && (
        <button
          type="button"
          className="notification-dropdown__load-more"
          onClick={onLoadMore}
          disabled={loadingMore}
        >
          <Translate id="notifications.bell.loadMore" defaultMessage="Load more" />
        </button>
      )}
      <NotificationModal
        notification={selectedNotification}
        onClose={handleCloseModal}
        onOpenLink={handleOpenLink}
      />
    </div>
  );
};

NotificationDropdown.propTypes = {
  notifications: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.string.isRequired,
      title: PropTypes.string.isRequired,
      createdAt: PropTypes.string.isRequired,
      read: PropTypes.bool.isRequired,
      linkUrl: PropTypes.string,
    }),
  ).isRequired,
  unreadCount: PropTypes.number.isRequired,
  activeTab: PropTypes.oneOf(['unread', 'all']).isRequired,
  onTabChange: PropTypes.func.isRequired,
  onMarkRead: PropTypes.func.isRequired,
  onMarkAllRead: PropTypes.func.isRequired,
  onLoadMore: PropTypes.func.isRequired,
  hasMore: PropTypes.bool.isRequired,
  loadingMore: PropTypes.bool.isRequired,
  error: PropTypes.instanceOf(Error),
};

NotificationDropdown.defaultProps = {
  error: null,
};

export default NotificationDropdown;

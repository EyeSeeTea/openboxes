import React, { useState } from 'react';

import NotificationModal from 'custom/notifications/components/NotificationModal';
import { NOTIFICATION_INBOX_URL } from 'custom/notifications/constants';
import { formatRelative } from 'custom/notifications/utils/dateFilters';
import PropTypes from 'prop-types';
import { RiInboxLine, RiMailOpenLine } from 'react-icons/ri';
import { getTranslate } from 'react-localize-redux';
import { useSelector } from 'react-redux';

import Translate, { translateWithDefaultMessage } from 'utils/Translate';

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
  const [selectedNotification, setSelectedNotification] = useState(null);
  const translate = useSelector(
    (state) => translateWithDefaultMessage(getTranslate(state.localize)),
  );

  const handleActivate = (notification) => {
    if (!notification.read) {
      onMarkRead(notification.id);
    }
    setSelectedNotification(notification);
  };

  const handleCloseModal = () => {
    setSelectedNotification(null);
  };

  let emptyMessage = (
    <Translate id="react.notification.bell.empty" defaultMessage="No notifications yet" />
  );
  if (error) {
    emptyMessage = (
      <Translate id="react.notification.bell.error" defaultMessage="Could not load notifications" />
    );
  } else if (activeTab === 'unread') {
    emptyMessage = (
      <Translate id="react.notification.bell.emptyUnread" defaultMessage="No unread notifications" />
    );
  }

  return (
    <div className="notification-dropdown" role="dialog" aria-label="Notifications">
      <div className="notification-dropdown__header">
        <h6><Translate id="react.notification.bell.title" defaultMessage="Notifications" /></h6>
        <div className="notification-dropdown__header-actions">
          {unreadCount > 0 && (
            <button
              type="button"
              className="notification-dropdown__icon-btn"
              onClick={onMarkAllRead}
              title={translate('react.notification.bell.markAllRead', 'Mark all as read')}
              aria-label={translate('react.notification.bell.markAllRead', 'Mark all as read')}
            >
              <RiMailOpenLine />
            </button>
          )}
          <a
            href={NOTIFICATION_INBOX_URL}
            className="notification-dropdown__icon-btn"
            title={translate('react.notification.inbox.viewAll', 'View all notifications')}
            aria-label={translate('react.notification.inbox.viewAll', 'View all notifications')}
          >
            <RiInboxLine />
          </a>
        </div>
      </div>
      <div className="notification-dropdown__tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'unread'}
          className={`notification-dropdown__tab${activeTab === 'unread' ? ' notification-dropdown__tab--active' : ''}`}
          onClick={() => onTabChange('unread')}
        >
          <Translate id="react.notification.bell.tabUnread" defaultMessage="Unread" />
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
          <Translate id="react.notification.bell.tabAll" defaultMessage="All" />
        </button>
      </div>
      {notifications.length === 0 ? (
        <p className="notification-dropdown__empty">{emptyMessage}</p>
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
          <Translate id="react.notification.bell.loadMore" defaultMessage="Load more" />
        </button>
      )}
      <NotificationModal
        notification={selectedNotification}
        onClose={handleCloseModal}
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

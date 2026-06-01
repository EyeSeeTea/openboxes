import React, { useCallback, useMemo, useState } from 'react';

import buildNotificationInboxFilterFields, { buildEmptyFilterValues } from 'custom/notifications/components/NotificationInboxFilterFields';
import useNotificationInbox, { PAGE_SIZES } from 'custom/notifications/hooks/useNotificationInbox';
import useNotificationInboxFilters from 'custom/notifications/hooks/useNotificationInboxFilters';
import { formatRelative, toBeforeIso, toSinceIso } from 'custom/notifications/utils/dateFilters';
import PropTypes from 'prop-types';
import { RiMailLine, RiMailOpenLine } from 'react-icons/ri';
import { getTranslate } from 'react-localize-redux';
import { useSelector } from 'react-redux';

import TablePagination from 'components/DataTable/TablePagination';
import FilterForm from 'components/Filter/FilterForm';
import Translate, { translateWithDefaultMessage } from 'utils/Translate';

import 'custom/notifications/styles/_inbox.scss';

const READ_FILTER_TO_PARAM = { UNREAD: false, READ: true };

const NotificationRow = ({
  notification, isSelected, onClick,
}) => (
  <li className="notification-inbox__item-wrapper">
    <button
      type="button"
      className={[
        'notification-inbox__item',
        notification.read ? '' : 'notification-inbox__item--unread',
        isSelected ? 'notification-inbox__item--selected' : '',
      ].filter(Boolean).join(' ')}
      onClick={() => onClick(notification.id)}
    >
      <div className="notification-inbox__item-content">
        <div className="notification-inbox__item-title">{notification.title}</div>
        <div className="notification-inbox__item-meta">
          <span className="notification-inbox__item-type">{notification.type}</span>
          <span className="notification-inbox__item-time">
            {formatRelative(notification.createdAt)}
          </span>
        </div>
      </div>
      {!notification.read && <span className="notification-inbox__unread-dot" aria-label="Unread" />}
    </button>
  </li>
);

NotificationRow.propTypes = {
  notification: PropTypes.shape({
    id: PropTypes.string.isRequired,
    title: PropTypes.string.isRequired,
    type: PropTypes.string,
    createdAt: PropTypes.string.isRequired,
    read: PropTypes.bool.isRequired,
  }).isRequired,
  isSelected: PropTypes.bool.isRequired,
  onClick: PropTypes.func.isRequired,
};

const DetailPane = ({ notification, onMarkUnread }) => {
  if (!notification) {
    return (
      <div className="notification-inbox__detail notification-inbox__detail--empty">
        <p><Translate id="react.notification.inbox.detail.empty" defaultMessage="Select a notification to read it" /></p>
      </div>
    );
  }

  return (
    <div className="notification-inbox__detail">
      <div className="notification-inbox__detail-header">
        <div className="notification-inbox__detail-heading">
          <h5 className="notification-inbox__detail-title">{notification.title}</h5>
          <div className="notification-inbox__detail-meta">
            <span className="notification-inbox__detail-type">{notification.type}</span>
            <span className="notification-inbox__detail-time">
              {formatRelative(notification.createdAt)}
            </span>
          </div>
        </div>
        {notification.read && (
          <button
            type="button"
            className="notification-inbox__detail-action"
            onClick={() => onMarkUnread(notification.id)}
            title="Mark as unread"
            aria-label="Mark as unread"
          >
            <RiMailLine />
          </button>
        )}
      </div>
      <div className="notification-inbox__detail-body">
        {notification.body ? (
          // Reason: body is an email body (untrusted HTML). The sandbox has no
          // allow-scripts/allow-same-origin, so scripts and same-origin access are
          // blocked; base target="_blank" forces links into a real tab.
          <iframe
            title={notification.title}
            srcDoc={`<base target="_blank">${notification.body}`}
            sandbox="allow-popups allow-popups-to-escape-sandbox"
            className="notification-inbox__detail-frame"
          />
        ) : (
          <p className="notification-inbox__no-body">
            <Translate id="react.notification.modal.noBody" defaultMessage="No additional details" />
          </p>
        )}
      </div>
    </div>
  );
};

DetailPane.propTypes = {
  notification: PropTypes.shape({
    id: PropTypes.string.isRequired,
    title: PropTypes.string.isRequired,
    type: PropTypes.string,
    createdAt: PropTypes.string.isRequired,
    body: PropTypes.string,
    read: PropTypes.bool.isRequired,
  }),
  onMarkUnread: PropTypes.func.isRequired,
};

DetailPane.defaultProps = {
  notification: null,
};

const NotificationInboxFilters = ({ setFilterParams }) => {
  const { defaultValues, setFilterValues } = useNotificationInboxFilters({ setFilterParams });
  const translate = useSelector(
    (state) => translateWithDefaultMessage(getTranslate(state.localize)),
  );
  const filterFields = useMemo(() => buildNotificationInboxFilterFields(translate), [translate]);

  return (
    <div className="d-flex flex-column list-page-filters">
      <FilterForm
        filterFields={filterFields}
        updateFilterParams={(values) => setFilterValues({ ...values })}
        onClear={(form) => {
          form.reset(buildEmptyFilterValues());
          setFilterValues({});
        }}
        formProps={{}}
        defaultValues={defaultValues}
        hidden={false}
        showSearchField={false}
        allowEmptySubmit
      />
    </div>
  );
};

NotificationInboxFilters.propTypes = {
  setFilterParams: PropTypes.func.isRequired,
};

const NotificationInbox = () => {
  const [filterParams, setFilterParams] = useState({});

  const typeFilter = filterParams.type?.value || filterParams.type || undefined;
  const sinceFilter = toSinceIso(filterParams.since);
  const beforeFilter = toBeforeIso(filterParams.before);
  const readValue = filterParams.read?.value || filterParams.read;
  const readFilter = READ_FILTER_TO_PARAM[readValue];

  const {
    notifications,
    loading,
    error,
    selectedId,
    selectedNotification,
    selectNotification,
    markUnread,
    markAllRead,
    unreadCount,
    page,
    pageSize,
    totalCount,
    totalPages,
    canNext,
    canPrevious,
    setPage,
    setPageSize,
  } = useNotificationInbox({
    type: typeFilter,
    since: sinceFilter,
    before: beforeFilter,
    read: readFilter,
  });

  const handleSetFilterParams = useCallback((values) => {
    setFilterParams(values);
  }, []);

  return (
    <div className="d-flex flex-column list-page-main notification-inbox">
      <div className="d-flex align-items-center justify-content-between list-page-header">
        <h4 className="title"><Translate id="react.notification.inbox.title" defaultMessage="Notification Inbox" /></h4>
        {unreadCount > 0 && (
          <button
            type="button"
            className="notification-inbox__mark-all-btn"
            onClick={markAllRead}
            title="Mark all as read"
            aria-label="Mark all as read"
          >
            <RiMailOpenLine />
            <span className="notification-inbox__mark-all-label">
              <Translate id="react.notification.bell.markAllRead" defaultMessage="Mark all as read" />
            </span>
          </button>
        )}
      </div>

      <NotificationInboxFilters setFilterParams={handleSetFilterParams} />

      <div className="notification-inbox__content list-page-list-section" style={{ marginBottom: 0 }}>
        <div className="notification-inbox__layout">
          <div className="notification-inbox__list-panel">
            {loading && notifications.length === 0 && (
              <div className="notification-inbox__loading">
                <Translate id="react.notification.inbox.loading" defaultMessage="Loading…" />
              </div>
            )}
            {!loading && error && notifications.length === 0 && (
              <div className="notification-inbox__error">
                <Translate id="react.notification.inbox.error" defaultMessage="Could not load notifications" />
              </div>
            )}
            {!loading && notifications.length === 0 && !error && (
              <div className="notification-inbox__empty">
                <Translate id="react.notification.inbox.empty" defaultMessage="No notifications found" />
              </div>
            )}
            {notifications.length > 0 && (
              <ul className="notification-inbox__list">
                {notifications.map((n) => (
                  <NotificationRow
                    key={n.id}
                    notification={n}
                    isSelected={n.id === selectedId}
                    onClick={selectNotification}
                  />
                ))}
              </ul>
            )}
          </div>

          <div className="notification-inbox__detail-panel">
            <DetailPane
              notification={selectedNotification}
              onMarkUnread={markUnread}
            />
          </div>
        </div>

        {totalCount > 0 && (
          <div className="notification-inbox__pagination">
            <TablePagination
              page={page}
              pages={totalPages}
              pageSize={pageSize}
              resolvedData={[]}
              pageSizeOptions={PAGE_SIZES}
              onPageChange={setPage}
              onPageSizeChange={setPageSize}
              canNext={canNext}
              canPrevious={canPrevious}
              totalData={totalCount}
            />
          </div>
        )}
      </div>
    </div>
  );
};

export default NotificationInbox;

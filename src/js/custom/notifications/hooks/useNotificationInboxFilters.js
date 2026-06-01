import { useCallback, useEffect, useState } from 'react';

import {
  buildEmptyFilterValues,
  NOTIFICATION_TYPE_VALUES,
  READ_STATUS_VALUES,
} from 'custom/notifications/components/NotificationInboxFilterFields';
import queryString from 'query-string';
import { useHistory } from 'react-router-dom';

const toOption = (value) => ({ id: value, value, label: value });

const useNotificationInboxFilters = ({ setFilterParams }) => {
  const [defaultValues, setDefaultValues] = useState({});
  const [filtersInitialized, setFiltersInitialized] = useState(false);
  const history = useHistory();

  const initializeDefaultFilterValues = useCallback(() => {
    const initialEmptyValues = buildEmptyFilterValues();
    const queryProps = queryString.parse(history.location.search);

    if (queryProps.type && NOTIFICATION_TYPE_VALUES.includes(queryProps.type)) {
      initialEmptyValues.type = toOption(queryProps.type);
    }
    if (queryProps.read && READ_STATUS_VALUES.includes(queryProps.read)) {
      initialEmptyValues.read = toOption(queryProps.read);
    }
    if (queryProps.since) {
      initialEmptyValues.since = queryProps.since;
    }
    if (queryProps.before) {
      initialEmptyValues.before = queryProps.before;
    }

    setDefaultValues(initialEmptyValues);
    setFiltersInitialized(true);
  }, [history.location.search]);

  useEffect(() => {
    if (!filtersInitialized) {
      initializeDefaultFilterValues();
    }
  }, [filtersInitialized, initializeDefaultFilterValues]);

  // Re-initialize when location changes (e.g. "Clear filters" navigates to plain pathname)
  useEffect(() => {
    setFiltersInitialized(false);
  }, [history.location.pathname]);

  const setFilterValues = useCallback((values) => {
    const params = {};
    if (values.type) params.type = values.type.value || values.type;
    if (values.read) params.read = values.read.value || values.read;
    if (values.since) params.since = values.since;
    if (values.before) params.before = values.before;

    const { pathname } = history.location;
    history.push({ pathname, search: queryString.stringify(params) });
    setFilterParams(values);
  }, [history, setFilterParams]);

  return { defaultValues, setFilterValues };
};

export default useNotificationInboxFilters;

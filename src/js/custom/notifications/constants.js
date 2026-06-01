const CONTEXT_PATH = (typeof window !== 'undefined' && window.CONTEXT_PATH) ? window.CONTEXT_PATH : '/openboxes';

// eslint-disable-next-line import/prefer-default-export
export const NOTIFICATION_INBOX_URL = `${CONTEXT_PATH}/notification/inbox`;

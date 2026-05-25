<%--
  Notification bell partial for GSP-rendered pages (React-shell pages mount
  a separate React bell via src/js/custom/notifications/components/NotificationBell.jsx).
  Both surfaces hit the same /api/custom/notifications endpoint so the
  unread count stays consistent.

  Designed to be dropped inside a `.navbar-icons` container — it inherits
  the `.menu-icon` sizing (40px/22px) from HeaderStyles.scss so the icon
  matches the search / help / gear / profile icons that sit next to it.
--%>
<div class="menu-icon position-relative custom-notification-bell"
     data-testid="navbar-icon"
     aria-label="${message(code: 'notifications.bell.title', default: 'Notifications')}"
     data-list-url="${createLink(uri: '/api/custom/notifications')}"
     data-count-url="${createLink(uri: '/api/custom/notifications/unread-count')}"
     data-mark-all-url="${createLink(uri: '/api/custom/notifications/read-all')}"
     data-mark-read-url-template="${createLink(uri: '/api/custom/notifications/__ID__/read')}">
    <div class="tooltip2">
        <i class="ri-notification-3-line custom-notification-bell__icon"></i>
        <span class="tooltiptext2">
            ${message(code: 'notifications.bell.title', default: 'Notifications')}
        </span>
    </div>
    <span class="custom-notification-bell__badge" hidden></span>
    <div class="custom-notification-bell__dropdown" role="dialog" hidden
         aria-label="${message(code: 'notifications.bell.title', default: 'Notifications')}">
        <div class="custom-notification-bell__header">
            <strong>${message(code: 'notifications.bell.title', default: 'Notifications')}</strong>
            <button type="button" class="custom-notification-bell__mark-all" hidden>
                ${message(code: 'notifications.bell.markAllRead', default: 'Mark all as read')}
            </button>
        </div>
        <div class="custom-notification-bell__tabs" role="tablist">
            <button type="button" role="tab"
                    class="custom-notification-bell__tab custom-notification-bell__tab--active"
                    data-tab="unread" aria-selected="true">
                ${message(code: 'notifications.bell.tabUnread', default: 'Unread')}
            </button>
            <button type="button" role="tab"
                    class="custom-notification-bell__tab"
                    data-tab="all" aria-selected="false">
                ${message(code: 'notifications.bell.tabAll', default: 'All')}
            </button>
        </div>
        <ul class="custom-notification-bell__list"></ul>
        <button type="button" class="custom-notification-bell__load-more" hidden>
            ${message(code: 'notifications.bell.loadMore', default: 'Load more')}
        </button>
        <p class="custom-notification-bell__empty"
           data-empty-unread="${message(code: 'notifications.bell.emptyUnread', default: 'No unread notifications')}"
           data-empty-all="${message(code: 'notifications.bell.empty', default: 'No notifications yet')}">
            ${message(code: 'notifications.bell.emptyUnread', default: 'No unread notifications')}
        </p>
    </div>
    <div class="custom-notification-bell__modal" role="dialog" aria-modal="true" hidden
         data-no-body="${message(code: 'notifications.modal.noBody', default: 'No additional details')}">
        <div class="custom-notification-bell__modal-backdrop"></div>
        <div class="custom-notification-bell__modal-dialog">
            <div class="custom-notification-bell__modal-header">
                <strong class="custom-notification-bell__modal-title"></strong>
                <button type="button" class="custom-notification-bell__modal-close"
                        aria-label="${message(code: 'notifications.modal.close', default: 'Close')}">
                    ${message(code: 'notifications.modal.close', default: 'Close')}
                </button>
            </div>
            <div class="custom-notification-bell__modal-body"></div>
            <div class="custom-notification-bell__modal-footer" hidden>
                <button type="button" class="custom-notification-bell__modal-open-link">
                    ${message(code: 'notifications.modal.openLink', default: 'Open link')}
                </button>
            </div>
        </div>
    </div>
</div>

<style>
.custom-notification-bell { cursor: pointer; }
.custom-notification-bell__badge { position: absolute; top: 2px; right: 2px; min-width: 16px;
    height: 16px; padding: 0 4px; border-radius: 8px; background: #dc3545; color: #fff;
    font-size: 10px; font-weight: 700; line-height: 16px; text-align: center; pointer-events: none; }
.custom-notification-bell__dropdown { position: absolute; top: calc(100% + 4px); right: 0;
    width: 320px; background: #fff; border: 1px solid rgba(0,0,0,0.15); border-radius: 4px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.15); z-index: 1050; overflow: hidden;
    font-size: 13px; text-align: left; color: #212529; }
.custom-notification-bell__header { display: flex; align-items: center; justify-content: space-between;
    padding: 10px 14px; border-bottom: 1px solid #e9ecef; background: #f8f9fa; }
.custom-notification-bell__mark-all { font-size: 12px; color: #007bff; background: none;
    border: none; padding: 0; cursor: pointer; }
.custom-notification-bell__mark-all:hover { text-decoration: underline; }
.custom-notification-bell__tabs { display: flex; border-bottom: 1px solid #e9ecef; }
.custom-notification-bell__tab { flex: 1; padding: 8px 0; font-size: 12px; font-weight: 600;
    color: #6c757d; background: none; border: none; border-bottom: 2px solid transparent;
    cursor: pointer; }
.custom-notification-bell__tab:hover { color: #212529; }
.custom-notification-bell__tab--active { color: #007bff; border-bottom-color: #007bff; }
.custom-notification-bell__list { list-style: none; margin: 0; padding: 0; max-height: 360px;
    overflow-y: auto; }
.custom-notification-bell__empty { padding: 24px 14px; text-align: center; color: #6c757d;
    font-size: 13px; margin: 0; }
.custom-notification-bell__load-more { display: block; width: 100%; padding: 8px 0;
    font-size: 12px; font-weight: 600; color: #007bff; background: #f8f9fa; border: none;
    border-top: 1px solid #e9ecef; cursor: pointer; }
.custom-notification-bell__load-more:hover { background: #eef5ff; }
.custom-notification-bell__load-more:disabled { color: #6c757d; cursor: default; }
.custom-notification-bell__item { padding: 10px 14px; cursor: pointer;
    border-bottom: 1px solid #f0f0f0; background: #eef5ff; }
.custom-notification-bell__item:last-child { border-bottom: none; }
.custom-notification-bell__item:hover { background: #dceeff; }
.custom-notification-bell__item--read { background: #fff; }
.custom-notification-bell__item--read:hover { background: #f5f5f5; }
.custom-notification-bell__item-time { font-size: 11px; color: #6c757d; margin-top: 2px; }
.custom-notification-bell__modal { position: fixed; inset: 0; z-index: 2000; display: flex;
    align-items: center; justify-content: center; }
.custom-notification-bell__modal-backdrop { position: absolute; inset: 0;
    background: rgba(0,0,0,0.45); }
.custom-notification-bell__modal-dialog { position: relative; z-index: 1; width: 100%;
    max-width: 520px; max-height: 70vh; display: flex; flex-direction: column; background: #fff;
    border-radius: 4px; box-shadow: 0 8px 24px rgba(0,0,0,0.18); overflow: hidden;
    text-align: left; color: #212529; }
.custom-notification-bell__modal-header { display: flex; align-items: flex-start;
    justify-content: space-between; padding: 14px 16px 10px; border-bottom: 1px solid #e9ecef;
    background: #f8f9fa; }
.custom-notification-bell__modal-title { flex: 1; padding-right: 12px; font-size: 14px;
    font-weight: 600; }
.custom-notification-bell__modal-close { flex-shrink: 0; padding: 0 4px; background: none;
    border: none; cursor: pointer; font-size: 13px; color: #6c757d; }
.custom-notification-bell__modal-close:hover { color: #212529; text-decoration: underline; }
.custom-notification-bell__modal-body { flex: 1; overflow-y: auto; }
.custom-notification-bell__modal-body iframe { width: 100%; min-height: 320px; border: none; }
.custom-notification-bell__modal-no-body { padding: 16px; margin: 0; color: #6c757d;
    font-style: italic; font-size: 13px; }
.custom-notification-bell__modal-footer { display: flex; justify-content: flex-end;
    padding: 10px 16px; border-top: 1px solid #e9ecef; background: #f8f9fa; }
.custom-notification-bell__modal-open-link { padding: 6px 16px; font-size: 13px; font-weight: 500;
    color: #fff; background: #007bff; border: none; border-radius: 4px; cursor: pointer; }
.custom-notification-bell__modal-open-link:hover { background: #0069d9; }
</style>

<script>
(function() {
    var bells = document.getElementsByClassName('custom-notification-bell');
    var root = bells[bells.length - 1];
    if (!root || root.dataset.customNotificationBellInit) return;
    root.dataset.customNotificationBellInit = '1';

    var listUrl = root.getAttribute('data-list-url');
    var countUrl = root.getAttribute('data-count-url');
    var markAllUrl = root.getAttribute('data-mark-all-url');
    var markReadUrlTemplate = root.getAttribute('data-mark-read-url-template');
    var badge = root.querySelector('.custom-notification-bell__badge');
    var dropdown = root.querySelector('.custom-notification-bell__dropdown');
    var list = root.querySelector('.custom-notification-bell__list');
    var empty = root.querySelector('.custom-notification-bell__empty');
    var loadMore = root.querySelector('.custom-notification-bell__load-more');
    var tabs = root.querySelectorAll('.custom-notification-bell__tab');
    var markAll = root.querySelector('.custom-notification-bell__mark-all');
    var modal = root.querySelector('.custom-notification-bell__modal');
    var modalBackdrop = root.querySelector('.custom-notification-bell__modal-backdrop');
    var modalTitle = root.querySelector('.custom-notification-bell__modal-title');
    var modalBody = root.querySelector('.custom-notification-bell__modal-body');
    var modalClose = root.querySelector('.custom-notification-bell__modal-close');
    var modalFooter = root.querySelector('.custom-notification-bell__modal-footer');
    var modalOpenLink = root.querySelector('.custom-notification-bell__modal-open-link');
    var noBodyText = modal.getAttribute('data-no-body');
    var emptyUnreadText = empty.getAttribute('data-empty-unread');
    var emptyAllText = empty.getAttribute('data-empty-all');
    var open = false;
    var activeTab = 'unread';
    var pageSize = 20;
    var hasMore = false;
    var loadingMore = false;

    function setOpen(next) {
        open = next;
        dropdown.hidden = !open;
        root.setAttribute('aria-expanded', open ? 'true' : 'false');
        if (open) fetchPage();
    }
    function isSameOriginPath(url) {
        return typeof url === 'string' && url.indexOf('/') === 0 && url.indexOf('//') !== 0;
    }
    function closeModal() {
        modal.hidden = true;
        modalBody.innerHTML = '';
    }
    function openModal(n) {
        modalTitle.textContent = n.title || '';
        modalBody.innerHTML = '';
        if (n.body && n.body.trim()) {
            var frame = document.createElement('iframe');
            // allow-popups lets links escape the sessionless frame; base target="_blank"
            // forces them into a real browser tab instead of navigating inside the iframe.
            frame.setAttribute('sandbox', 'allow-popups allow-popups-to-escape-sandbox');
            frame.setAttribute('title', n.title || '');
            frame.setAttribute('srcdoc', '<base target="_blank">' + n.body);
            modalBody.appendChild(frame);
        } else {
            var p = document.createElement('p');
            p.className = 'custom-notification-bell__modal-no-body';
            p.textContent = noBodyText;
            modalBody.appendChild(p);
        }
        if (isSameOriginPath(n.linkUrl)) {
            modalFooter.hidden = false;
            modalOpenLink.onclick = function() { window.location.href = n.linkUrl; };
        } else {
            modalFooter.hidden = true;
            modalOpenLink.onclick = null;
        }
        modal.hidden = false;
    }
    function relativeTime(iso) {
        if (!iso) return '';
        var s = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
        if (s < 60) return s + 's ago';
        var m = Math.round(s / 60);
        if (m < 60) return m + 'm ago';
        var h = Math.round(m / 60);
        if (h < 24) return h + 'h ago';
        return Math.round(h / 24) + 'd ago';
    }
    function pageUrl(offset) {
        return listUrl
            + '?unreadOnly=' + (activeTab === 'all' ? 'false' : 'true')
            + '&offset=' + offset
            + '&limit=' + pageSize;
    }
    function setBadge(count) {
        if (count > 0) {
            badge.textContent = count > 99 ? '99+' : String(count);
            badge.hidden = false;
            markAll.hidden = false;
        } else {
            badge.hidden = true;
            markAll.hidden = true;
        }
    }
    function currentBadgeCount() {
        if (badge.hidden) return 0;
        var n = parseInt(badge.textContent, 10);
        return isNaN(n) ? 0 : n;
    }
    function updateLoadMore() {
        loadMore.hidden = !hasMore;
    }
    function appendItems(items) {
        if (items.length) empty.hidden = true;
        items.forEach(function(n) {
            var li = document.createElement('li');
            li.className = 'custom-notification-bell__item'
                + (n.read ? ' custom-notification-bell__item--read' : '');
            li.setAttribute('data-id', n.id);
            var titleDiv = document.createElement('div');
            titleDiv.textContent = n.title || '';
            var timeDiv = document.createElement('div');
            timeDiv.className = 'custom-notification-bell__item-time';
            timeDiv.textContent = relativeTime(n.createdAt);
            li.appendChild(titleDiv);
            li.appendChild(timeDiv);
            li.addEventListener('click', function() {
                if (!n.read) {
                    n.read = true;
                    markRead(n.id, li);
                }
                openModal(n);
            });
            list.appendChild(li);
        });
    }
    function pollCount() {
        fetch(countUrl, { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function(r) { return r.ok ? r.json() : null; })
            .then(function(body) {
                if (!body || typeof body.unreadCount !== 'number') return;
                setBadge(body.unreadCount);
            })
            .catch(function() { /* swallow; next poll retries */ });
    }
    function fetchPage() {
        list.innerHTML = '';
        empty.hidden = true;
        hasMore = false;
        updateLoadMore();
        fetch(pageUrl(0), { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function(r) { return r.ok ? r.json() : null; })
            .then(function(body) {
                if (!body) return;
                var items = Array.isArray(body) ? body : (body.data || []);
                if (!Array.isArray(body) && typeof body.unreadCount === 'number') {
                    setBadge(body.unreadCount);
                }
                if (!items.length) {
                    empty.textContent = activeTab === 'all' ? emptyAllText : emptyUnreadText;
                    empty.hidden = false;
                } else {
                    appendItems(items);
                }
                hasMore = items.length === pageSize;
                updateLoadMore();
            })
            .catch(function() { /* swallow; user can reopen to retry */ });
    }
    function loadMore_() {
        if (loadingMore) return;
        loadingMore = true;
        loadMore.disabled = true;
        var offset = list.children.length;
        fetch(pageUrl(offset), { credentials: 'same-origin', headers: { 'Accept': 'application/json' } })
            .then(function(r) { return r.ok ? r.json() : null; })
            .then(function(body) {
                if (!body) return;
                var items = Array.isArray(body) ? body : (body.data || []);
                appendItems(items);
                hasMore = items.length === pageSize;
                updateLoadMore();
            })
            .catch(function() { /* swallow; user can retry */ })
            .then(function() {
                loadingMore = false;
                loadMore.disabled = false;
            });
    }
    function markRead(id, li) {
        if (li) li.className = 'custom-notification-bell__item custom-notification-bell__item--read';
        setBadge(Math.max(0, currentBadgeCount() - 1));
        var url = markReadUrlTemplate.replace('__ID__', encodeURIComponent(id));
        fetch(url, { method: 'PUT', credentials: 'same-origin' })
            .catch(function() { /* swallow; next badge poll reconciles */ });
    }
    function markAllRead() {
        var items = list.children;
        for (var i = 0; i < items.length; i++) {
            items[i].className = 'custom-notification-bell__item custom-notification-bell__item--read';
        }
        setBadge(0);
        fetch(markAllUrl, { method: 'PUT', credentials: 'same-origin' })
            .catch(function() { /* swallow; next badge poll reconciles */ });
    }

    root.addEventListener('click', function(e) {
        if (e.target.closest('.custom-notification-bell__dropdown')) return;
        if (e.target.closest('.custom-notification-bell__modal')) return;
        e.stopPropagation();
        setOpen(!open);
    });
    function setActiveTab(tab) {
        if (tab === activeTab) return;
        activeTab = tab;
        hasMore = false;
        updateLoadMore();
        for (var i = 0; i < tabs.length; i++) {
            var selected = tabs[i].getAttribute('data-tab') === tab;
            tabs[i].setAttribute('aria-selected', selected ? 'true' : 'false');
            if (selected) {
                tabs[i].className = 'custom-notification-bell__tab custom-notification-bell__tab--active';
            } else {
                tabs[i].className = 'custom-notification-bell__tab';
            }
        }
        fetchPage();
    }
    for (var t = 0; t < tabs.length; t++) {
        tabs[t].addEventListener('click', function(e) {
            e.stopPropagation();
            setActiveTab(this.getAttribute('data-tab'));
        });
    }
    loadMore.addEventListener('click', function(e) { e.stopPropagation(); loadMore_(); });
    markAll.addEventListener('click', function(e) { e.stopPropagation(); markAllRead(); });
    modalClose.addEventListener('click', function(e) { e.stopPropagation(); closeModal(); });
    modalBackdrop.addEventListener('click', function(e) { e.stopPropagation(); closeModal(); });
    document.addEventListener('keydown', function(e) {
        if (!modal.hidden && e.key === 'Escape') closeModal();
    });
    document.addEventListener('mousedown', function(e) {
        if (open && !root.contains(e.target)) setOpen(false);
    });

    pollCount();
    setInterval(pollCount, 30000);
})();
</script>

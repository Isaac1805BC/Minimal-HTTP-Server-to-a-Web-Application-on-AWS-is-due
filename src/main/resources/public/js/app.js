/*
 * Client script served by the Java server as text/javascript.
 *
 * At this point of the laboratory it only proves that the script was fetched
 * and executed as a separate HTTP request. The asynchronous calls to the
 * hardcoded services are added in a later step.
 */
(function () {
  'use strict';

  document.addEventListener('DOMContentLoaded', function () {
    var badges = document.querySelector('.badges');
    if (!badges) {
      return;
    }
    var badge = document.createElement('span');
    badge.className = 'badge live';
    badge.textContent = 'script loaded';
    badges.appendChild(badge);
    console.log('[app] /js/app.js executed; the browser requested it separately from the page.');
  });
})();

/*
 * Asynchronous client of the mini application.
 *
 * Served by the Java server as text/javascript, on its own HTTP request. Its
 * job is to call the hardcoded services without ever reloading the page:
 * validate the input, build the URL, send the request, keep the interface
 * usable while the answer travels, check the HTTP status before reading the
 * body, and update only the affected part of the document.
 *
 * A network failure (the server is down, the connection dropped) and a valid
 * HTTP error response (400, 404, 405) are two different situations and are
 * reported differently.
 */
(function () {
  'use strict';

  /** A request that never answers must not leave the button disabled forever. */
  var REQUEST_TIMEOUT_MILLIS = 20000;

  var elements = {};

  document.addEventListener('DOMContentLoaded', function () {
    elements = {
      result: document.getElementById('result'),
      resultValue: document.getElementById('resultValue'),
      resultMeta: document.getElementById('resultMeta'),
      resultPayload: document.getElementById('resultPayload'),
      error: document.getElementById('error'),
      errorMessage: document.getElementById('errorMessage'),
      errorMeta: document.getElementById('errorMeta'),
      healthBadge: document.getElementById('healthBadge')
    };

    document.getElementById('greetingForm').addEventListener('submit', onGreeting);
    document.getElementById('squareForm').addEventListener('submit', onSquare);
    document.getElementById('timeForm').addEventListener('submit', onServerTime);
    document.getElementById('slowForm').addEventListener('submit', onSlow);
    document.getElementById('browserClockButton').addEventListener('click', onBrowserClock);

    checkHealth();
  });

  /* ----------------------------------------------------------------------
   * Actions
   * -------------------------------------------------------------------- */

  function onGreeting(event) {
    // Without this the browser would submit the form and reload the page.
    event.preventDefault();

    var name = document.getElementById('nameInput').value.trim();
    if (name === '') {
      showError('Type a name before asking for a greeting.', 'Validated in the browser; no request was sent.');
      return;
    }

    // encodeURIComponent keeps spaces, accents and '&' from breaking the query string.
    var url = '/app/hello?name=' + encodeURIComponent(name);
    call(url, 'greetingButton', 'greetingStatus', function (data, meta) {
      showResult(data.message, meta, data);
    });
  }

  function onSquare(event) {
    event.preventDefault();

    var raw = document.getElementById('valueInput').value.trim();
    if (raw === '') {
      showError('Type a number before asking for its square.', 'Validated in the browser; no request was sent.');
      return;
    }
    if (!isFinite(Number(raw))) {
      showError('"' + raw + '" is not a number.', 'Validated in the browser; the server would also reject it with 400.');
      return;
    }

    var url = '/app/square?value=' + encodeURIComponent(raw);
    call(url, 'squareButton', 'squareStatus', function (data, meta) {
      showResult(data.value + ' squared is ' + data.square, meta, data);
    });
  }

  function onServerTime(event) {
    event.preventDefault();
    call('/app/time', 'timeButton', 'timeStatus', function (data, meta) {
      showResult(data.readable + ' (' + data.zone + ')',
        meta + ' · browser clock: ' + new Date().toLocaleTimeString(), data);
    });
  }

  function onSlow(event) {
    event.preventDefault();
    call('/app/slow?ms=5000', 'slowButton', 'slowStatus', function (data, meta) {
      showResult('The server was busy for ' + data.actualDelayMillis + " ms",
        meta + ' · nothing else was served during that time', data);
    });
  }

  /** Local comparison only: no request is sent, the value comes from this machine. */
  function onBrowserClock(event) {
    event.preventDefault();
    showResult(new Date().toLocaleString(),
      'browser clock · no HTTP request was made', null);
  }

  /** Runs once when the page loads and turns the header badge into a health indicator. */
  function checkHealth() {
    request('/app/health').then(function (outcome) {
      elements.healthBadge.textContent = 'server ' + outcome.data.status.toLowerCase()
        + ' · up ' + outcome.data.uptime;
      elements.healthBadge.className = 'badge live';
    }).catch(function (failure) {
      elements.healthBadge.textContent = 'server unreachable';
      elements.healthBadge.className = 'badge down';
      console.warn('[app] health check failed:', failure.message);
    });
  }

  /* ----------------------------------------------------------------------
   * Request plumbing
   * -------------------------------------------------------------------- */

  /**
   * Sends the request while keeping the page usable: the button is disabled and
   * a loading state appears, but nothing blocks and the rest of the interface
   * still reacts to the user.
   */
  function call(url, buttonId, statusId, onSuccess) {
    var button = document.getElementById(buttonId);
    var status = document.getElementById(statusId);
    var startedAt = Date.now();

    button.disabled = true;
    status.innerHTML = '<span class="spinner"></span> waiting for ' + escapeHtml(url) + '&hellip;';

    request(url).then(function (outcome) {
      var elapsed = Date.now() - startedAt;
      var meta = 'GET ' + url + ' → ' + outcome.status + ' ' + outcome.contentType + ' · ' + elapsed + ' ms';
      status.textContent = 'answered in ' + elapsed + ' ms';
      onSuccess(outcome.data, meta);
    }).catch(function (failure) {
      var elapsed = Date.now() - startedAt;
      status.textContent = failure.kind === 'network' ? 'the server did not answer' : 'rejected by the server';
      showError(failure.message,
        (failure.status ? 'GET ' + url + ' → ' + failure.status + ' · ' : 'GET ' + url + ' · ')
        + failure.kind + ' failure · ' + elapsed + ' ms');
    }).then(function () {
      button.disabled = false;
    });
  }

  /**
   * One asynchronous HTTP call.
   *
   * Rejects with {kind:'network'} when the request never produced a response,
   * and with {kind:'http', status:N} when the server answered with an error
   * status. The status is always checked before the body is interpreted.
   */
  function request(url) {
    var controller = new AbortController();
    var timeout = setTimeout(function () { controller.abort(); }, REQUEST_TIMEOUT_MILLIS);

    return fetch(url, {
      method: 'GET',
      headers: { 'Accept': 'application/json' },
      signal: controller.signal
    }).catch(function (networkFailure) {
      throw {
        kind: 'network',
        message: networkFailure.name === 'AbortError'
          ? 'The server took too long to answer.'
          : 'The server could not be reached. Check that it is running.'
      };
    }).then(function (response) {
      var contentType = response.headers.get('Content-Type') || 'unknown';

      return response.text().then(function (body) {
        var data = null;
        if (contentType.indexOf('application/json') === 0) {
          try {
            data = JSON.parse(body);
          } catch (malformed) {
            data = null;
          }
        }

        if (!response.ok) {
          throw {
            kind: 'http',
            status: response.status,
            // Prefer the message the service sent; never expose the raw body.
            message: (data && data.message)
              ? data.message
              : 'The server answered with status ' + response.status + '.'
          };
        }
        if (data === null) {
          throw {
            kind: 'http',
            status: response.status,
            message: 'The server answered with an unexpected content type: ' + contentType
          };
        }

        return { status: response.status, contentType: contentType, data: data };
      });
    }).then(function (outcome) {
      clearTimeout(timeout);
      return outcome;
    }, function (failure) {
      clearTimeout(timeout);
      throw failure;
    });
  }

  /* ----------------------------------------------------------------------
   * Rendering: only the result or the error area is touched
   * -------------------------------------------------------------------- */

  function showResult(value, meta, payload) {
    elements.resultValue.textContent = value;
    elements.resultMeta.textContent = meta || '';
    elements.resultPayload.textContent = payload ? JSON.stringify(payload, null, 2) : '';
    elements.resultPayload.hidden = !payload;
    elements.result.hidden = false;
    elements.error.hidden = true;
  }

  function showError(message, meta) {
    elements.errorMessage.textContent = message;
    elements.errorMeta.textContent = meta || '';
    elements.error.hidden = false;
    elements.result.hidden = true;
  }

  /** The URL is echoed inside the loading state, so it is escaped first. */
  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
})();
